package com.ganten.peanuts.protocol.aeron;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;

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

/**
 * Aeron Subscriber template:
 * - 负责启动/停止 poll loop
 * - 负责在 FragmentHandler 中 decode -> onMessage
 * - 可选：若 {@link AeronProperties#isRaftEnabled()} 为 true，状态机默认回调
 * {@link #onRaftCommitted}，
 * 其默认仅在 {@code localApply} 时调用 {@link #onMessage}；多副本且跟随者需执行相同逻辑时请覆盖
 * {@link #onRaftCommitted}。
 * 亦可覆盖 {@link #raftMessageApplyHandler()} 或 {@link #createRaftApplyClient()}
 * 完全自定义。
 */
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

    protected abstract void onMessage(M message);

    /**
     * 非空时优先于 {@link #onRaftCommitted} 作为状态机 handler（完全自定义装配时用）。
     */
    protected RaftMessageApplyHandler<M> raftMessageApplyHandler() {
        return (message, localApply, done) -> {
            if (localApply) {
                onMessage(message);
                if (done != null) {
                    done.run(Status.OK());
                }
            }
        };
    }

    /**
     * 当 {@link AeronProperties#isRaftEnabled()} 为 true 时由
     * {@link #initRaftFromProperties()} 调用；默认用
     * {@link CodecRaftStateMachine} + {@link RaftBootstrap} 装配，子类可覆盖。
     */
    protected RaftApplyClient createRaftApplyClient() {
        if (!properties.isRaftEnabled()) {
            return null;
        }
        RaftMessageApplyHandler<M> handler = raftMessageApplyHandler();
        CodecRaftStateMachine<M, N> fsm = new CodecRaftStateMachine<>(codec, handler);
        RaftBootstrap bootstrap = new RaftBootstrap(properties.toRaftProperties(), fsm);
        try {
            bootstrap.start();
        } catch (IOException e) {
            throw new IllegalStateException("Raft start failed, streamId=" + properties.getStreamId(), e);
        }
        return new RaftApplyClient(bootstrap);
    }

    /**
     * 是否走 Raft 路径：以是否成功创建 {@link #raftApplyClient} 为准（配置开启但无 handler / 启动失败则不会为
     * true）。
     */
    protected boolean useRaft() {
        return raftApplyClient != null;
    }

    protected boolean shouldApplyRaftWhenLocal() {
        return properties.isRaftEnabled() && useRaft();
    }

    protected void onRaftAccepted(M message) {
        log.debug("Raft apply accepted, streamId={}", properties.getStreamId());
    }

    protected void onRaftRejected(M message, String reason) {
        log.warn("Raft apply rejected, streamId={}, reason={}", properties.getStreamId(), reason);
    }

    private void initRaftFromProperties() {
        if (properties == null || !properties.isRaftEnabled()) {
            return;
        }
        try {
            this.raftApplyClient = createRaftApplyClient();
        } catch (Exception e) {
            log.error("Failed to init Raft client from AeronProperties, streamId={}", properties.getStreamId(), e);
        }
    }

    private void handleMessage(M message) {
        if (useRaft()) {
            if (raftApplyClient == null) {
                onRaftRejected(message, "raft client not initialized");
                return;
            }
            try {
                AeronMessage aeronMessage = codec.encode(message);
                byte[] payload = new byte[aeronMessage.getLength()];
                aeronMessage.getBuffer().getBytes(0, payload);
                RaftApplyResult result = raftApplyClient.apply(payload);
                if (result.isAccepted()) {
                    onRaftAccepted(message);
                } else {
                    onRaftRejected(message, result.getReason());
                }
            } catch (Throwable t) {
                onRaftRejected(message, t.getMessage());
            }
            return;
        }
        onMessage(message);
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
                this.errorHandler(t);
            }
        };

        this.pollWorker = AeronPollWorker.start(
                () -> this.subscription.poll(fragmentHandler, properties.getFragmentLimit()),
                this::errorHandler);
        log.info("Aeron subscriber ready. channel={}, streamId={}, raftEnabled={}",
                properties.getChannel(), properties.getStreamId(), properties.isRaftEnabled());
    }

    protected void errorHandler(Throwable t) {
        log.error("Aeron poll loop failed", t);
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
