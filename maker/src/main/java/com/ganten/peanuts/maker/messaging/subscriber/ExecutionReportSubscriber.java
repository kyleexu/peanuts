package com.ganten.peanuts.maker.messaging.subscriber;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.maker.cache.OrderExecutionStateCache;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

@Component
public class ExecutionReportSubscriber extends AbstractAeronSubscriber<ExecutionReportProto, ExecutionReportCodec> {

    private final OrderExecutionStateCache orderExecutionStateCache;

    public ExecutionReportSubscriber(OrderExecutionStateCache orderExecutionStateCache) {
        // Use a dedicated instance tag to avoid Raft node/group collisions with other services.
        super(AeronStream.EXECUTION_REPORT.toProperties("maker"), ExecutionReportCodec.getInstance());
        this.orderExecutionStateCache = orderExecutionStateCache;
    }

    @Override
    protected void onMessage(ExecutionReportProto message) {
        orderExecutionStateCache.applyExecutionReport(message);
    }
}
