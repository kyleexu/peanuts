package com.ganten.peanuts.aerondriver;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.aeron.driver.MediaDriver;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class StandaloneMediaDriverService {
    private static final Logger log = LoggerFactory.getLogger(StandaloneMediaDriverService.class);

    private final AeronDriverProperties properties;
    private MediaDriver mediaDriver;

    public StandaloneMediaDriverService(AeronDriverProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        MediaDriver.Context context = new MediaDriver.Context();
        context.aeronDirectoryName(properties.getDirectory());
        context.dirDeleteOnStart(properties.isDirDeleteOnStart());
        mediaDriver = MediaDriver.launch(context);
        log.info("Standalone Aeron MediaDriver started. directory={}", properties.getDirectory());
    }

    @PreDestroy
    public void stop() {
        if (mediaDriver != null) {
            mediaDriver.close();
            mediaDriver = null;
            log.info("Standalone Aeron MediaDriver stopped.");
        }
    }
}
