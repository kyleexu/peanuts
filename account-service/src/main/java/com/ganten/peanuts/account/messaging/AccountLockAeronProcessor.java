package com.ganten.peanuts.account.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.config.AccountAeronProperties;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.protocol.aeron.AeronPollWorker;
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
public class AccountLockAeronProcessor {

    private final AccountAeronProperties properties;
    private final AccountService accountService;    private Aeron aeron;
    private Subscription requestSubscription;
    private Publication responsePublication;
    private AeronPollWorker pollWorker;

    public AccountLockAeronProcessor(AccountAeronProperties properties, AccountService accountService) {
        this.properties = properties;
        this.accountService = accountService;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.warn("Account lock Aeron processor disabled by config");
            return;
        }
        aeron = Aeron.connect();
        requestSubscription = aeron.addSubscription(properties.getChannel(), properties.getLockRequestStreamId());
        responsePublication = aeron.addPublication(properties.getChannel(), properties.getLockResponseStreamId());
        startPollLoop();
        log.info("Account lock Aeron processor ready. channel={}, reqStream={}, respStream={}", properties.getChannel(),
                properties.getLockRequestStreamId(), properties.getLockResponseStreamId());
    }

    private void startPollLoop() {
        final FragmentHandler requestHandler = (buffer, offset, length, header) -> {
            LockRequestProto request = LockRequestCodec.getInstance().decode(buffer, offset);
            boolean success = accountService.tryLock(request.getUserId(), request.getCurrency(), request.getAmount());

            LockResponseProto response = new LockResponseProto();
            response.setRequestId(request.getRequestId());
            response.setSuccess(success);
            if (success) {
                response.setMessage("locked");
            } else {
                response.setMessage("insufficient available balance");
            }

            AeronMessage encoded = LockResponseCodec.getInstance().encode(response);
            long result = responsePublication.offer(encoded.getBuffer(), 0, encoded.getLength());
            if (result <= 0) {
                log.warn("Account lock response back pressured, requestId={}, code={}", request.getRequestId(), result);
            }
        };
        pollWorker =
                AeronPollWorker.start("account-lock-aeron-poller", () -> requestSubscription.poll(requestHandler, 50),
                        ex -> log.error("Account lock poll loop failed", ex));
    }

    @PreDestroy
    public void shutdown() {
        if (pollWorker != null) {
            pollWorker.close();
        }
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
