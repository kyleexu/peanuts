package com.ganten.peanuts.gateway.account;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.protocol.model.LockResponseProto;

/**
 * Shared pending requests store for gateway-account lock interaction.
 *
 * requestId -> future, completed by AccountLockResponseSubscriber.
 */
@Component
public class AccountLockPendingRequests {

    private final Map<Long, CompletableFuture<LockResponseProto>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<LockResponseProto> put(long requestId) {
        CompletableFuture<LockResponseProto> future = new CompletableFuture<>();
        CompletableFuture<LockResponseProto> old = pending.put(requestId, future);
        if (old != null) {
            // Should not happen (requestId generator is monotonic), but keep it safe.
            old.completeExceptionally(new IllegalStateException("Duplicate lock requestId=" + requestId));
        }
        return future;
    }

    public CompletableFuture<LockResponseProto> remove(long requestId) {
        return pending.remove(requestId);
    }

    public void complete(LockResponseProto response) {
        CompletableFuture<LockResponseProto> future = pending.remove(Long.valueOf(response.getRequestId()));
        if (future != null) {
            future.complete(response);
        }
    }
}

