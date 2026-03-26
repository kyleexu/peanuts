package com.ganten.peanuts.gateway.messaging.publisher;

import java.util.concurrent.locks.LockSupport;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import io.aeron.Publication;

/**
 * Publish account lock requests to account.
 */
@Slf4j
@Component
public class LockRequestPublisher extends AbstractAeronPublisher<LockRequestProto, LockRequestCodec> {

    public LockRequestPublisher(
            @Qualifier("accountLockAeronRequestProperties") AeronProperties properties) {
        super(properties, LockRequestCodec.getInstance());
    }

    /**
     * Offer with bounded retry to reduce transient Aeron back-pressure drops.
     */
    public boolean offerWithRetry(LockRequestProto request) {
        if (publication == null || request == null) {
            return false;
        }
        AeronMessage encoded = encode(request);
        final int maxAttempts = 20;
        for (int i = 1; i <= maxAttempts; i++) {
            long result = publication.offer(encoded.getBuffer(), 0, encoded.getLength());
            if (result > 0) {
                return true;
            }
            if (result == Publication.CLOSED || result == Publication.NOT_CONNECTED) {
                log.warn("Lock request offer failed immediately, requestId={}, result={}", request.getRequestId(), result);
                return false;
            }
            if (i == maxAttempts) {
                log.warn("Lock request offer retries exhausted, requestId={}, lastResult={}", request.getRequestId(), result);
                return false;
            }
            // light backoff for back pressure/admin action
            LockSupport.parkNanos(1_000_000L); // 1ms
        }
        return false;
    }
}
