package com.ganten.peanuts.gateway.model;

import org.agrona.concurrent.UnsafeBuffer;

public class EncodedOrder {

    private final UnsafeBuffer buffer;
    private final int length;

    public EncodedOrder(UnsafeBuffer buffer, int length) {
        this.buffer = buffer;
        this.length = length;
    }

    public UnsafeBuffer getBuffer() {
        return buffer;
    }

    public int getLength() {
        return length;
    }
}
