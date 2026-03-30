package com.ganten.peanuts.maker.util;

import java.util.concurrent.atomic.AtomicLong;

public final class OrderIdGenerator {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private OrderIdGenerator() {}

    public static long nextId() {
        return SEQ.incrementAndGet();
    }
}
