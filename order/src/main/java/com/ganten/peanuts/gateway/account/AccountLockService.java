package com.ganten.peanuts.gateway.account;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.mapping.LockRequestProtocolMapper;
import com.ganten.peanuts.gateway.messaging.publisher.LockRequestPublisher;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Gateway-side orchestration for account lock request/response.
 */
@Slf4j
@Component
public class AccountLockService {

    // 使用这个 publisher 发送锁请求
    private final LockRequestPublisher requestPublisher;

    // 使用这个 store 存储锁请求的 future 响应结果，用于等待锁响应
    private final LockPendingRequests lockPendingRequests;

    // 使用这个 generator 生成锁请求的 requestId
    private final AtomicLong requestIdGenerator = new AtomicLong(1L);
    private final long lockTimeoutMs;

    public AccountLockService(LockPendingRequests lockPendingRequests,
            LockRequestPublisher requestPublisher,
            @Value("${gateway.account-lock.timeout-ms:" + Constants.ACCOUNT_LOCK_TIMEOUT_MS + "}") long lockTimeoutMs) {
        this.lockPendingRequests = lockPendingRequests;
        this.requestPublisher = requestPublisher;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    /**
     * 第 3 步，构建锁请求并发送给 account 并等待锁响应
     * Aeron: order -> account
     */
    public LockResponseProto checkAndLock(Order order) {
        long requestId = requestIdGenerator.getAndIncrement();
        LockRequestProto request = LockRequestProtocolMapper.toLockRequest(
                order, requestId, System.currentTimeMillis());
        log.info("Lock request dispatch, requestId={}, userId={}, currency={}, amount={}, orderId={}",
                requestId, request.getUserId(), request.getCurrency(), request.getAmount(), order.getOrderId());
        CompletableFuture<LockResponseProto> future = lockPendingRequests.put(requestId);
        LockResponseProto response;
        try {
            boolean offered = requestPublisher.offerWithRetry(request);
            if (!offered) {
                lockPendingRequests.remove(requestId);
                return LockRequestProtocolMapper.toFailureResponse(
                        requestId, "account lock request publish failed");
            }
            /**
             * 第 6 步，等待锁响应，拿到锁响应结果后
             * 关键: 这里会阻塞等待锁响应，直到锁响应返回或超时
             */
            response = future.get(lockTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.warn("Lock request timeout, requestId={}, timeoutMs={}, request={}", requestId, lockTimeoutMs, request);
            lockPendingRequests.remove(requestId);
            response = LockRequestProtocolMapper.toFailureResponse(requestId,
                    "account lock request timeout, timeoutMs=" + lockTimeoutMs);
        } catch (Exception ex) {
            log.error("Lock request failed, requestId={}, request={}, error={}", requestId, request, ex.getMessage(),
                    ex);
            lockPendingRequests.remove(requestId);
            response = LockRequestProtocolMapper.toFailureResponse(requestId,
                    "account lock request timeout or failed: " + ex.getMessage());
        }
        log.info("Lock request completed, requestId={}, response={}", requestId, response);
        return response;
    }
}
