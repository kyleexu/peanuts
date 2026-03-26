package com.ganten.peanuts.protocol.aeron;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.ganten.peanuts.protocol.codec.AbstractCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.raft.CodecRaftStateMachine;
import com.ganten.peanuts.protocol.raft.RaftBootstrap;
import com.ganten.peanuts.protocol.raft.RaftBootstrap.ApplyResult;
import com.ganten.peanuts.protocol.raft.RaftMessageApplyHandler;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Aeron 订阅模板；各模块 {@link AeronProperties} 由
 * {@link AeronSubscriberPropertiesFactory} 从
 * {@link com.ganten.peanuts.common.constant.Constants} 组装。行为说明见
 * {@code docs/AERON_AND_RAFT.md}。
 */
@Slf4j
public abstract class AbstractAeronSubscriber<M, N extends AbstractCodec<M>> implements AutoCloseable {

    protected AeronProperties properties;
    protected Subscription subscription;
    protected AeronPollWorker pollWorker;
    protected Aeron aeron;
    protected final N codec;
    protected RaftBootstrap bootstrap;

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
     * @see RaftMessageApplyHandler#onCommitted
     * @return
     */
    private RaftBootstrap createRaftBootstrap() {
        CodecRaftStateMachine<M, N> fsm = new CodecRaftStateMachine<>(codec, this::onRaftCommitted);
        RaftBootstrap bootstrap = new RaftBootstrap(properties.toRaftProperties(), fsm);
        try {
            bootstrap.start();
        } catch (IOException e) {
            throw new IllegalStateException("Raft start failed, streamId=" + properties.getStreamId(), e);
        }
        return bootstrap;
    }

    /**
     * 该方法由状态机回调触发，会在多数派共识达成并 apply 时执行。
     * 这个方式是同步性更好的方案
     */
    protected void onRaftCommitted(M message, boolean localApply, Closure done) {
        if (properties.getRaftApplyMode() == RaftApplyMode.AFTER_COMMIT) {
            log.info("Raft apply committed, streamId:{}. message={}", properties.getStreamId(), message);
            this.onMessage(message);
        }
        if (localApply && done != null) {
            done.run(Status.OK());
        }
    }

    private void handleMessage(M message) {
        if (properties.getRaftApplyMode() == RaftApplyMode.DISABLE) {
            this.onMessage(message);
            return;
        }

        int streamId = properties.getStreamId();
        RaftApplyMode raftApplyMode = properties.getRaftApplyMode();

        AeronMessage aeronMessage = codec.encode(message);
        byte[] payload = new byte[aeronMessage.getLength()];
        aeronMessage.getBuffer().getBytes(0, payload);

        ApplyResult result = bootstrap.apply(payload);
        // isAccepted() 是指 leader 侧提案已入队，并不是多数节点复制完成
        // 处于这种模式下，执行业务逻辑是低延迟方案
        if (result.isAccepted()) {
            if (raftApplyMode == RaftApplyMode.ON_AERON_POLL) {
                this.onMessage(message);
            }
        } else {
            log.warn("Raft apply rejected, streamId:{}.", streamId);
        }
    }

    @PostConstruct
    protected void start() {
        if (!properties.isEnabled()) {
            log.warn("Aeron subscriber disabled by config");
            return;
        }

        if (properties.getRaftApplyMode() != RaftApplyMode.DISABLE) {
            this.bootstrap = this.createRaftBootstrap();
            if (bootstrap == null) {
                log.error("Raft is enabled but have no Raft client. streamId={}",
                        properties.getStreamId());
                return;
            }
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
                this.handleMessage(message);
            } catch (Throwable t) {
                log.error("Aeron poll loop failed", t);
            }
        };

        this.pollWorker = AeronPollWorker.start(
                () -> this.subscription.poll(fragmentHandler, properties.getFragmentLimit()),
                (t) -> log.error("Aeron poll loop failed", t));
        log.info("Aeron subscriber ready. channel={}, streamId={}, raftApplyMode={}",
                properties.getChannel(), properties.getStreamId(), properties.getRaftApplyMode());
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
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
        bootstrap = null;
    }
}
