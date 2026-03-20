package com.ganten.peanuts.gateway.account;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.aeron.AeronPollWorker;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.gateway.config.AeronProperties;
import com.ganten.peanuts.protocol.codec.AccountLockMessageCodec;
import com.ganten.peanuts.protocol.model.AccountLockRequest;
import com.ganten.peanuts.protocol.model.AccountLockResponse;
import com.ganten.peanuts.protocol.model.EncodedMessage;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountLockAeronClient {

    private final AeronProperties properties;
    private final AccountLockMessageCodec codec;
    private final AtomicLong requestIdGenerator = new AtomicLong(1L);
    private final Map<Long, CompletableFuture<AccountLockResponse>> pending =
            new ConcurrentHashMap<Long, CompletableFuture<AccountLockResponse>>();

    private Aeron aeron;
    private Publication requestPublication;
    private Subscription responseSubscription;
    private AeronPollWorker responsePollWorker;

    public AccountLockAeronClient(AeronProperties properties, AccountLockMessageCodec codec) {
        this.properties = properties;
        this.codec = codec;
    }

    @PostConstruct
    public void init() {
        aeron = Aeron.connect();
        requestPublication = aeron.addPublication(properties.getChannel(), properties.getAccountLockRequestStreamId());
        responseSubscription =
                aeron.addSubscription(properties.getChannel(), properties.getAccountLockResponseStreamId());
        startResponsePollLoop();
    }

    public AccountLockResponse checkAndLock(Order order) {
        AccountLockRequest request = buildRequest(order);
        CompletableFuture<AccountLockResponse> future = new CompletableFuture<AccountLockResponse>();
        pending.put(Long.valueOf(request.getRequestId()), future);
        try {
            EncodedMessage encoded = codec.encodeRequest(request);
            long result = requestPublication.offer(encoded.getBuffer(), 0, encoded.getLength());
            if (result <= 0) {
                pending.remove(Long.valueOf(request.getRequestId()));
                return fail(request.getRequestId(), "account lock request back pressured");
            }
            return future.get(properties.getAccountLockTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            pending.remove(Long.valueOf(request.getRequestId()));
            return fail(request.getRequestId(), "account lock request timeout or failed: " + ex.getMessage());
        }
    }

    private void startResponsePollLoop() {
        final FragmentHandler responseHandler = (buffer, offset, length, header) -> {
            AccountLockResponse response = codec.decodeResponse(buffer, offset);
            CompletableFuture<AccountLockResponse> future = pending.remove(Long.valueOf(response.getRequestId()));
            if (future != null) {
                future.complete(response);
            }
        };
        responsePollWorker = AeronPollWorker.start("gateway-account-lock-response-poller",
                () -> responseSubscription.poll(responseHandler, 20),
                ex -> log.error("Account lock response poll loop failed", ex));
    }

    private AccountLockRequest buildRequest(Order order) {
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

        AccountLockRequest request = new AccountLockRequest();
        request.setRequestId(requestIdGenerator.getAndIncrement());
        request.setUserId(order.getUserId());
        request.setCurrency(currency);
        request.setAmount(amount);
        request.setTimestamp(System.currentTimeMillis());
        return request;
    }

    private AccountLockResponse fail(long requestId, String message) {
        AccountLockResponse response = new AccountLockResponse();
        response.setRequestId(requestId);
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    @PreDestroy
    public void shutdown() {
        if (responsePollWorker != null) {
            responsePollWorker.close();
        }
        if (responseSubscription != null) {
            responseSubscription.close();
        }
        if (requestPublication != null) {
            requestPublication.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
