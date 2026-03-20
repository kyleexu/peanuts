package com.ganten.peanuts.protocol.aeron;

import javax.annotation.PostConstruct;
import io.aeron.Publication;

import com.ganten.peanuts.protocol.codec.AbstractCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import io.aeron.Aeron;
import lombok.extern.slf4j.Slf4j;

/**
 * Aeron Publisher template:
 * - 统一 offer + 编码
 * - 具体 encode 由子类实现
 */
@Slf4j
public abstract class AbstractAeronPublisher<M, N extends AbstractCodec<M>> implements AutoCloseable {

    protected Aeron aeron;
    protected Publication publication;
    protected final AeronProperties properties;
    protected final N codec;

    public AbstractAeronPublisher(AeronProperties properties, N codec) {
        this.properties = properties;
        this.codec = codec;
    }

    @PostConstruct
    protected void start() {
        if (!properties.isEnabled()) {
            log.warn("Aeron publisher disabled by config");
            return;
        }
        try {
            aeron = AeronRuntime.connect(properties);
            publication = aeron.addPublication(properties.getChannel(), properties.getStreamId());
            log.info("Aeron publisher ready. channel={}, streamId={}", properties.getChannel(),
                    properties.getStreamId());
        } catch (Exception e) {
            log.error("Failed to initialize Aeron publisher", e);
        }
    }

    protected AeronMessage encode(M message) {
        return codec.encode(message);
    }

    public void offer(M message) {
        if (publication == null || message == null) {
            onPublicationUnavailable(message);
            return;
        }
        try {
            AeronMessage encoded = encode(message);
            if (encoded == null) {
                return;
            }
            long result = publication.offer(encoded.getBuffer(), 0, encoded.getLength());
            onOfferResult(message, result);
        } catch (Throwable t) {
            onPublishError(message, t);
        }
    }

    protected void onPublicationUnavailable(M message) {
    }

    protected void onOfferResult(M message, long result) {
    }

    protected void onPublishError(M message, Throwable error) {
    }

    public void shutdown() {
        if (publication != null) {
            publication.close();
            publication = null;
        }
        AeronRuntime.close(properties, aeron);
        aeron = null;
    }

    @Override
    public void close() {
        this.shutdown();
    }
}
