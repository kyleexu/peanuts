package com.ganten.peanuts.account.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.codec.AccountLockMessageCodec;
import com.ganten.peanuts.account.config.AccountAeronProperties;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.entity.AccountLockRequest;
import com.ganten.peanuts.common.entity.AccountLockResponse;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountLockAeronProcessor {

    private final AccountAeronProperties properties;
    private final AccountService accountService;
    private final AccountLockMessageCodec codec;

    private Aeron aeron;
    private Subscription requestSubscription;
    private Publication responsePublication;

    public AccountLockAeronProcessor(AccountAeronProperties properties, AccountService accountService,
            AccountLockMessageCodec codec) {
        this.properties = properties;
        this.accountService = accountService;
        this.codec = codec;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        aeron = Aeron.connect();
        requestSubscription = aeron.addSubscription(properties.getChannel(), properties.getLockRequestStreamId());
        responsePublication = aeron.addPublication(properties.getChannel(), properties.getLockResponseStreamId());
        log.info("Account lock Aeron processor ready. channel={}, reqStream={}, respStream={}", properties.getChannel(),
                properties.getLockRequestStreamId(), properties.getLockResponseStreamId());
    }

    @Scheduled(fixedDelay = 20L)
    public void pollRequests() {
        if (requestSubscription == null) {
            return;
        }
        FragmentHandler handler = (buffer, offset, length, header) -> {
            AccountLockRequest request = codec.decodeRequest(buffer, offset);
            boolean success = accountService.tryLock(request.getUserId(), request.getCurrency(), request.getAmount());

            AccountLockResponse response = new AccountLockResponse();
            response.setRequestId(request.getRequestId());
            response.setSuccess(success);
            if (success) {
                response.setMessage("locked");
            } else {
                response.setMessage("insufficient available balance");
            }

            UnsafeBuffer encoded = codec.encodeResponse(response);
            long result = responsePublication.offer(encoded, 0, encoded.capacity());
            if (result <= 0) {
                log.warn("Account lock response back pressured, requestId={}, code={}", request.getRequestId(), result);
            }
        };

        requestSubscription.poll(handler, 50);
    }

    @PreDestroy
    public void shutdown() {
        if (requestSubscription != null) {
            requestSubscription.close();
        }
        if (responsePublication != null) {
            responsePublication.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
