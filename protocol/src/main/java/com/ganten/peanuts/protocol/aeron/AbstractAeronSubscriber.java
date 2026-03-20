package com.ganten.peanuts.protocol.aeron;

import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;

import com.ganten.peanuts.protocol.codec.AbstractCodec;

import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import io.aeron.Aeron;

/**
 * Aeron Subscriber template:
 * - 负责启动/停止 poll loop
 * - 负责在 FragmentHandler 中 decode -> onMessage
 * 具体的 decode/onMessage 由子类实现
 */
@Slf4j
public abstract class AbstractAeronSubscriber<M, N extends AbstractCodec<M>> implements AutoCloseable {

    protected AeronProperties properties;
    protected Subscription subscription;
    protected AeronPollWorker pollWorker;
    protected Aeron aeron;
    protected final N codec;

    public AbstractAeronSubscriber(AeronProperties properties, N codec) {
        this.properties = properties;
        this.codec = codec;
    }

    protected M decode(DirectBuffer buffer, int offset) {
        return codec.decode(buffer, offset);
    }

    protected abstract void onMessage(M message);
    
    @PostConstruct
    protected void start() {
        if (!properties.isEnabled()) {
            log.warn("Aeron subscriber disabled by config");
            return;
        }

        // Ensure idempotent start.
        if (pollWorker != null) {
            pollWorker.close();
        }
        aeron = Aeron.connect();
        this.subscription = aeron.addSubscription(properties.getChannel(), properties.getStreamId());
        if (this.subscription == null) {
            log.error("Failed to create Aeron subscription");
            return;
        }

        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            try {
                M message = decode(buffer, offset);
                if (message != null) {
                    this.onMessage(message);
                }
            } catch (Throwable t) {
                this.errorHandler().accept(t);
            }
        };

        this.pollWorker = AeronPollWorker.start(
                () -> this.subscription.poll(fragmentHandler, properties.getFragmentLimit()),
                this.errorHandler());
        log.info("Aeron subscriber ready. channel={}, streamId={}",
                properties.getChannel(), properties.getStreamId());
    }

    protected Consumer<Throwable> errorHandler() {
        return (throwable) -> log.error("Aeron poll loop failed", throwable);
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
    }
}
