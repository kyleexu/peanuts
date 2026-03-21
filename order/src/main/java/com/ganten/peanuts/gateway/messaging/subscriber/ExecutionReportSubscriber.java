package com.ganten.peanuts.gateway.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.gateway.cache.OrderCache;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

import lombok.extern.slf4j.Slf4j;

/**
 * 消费 match 发布的执行回报（stream 2002），更新网关订单缓存。
 */
@Slf4j
@Component
public class ExecutionReportSubscriber extends AbstractAeronSubscriber<ExecutionReportProto, ExecutionReportCodec> {

    private final OrderCache orderCache;

    public ExecutionReportSubscriber(
            @Qualifier("executionReportAeronProperties") AeronProperties aeronProperties,
            OrderCache orderCache) {
        super(aeronProperties, ExecutionReportCodec.getInstance());
        this.orderCache = orderCache;
    }

    @Override
    protected void onMessage(ExecutionReportProto message) {
        orderCache.applyExecutionReport(message);
    }
}
