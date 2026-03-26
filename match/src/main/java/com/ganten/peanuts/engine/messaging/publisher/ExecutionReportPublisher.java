package com.ganten.peanuts.engine.messaging.publisher;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Component
public class ExecutionReportPublisher extends AbstractAeronPublisher<ExecutionReportProto, ExecutionReportCodec> {

    public ExecutionReportPublisher() {
        super(AeronStream.EXECUTION_REPORT.toProperties(), ExecutionReportCodec.getInstance());
    }
}
