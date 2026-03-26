package com.ganten.peanuts.protocol.aeron;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.ganten.peanuts.common.entity.AeronProperties;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared Aeron runtime for optional embedded MediaDriver usage.
 */
@Slf4j
final class AeronRuntime {

    private static final ConcurrentHashMap<String, DriverRef> DRIVER_REFS = new ConcurrentHashMap<String, DriverRef>();
    private static final long CNNC_FILE_TIMEOUT_MS = 30_000;

    private AeronRuntime() {
    }

    static Aeron connect(AeronProperties properties) {
        String directory = properties.getDirectory();

        if (properties.isLaunchEmbeddedDriver()) {
            DriverRef ref = DRIVER_REFS.compute(directory, (key, existing) -> {
                if (existing == null) {
                    MediaDriver.Context driverContext = new MediaDriver.Context();
                    driverContext.aeronDirectoryName(directory);
                    driverContext.dirDeleteOnStart(true);
                    MediaDriver driver = MediaDriver.launch(driverContext);
                    log.info("MediaDriver launched, directory={}", directory);

                    awaitCnCDirectory(directory);
                    return new DriverRef(driver);
                }
                existing.refCount.incrementAndGet();
                return existing;
            });
            if (ref == null) {
                throw new IllegalStateException("Failed to initialize MediaDriver for directory=" + directory);
            }
        } else {
            awaitCnCDirectory(directory);
        }

        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(directory);
        return Aeron.connect(context);
    }

    private static void awaitCnCDirectory(String directory) {
        File requestedDir = new File(directory);
        File requestedCncFile = new File(requestedDir, "cnc.dat");

        long start = System.currentTimeMillis();
        while (true) {
            if (requestedCncFile.exists()) {
                log.info("CnC file ready: {}", requestedCncFile.getAbsolutePath());
                return;
            }

            if (System.currentTimeMillis() - start > CNNC_FILE_TIMEOUT_MS) {
                String requestedCncPath = requestedCncFile.getAbsolutePath();
                log.error("Timeout waiting for CnC file. requestedCnc={}, directoryExists={}",
                        requestedCncPath, requestedDir.exists());
                throw new RuntimeException("Timeout waiting for CnC file: " + requestedCncPath);
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for CnC file", e);
            }
        }
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

