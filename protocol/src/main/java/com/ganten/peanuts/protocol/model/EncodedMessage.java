package com.ganten.peanuts.protocol.model;

import org.agrona.concurrent.UnsafeBuffer;

public class EncodedMessage {

    private final UnsafeBuffer buffer;
    private final int length;

    public EncodedMessage(UnsafeBuffer buffer, int length) {
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
