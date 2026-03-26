package com.ganten.peanuts.protocol.aeron;

import java.io.File;
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
    private static final long CNNC_FILE_TIMEOUT_MS = 30_000;

    private AeronRuntime() {
    }

    static Aeron connect(AeronProperties properties) {
        String requestedDirectory = properties.getDirectory();
        String resolvedDirectory = requestedDirectory;

        if (properties.isLaunchEmbeddedDriver()) {
            DriverRef ref = DRIVER_REFS.compute(requestedDirectory, (key, existing) -> {
                if (existing == null) {
                    MediaDriver.Context driverContext = new MediaDriver.Context();
                    driverContext.aeronDirectoryName(requestedDirectory);
                    MediaDriver driver = MediaDriver.launchEmbedded(driverContext);
                    log.info("Embedded MediaDriver launched, directory={}", requestedDirectory);

                    String cncDirectory = awaitCnCDirectory(requestedDirectory);
                    return new DriverRef(driver, cncDirectory);
                }
                existing.refCount.incrementAndGet();
                return existing;
            });
            resolvedDirectory = ref.resolvedDirectory;
        } else {
            resolvedDirectory = awaitCnCDirectory(requestedDirectory);
        }

        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(resolvedDirectory);
        return Aeron.connect(context);
    }

    private static String awaitCnCDirectory(String directoryRequested) {
        File requestedDir = new File(directoryRequested);
        File requestedCncFile = new File(requestedDir, "cnc.dat");

        String baseName = requestedDir.getName();
        File parentDir = requestedDir.getParentFile();

        long start = System.currentTimeMillis();
        while (true) {
            if (requestedCncFile.exists()) {
                log.info("CnC file ready: {}", requestedCncFile.getAbsolutePath());
                return directoryRequested;
            }

            // Some embedded MediaDriver implementations may create a suffixed directory, e.g.:
            //   aeron-ganten -> aeron-ganten-<uuid>
            // In that case we find the latest matching directory that already has cnc.dat.
            if (parentDir != null && parentDir.exists()) {
                File[] children = parentDir.listFiles(f ->
                        f.isDirectory() && f.getName().startsWith(baseName + "-"));
                if (children != null && children.length > 0) {
                    File best = null;
                    long bestModified = -1;
                    for (File child : children) {
                        File cnc = new File(child, "cnc.dat");
                        if (cnc.exists()) {
                            long modified = cnc.lastModified();
                            if (modified > bestModified) {
                                bestModified = modified;
                                best = child;
                            }
                        }
                    }
                    if (best != null) {
                        log.info("CnC file ready (resolved): {}", new File(best, "cnc.dat").getAbsolutePath());
                        return best.getAbsolutePath();
                    }
                }
            }

            if (System.currentTimeMillis() - start > CNNC_FILE_TIMEOUT_MS) {
                String requestedCncPath = requestedCncFile.getAbsolutePath();
                long parentCount = (parentDir != null && parentDir.exists() && parentDir.isDirectory())
                        ? (parentDir.listFiles() == null ? 0 : parentDir.listFiles().length)
                        : 0;
                log.error("Timeout waiting for CnC file. requestedCnc={}, directoryExists={}, parentDir={}, parentFilesCount={}",
                        requestedCncPath, requestedDir.exists(), parentDir, parentCount);
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
        private final String resolvedDirectory;
        private final AtomicInteger refCount = new AtomicInteger(1);

        private DriverRef(MediaDriver driver, String resolvedDirectory) {
            this.driver = driver;
            this.resolvedDirectory = resolvedDirectory;
        }
    }
}

