package com.ganten.peanuts.gateway.account;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.gateway.messaging.publisher.LockRequestPublisher;
import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.gateway.messaging.subscriber.LockResponseSubscriber;

/**
 * Gateway-side orchestration for account lock request/response.
 */
@Slf4j
@Component
public class AccountLockService {

    private final AccountLockPendingRequests pendingRequests;
    private final LockRequestPublisher requestPublisher;
    private final LockResponseSubscriber responseSubscriber;

    private final AtomicLong requestIdGenerator = new AtomicLong(1L);

    public AccountLockService(
            @Qualifier("accountLockAeronResponseProperties") LockResponseSubscriber responseSubscriber,
            @Qualifier("accountLockAeronRequestProperties") LockRequestPublisher requestPublisher,
            AccountLockPendingRequests pendingRequests) {
        this.responseSubscriber = responseSubscriber;
        this.requestPublisher = requestPublisher;
        this.pendingRequests = pendingRequests;
    }

    public LockResponseProto checkAndLock(Order order) {
        LockRequestProto request = this.buildRequest(order);
        long requestId = request.getRequestId();
        CompletableFuture<LockResponseProto> future = pendingRequests.put(requestId);
        try {
            // Publish request and wait for response completion (by
            // AccountLockResponseSubscriber).
            long result = requestPublisher.publish(request);
            if (result <= 0) {
                pendingRequests.remove(requestId);
                return fail(requestId, "account lock request back pressured");
            }
            return future.get(Constants.ACCOUNT_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            pendingRequests.remove(requestId);
            return fail(requestId, "account lock request timeout or failed: " + ex.getMessage());
        }
    }

    private LockRequestProto buildRequest(Order order) {
        Currency currency;
        BigDecimal amount;
        if (order.getSide() == Side.BUY) {
            if (order.getPrice() == null) {
                throw new IllegalArgumentException("buy order price required for account lock");
            }
            currency = order.getContract().getQuote();
            amount = order.getPrice().multiply(order.getTotalQuantity());
        } else {
            currency = order.getContract().getBase();
            amount = order.getTotalQuantity();
        }

        LockRequestProto request = new LockRequestProto();
        request.setRequestId(requestIdGenerator.getAndIncrement());
        request.setUserId(order.getUserId());
        request.setCurrency(currency);
        request.setAmount(amount);
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }

    private LockResponseProto fail(long requestId, String message) {
        LockResponseProto response = new LockResponseProto();
        response.setRequestId(requestId);
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    @PreDestroy
    public void shutdown() {
        // No-op: Aeron resources are owned by publisher/subscriber components.
    }
}
