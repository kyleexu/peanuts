package com.ganten.peanuts.protocol.aeron;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared Aeron runtime for optional embedded MediaDriver usage.
 */
@Slf4j
final class AeronRuntime {

    private static final ConcurrentHashMap<String, DriverRef> DRIVER_REFS = new ConcurrentHashMap<String, DriverRef>();

    private AeronRuntime() {
    }

    static Aeron connect(AeronProperties properties) {
        String directory = properties.getDirectory();
        if (properties.isLaunchEmbeddedDriver()) {
            DRIVER_REFS.compute(directory, (key, existing) -> {
                if (existing == null) {
                    MediaDriver.Context driverContext = new MediaDriver.Context();
                    driverContext.aeronDirectoryName(directory);
                    MediaDriver driver = MediaDriver.launchEmbedded(driverContext);
                    log.info("Embedded MediaDriver launched, directory={}", directory);
                    return new DriverRef(driver);
                }
                existing.refCount.incrementAndGet();
                return existing;
            });
        }

        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(directory);
        return Aeron.connect(context);
    }

    static void close(AeronProperties properties, Aeron aeron) {
        if (aeron != null) {
            aeron.close();
        }
        if (!properties.isLaunchEmbeddedDriver()) {
            return;
        }

        String directory = properties.getDirectory();
        DRIVER_REFS.computeIfPresent(directory, (key, ref) -> {
            if (ref.refCount.decrementAndGet() <= 0) {
                try {
                    ref.driver.close();
                    log.info("Embedded MediaDriver closed, directory={}", directory);
                } catch (Exception ex) {
                    log.warn("Failed to close embedded MediaDriver, directory={}: {}", directory, ex.getMessage());
                }
                return null;
            }
            return ref;
        });
    }

    private static final class DriverRef {
        private final MediaDriver driver;
        private final AtomicInteger refCount = new AtomicInteger(1);

        private DriverRef(MediaDriver driver) {
            this.driver = driver;
        }
    }
}

