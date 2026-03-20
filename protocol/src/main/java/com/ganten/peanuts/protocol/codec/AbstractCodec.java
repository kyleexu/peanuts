package com.ganten.peanuts.protocol.codec;

import org.agrona.DirectBuffer;
import com.ganten.peanuts.protocol.model.AeronMessage;

public abstract class AbstractCodec<T> {

    protected AbstractCodec() {}

    /**
     * Encode the message to an AeronMessage.
     * <p>
     * @param message The message to encode.
     * @return The AeronMessage containing the encoded message.
     */
    public abstract AeronMessage encode(T message);

    /**
     * Decode the message from the buffer.
     * <p>
     * @param buffer The DirectBuffer containing the encoded message.
     * @param offset The offset in the buffer to start decoding.
     * @return The decoded message.
     */
    public abstract T decode(DirectBuffer buffer, int offset);
}
