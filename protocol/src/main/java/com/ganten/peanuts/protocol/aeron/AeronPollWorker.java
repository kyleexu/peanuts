package com.ganten.peanuts.protocol.aeron;

import java.util.function.Consumer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public final class AeronPollWorker implements AutoCloseable {

    private final Thread thread;
    private volatile boolean running;

    private AeronPollWorker(Runnable pollAction, Consumer<Throwable> errorHandler) {
        this.running = true;
        this.thread = new Thread(() -> runLoop(pollAction, errorHandler));
        this.thread.setDaemon(true);
        this.thread.setName("aeron-poll-worker");
        this.thread.start();
    }

    public static AeronPollWorker start(Runnable pollAction, Consumer<Throwable> errorHandler) {
        return new AeronPollWorker(pollAction, errorHandler);
    }

    private void runLoop(Runnable pollAction, Consumer<Throwable> errorHandler) {
        IdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, 1, 1000);
        while (running) {
            try {
                pollAction.run();
                idleStrategy.idle();
            } catch (Throwable throwable) {
                if (!running) {
                    break;
                }
                if (errorHandler != null) {
                    errorHandler.accept(throwable);
                }
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
