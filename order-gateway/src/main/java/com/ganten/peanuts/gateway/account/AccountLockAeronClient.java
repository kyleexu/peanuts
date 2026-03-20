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
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.protocol.model.AeronMessage;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountLockAeronClient {

    private final AeronProperties properties;
    private final AtomicLong requestIdGenerator = new AtomicLong(1L);
    private final Map<Long, CompletableFuture<LockResponseProto>> pending =
            new ConcurrentHashMap<Long, CompletableFuture<LockResponseProto>>();

    private Aeron aeron;
    private Publication requestPublication;
    private Subscription responseSubscription;
    private AeronPollWorker responsePollWorker;

    public AccountLockAeronClient(AeronProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        aeron = Aeron.connect();
        requestPublication = aeron.addPublication(properties.getChannel(), properties.getAccountLockRequestStreamId());
        responseSubscription =
                aeron.addSubscription(properties.getChannel(), properties.getAccountLockResponseStreamId());
        startResponsePollLoop();
    }

    public LockResponseProto checkAndLock(Order order) {
        LockRequestProto request = buildRequest(order);
        CompletableFuture<LockResponseProto> future = new CompletableFuture<LockResponseProto>();
        pending.put(Long.valueOf(request.getRequestId()), future);
        try {
            AeronMessage encoded = LockRequestCodec.getInstance().encode(request);
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
            LockResponseProto response = LockResponseCodec.getInstance().decode(buffer, offset);
            CompletableFuture<LockResponseProto> future = pending.remove(Long.valueOf(response.getRequestId()));
            if (future != null) {
                future.complete(response);
            }
        };
        responsePollWorker = AeronPollWorker.start("gateway-account-lock-response-poller",
                () -> responseSubscription.poll(responseHandler, 20),
                ex -> log.error("Account lock response poll loop failed", ex));
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
