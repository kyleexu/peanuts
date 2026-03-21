package com.ganten.peanuts.engine.messaging.publisher;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import lombok.extern.slf4j.Slf4j;

import com.ganten.peanuts.protocol.aeron.AeronProperties;
import org.springframework.beans.factory.annotation.Qualifier;
@Slf4j
@Component
public class ExecutionReportPublisher extends AbstractAeronPublisher<ExecutionReportProto, ExecutionReportCodec> {

    public ExecutionReportPublisher(@Qualifier("executionReportAeronProperties") AeronProperties properties) {
        super(properties, ExecutionReportCodec.getInstance());
    }
}
