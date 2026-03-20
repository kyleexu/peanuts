package com.ganten.peanuts.engine.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import com.ganten.peanuts.protocol.model.AeronMessage;

@Component
public class AeronExecutionReportPublisher extends AbstractAeronPublisher<ExecutionReportProto> {

    private static final Logger log = LoggerFactory.getLogger(AeronExecutionReportPublisher.class);

    private final MatchEngineProperties properties;

    private MediaDriver mediaDriver;
    private Aeron aeron;

    public AeronExecutionReportPublisher(MatchEngineProperties properties) {
        this.properties = properties;
        super(properties.getAeronProperties());
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.warn("Match-engine Aeron transport disabled");
            return;
        }
        if (properties.isLaunchEmbeddedDriver()) {
            MediaDriver.Context driverContext = new MediaDriver.Context();
            driverContext.aeronDirectoryName(properties.getDirectory());
            driverContext.dirDeleteOnStart(true);
            driverContext.dirDeleteOnShutdown(true);
            mediaDriver = MediaDriver.launchEmbedded(driverContext);
        }

        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(properties.getDirectory());
        aeron = Aeron.connect(context);
        setPublication(aeron.addPublication(properties.getChannel(), properties.getOutboundStreamId()));
        log.info("Execution report publisher ready. dir={}, channel={}, streamId={}", properties.getDirectory(),
                properties.getChannel(), properties.getOutboundStreamId());
    }

    @Override
    protected AeronMessage encode(ExecutionReportProto report) {
        return ExecutionReportCodec.getInstance().encode(report);
    }

    @Override
    protected void onPublicationUnavailable(ExecutionReportProto report) {
        log.error("Execution report publication not available, orderId={}", report.getOrderId());
    }

    @Override
    protected void onOfferResult(ExecutionReportProto report, long result) {
        if (result > 0) {
            log.info("Execution report published, orderId={}, result={}", report.getOrderId(), result);
        } else {
            log.warn("Execution report publish back pressured, orderId={}, code={}", report.getOrderId(), result);
        }
    }

    public Aeron aeron() {
        return aeron;
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
        if (aeron != null) {
            aeron.close();
        }
        if (mediaDriver != null) {
            mediaDriver.close();
        }
    }
}
