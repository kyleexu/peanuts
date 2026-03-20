package com.ganten.peanuts.gateway.account;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储锁请求的 future 响应结果，用于等待锁响应，这里类似一个请求队列，每个请求都有一个唯一的 requestId 用于标识请求
 * 当 account-service 返回锁响应时，需要调用 complete 方法完成锁请求
 */
@Slf4j
@Component
public class LockPendingRequests {

    private final Map<Long, CompletableFuture<LockResponseProto>> pending = new ConcurrentHashMap<>();

    /**
     * 添加锁请求
     * @param requestId 请求ID
     * @return 锁请求的 future 响应结果
     */
    public CompletableFuture<LockResponseProto> put(long requestId) {
        CompletableFuture<LockResponseProto> future = new CompletableFuture<>();
        CompletableFuture<LockResponseProto> old = pending.put(requestId, future);
        if (old != null) {
            // Should not happen (requestId generator is monotonic), but keep it safe.
            old.completeExceptionally(new IllegalStateException("Duplicate lock requestId=" + requestId));
        }
        return future;
    }

    /**
     * 移除锁请求
     * @param requestId 请求ID
     * @return 锁请求的 future 响应结果
     */
    public CompletableFuture<LockResponseProto> remove(long requestId) {
        return pending.remove(requestId);
    }

    /**
     * 完成锁请求
     * @param response 锁响应
     */
    public void complete(LockResponseProto response) {
        CompletableFuture<LockResponseProto> future = pending.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
            log.info("Lock request completed, requestId={}", response.getRequestId());
        } else {
            log.error("Lock request not found, requestId={}", response.getRequestId());
        }
    }
}

