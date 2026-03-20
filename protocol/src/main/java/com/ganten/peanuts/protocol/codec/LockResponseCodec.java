package com.ganten.peanuts.protocol.codec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.protocol.model.AeronMessage;

public class LockResponseCodec extends AbstractCodec<LockResponseProto> {

    private static final LockResponseCodec INSTANCE = new LockResponseCodec();

    private LockResponseCodec() {
        super();
    }

    public static LockResponseCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(LockResponseProto response) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, response.getRequestId());
        offset += 8;
        buffer.putByte(offset, response.isSuccess() ? (byte) 1 : (byte) 0);
        offset += 1;
        offset += buffer.putStringAscii(offset, response.getMessage() == null ? "" : response.getMessage());
        return new AeronMessage(buffer, offset);
    }

    @Override
    public LockResponseProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        LockResponseProto response = new LockResponseProto();
        response.setRequestId(buffer.getLong(currentOffset));
        currentOffset += 8;
        response.setSuccess(buffer.getByte(currentOffset) == 1);
        currentOffset += 1;
        response.setMessage(buffer.getStringAscii(currentOffset));
        return response;
    }
}
