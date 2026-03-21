package com.ganten.peanuts.protocol.aeron;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.ganten.peanuts.protocol.codec.AbstractCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.raft.CodecRaftStateMachine;
import com.ganten.peanuts.protocol.raft.RaftApplyClient;
import com.ganten.peanuts.protocol.raft.RaftApplyResult;
import com.ganten.peanuts.protocol.raft.RaftBootstrap;
import com.ganten.peanuts.protocol.raft.RaftMessageApplyHandler;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAeronSubscriber<M, N extends AbstractCodec<M>> implements AutoCloseable {

    protected AeronProperties properties;
    protected Subscription subscription;
    protected AeronPollWorker pollWorker;
    protected Aeron aeron;
    protected final N codec;
    /**
     * 由 {@link #initRaftFromProperties()} 调用 {@link #createRaftApplyClient()} 创建
     */
    protected RaftApplyClient raftApplyClient;

    public AbstractAeronSubscriber(AeronProperties properties, N codec) {
        this.properties = properties;
        this.codec = codec;
    }

    protected M decode(DirectBuffer buffer, int offset) {
        return codec.decode(buffer, offset);
    }

    /**
     * 仅在不走 Raft 时由 Aeron poll 线程调用，表示「内存路径」上的业务处理。
     */
    protected abstract void onMessage(M message);

    /**
     * Raft 状态机 apply：默认不做业务，仅完成 JRaft 的 {@code done} 回调（日志已由 Raft 层持久化/复制）。
     */
    protected void onRaftLogCommitted(M message, boolean localApply, Closure done) {
        // 如果在这里调用，那么就是速度慢的，但是 raft 一致性可以保证
        this.onMessage(message);
        if (localApply && done != null) {
            done.run(Status.OK());
        }
    }

    private RaftApplyClient createRaftApplyClient() {
        if (!properties.isRaftEnabled()) {
            return null;
        }
        CodecRaftStateMachine<M, N> fsm = new CodecRaftStateMachine<>(codec, this::onRaftLogCommitted);
        RaftBootstrap bootstrap = new RaftBootstrap(properties.toRaftProperties(), fsm);
        try {
            bootstrap.start();
        } catch (IOException e) {
            throw new IllegalStateException("Raft start failed, streamId=" + properties.getStreamId(), e);
        }
        return new RaftApplyClient(bootstrap);
    }

    private void initRaftFromProperties() {
        try {
            if (properties != null && properties.isRaftEnabled()) {
                this.raftApplyClient = createRaftApplyClient();
            }
        } catch (Exception e) {
            log.error("Failed to init Raft client from AeronProperties, streamId={}", properties.getStreamId(), e);
        }
    }

    private void handleMessage(M message) {
        // 如果在这里调用，那么就是速度快的，但是 raft 一致性没办法保证
        // this.onMessage(message);
        if (properties.isRaftEnabled() && raftApplyClient != null) {
            try {
                AeronMessage aeronMessage = codec.encode(message);
                byte[] payload = new byte[aeronMessage.getLength()];
                aeronMessage.getBuffer().getBytes(0, payload);
                RaftApplyResult result = raftApplyClient.apply(payload);
                if (result.isAccepted()) {
                    log.debug("Raft apply accepted, streamId={}", properties.getStreamId());
                } else {
                    log.warn("Raft apply rejected, streamId={}, reason={}", properties.getStreamId(),
                            result.getReason());
                }
            } catch (Throwable t) {
                log.error("Raft apply failed, streamId={}, reason={}", properties.getStreamId(), t.getMessage());
            }
            return;
        }
    }

    @PostConstruct
    protected void start() {
        if (!properties.isEnabled()) {
            log.warn("Aeron subscriber disabled by config");
            return;
        }

        initRaftFromProperties();
        if (properties.isRaftEnabled() && raftApplyClient == null) {
            log.error("Raft is enabled but no Raft client was created (override createRaftApplyClient()). streamId={}",
                    properties.getStreamId());
            return;
        }

        if (pollWorker != null) {
            pollWorker.close();
        }
        aeron = AeronRuntime.connect(properties);
        this.subscription = aeron.addSubscription(properties.getChannel(), properties.getStreamId());
        if (this.subscription == null) {
            log.error("Failed to create Aeron subscription");
            return;
        }

        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            try {
                M message = decode(buffer, offset);
                if (message == null) {
                    return;
                }
                handleMessage(message);
            } catch (Throwable t) {
                log.error("Aeron poll loop failed", t);
            }
        };

        this.pollWorker = AeronPollWorker.start(
                () -> this.subscription.poll(fragmentHandler, properties.getFragmentLimit()),
                (t) -> log.error("Aeron poll loop failed", t));
        log.info("Aeron subscriber ready. channel={}, streamId={}, raftEnabled={}",
                properties.getChannel(), properties.getStreamId(), properties.isRaftEnabled());
    }

    @Override
    public void close() {
        if (pollWorker != null) {
            pollWorker.close();
            pollWorker = null;
        }
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        AeronRuntime.close(properties, aeron);
        aeron = null;
        if (raftApplyClient != null && raftApplyClient.getRaftBootstrap() != null) {
            raftApplyClient.getRaftBootstrap().shutdown();
        }
        raftApplyClient = null;
    }
}
