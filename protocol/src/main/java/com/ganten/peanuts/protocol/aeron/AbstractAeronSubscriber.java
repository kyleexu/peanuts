package com.ganten.peanuts.protocol.aeron;

import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import io.aeron.Subscription;
import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Aeron Subscriber template:
 * - 负责启动/停止 poll loop
 * - 负责在 FragmentHandler 中 decode -> onMessage
 * 具体的 decode/onMessage 由子类实现
 */
@Slf4j
public abstract class AbstractAeronSubscriber<M> implements AutoCloseable {

    protected Aeron aeron;
    protected AeronProperties properties;
    protected Subscription subscription;
    protected AeronPollWorker pollWorker;

    protected abstract M decode(DirectBuffer buffer, int offset);

    protected abstract void onMessage(M message);

    public AbstractAeronSubscriber(AeronProperties properties) {
        this.properties = properties;
    }

    protected void start(String threadName, FragmentHandler handler,
            Consumer<M> messageHandler) {
        this.subscription = aeron.addSubscription(properties.getChannel(), properties.getStreamId());
        pollWorker = AeronPollWorker.start(() -> subscription.poll(handler, properties.getFragmentLimit()), null);
        log.info("Aeron subscriber ready. channel={}, streamId={}", properties.getChannel(), properties.getStreamId());
    }
}

