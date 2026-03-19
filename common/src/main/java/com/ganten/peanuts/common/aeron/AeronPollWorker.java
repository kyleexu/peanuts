package com.ganten.peanuts.common.aeron;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public final class AeronPollWorker implements AutoCloseable {

    private final Thread thread;
    private volatile boolean running;

    private AeronPollWorker(String threadName, IntSupplier pollAction, Consumer<Throwable> errorHandler) {
        this.running = true;
        this.thread = new Thread(() -> runLoop(pollAction, errorHandler), threadName);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public static AeronPollWorker start(String threadName, IntSupplier pollAction, Consumer<Throwable> errorHandler) {
        if (pollAction == null) {
            throw new IllegalArgumentException("pollAction must not be null");
        }
        Consumer<Throwable> safeErrorHandler = errorHandler == null ? t -> {
        } : errorHandler;
        return new AeronPollWorker(threadName, pollAction, safeErrorHandler);
    }

    private void runLoop(IntSupplier pollAction, Consumer<Throwable> errorHandler) {
        IdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, 1, 1000);
        while (running) {
            try {
                int fragments = pollAction.getAsInt();
                idleStrategy.idle(fragments);
            } catch (Throwable throwable) {
                if (!running) {
                    break;
                }
                errorHandler.accept(throwable);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try {
            thread.join(1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
