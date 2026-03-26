package com.ganten.peanuts.protocol.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.AeronMessage;

public class LockRequestCodec extends AbstractCodec<LockRequestProto> {

    private static final LockRequestCodec INSTANCE = new LockRequestCodec();

    private LockRequestCodec() {
        super();
    }

    public static LockRequestCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(LockRequestProto request) {
        String currency = request.getCurrency() == null ? "" : request.getCurrency().name();
        String amount = request.getAmount() == null ? "" : request.getAmount().toPlainString();
        int capacity = 8 + 8 + (4 + currency.length()) + (4 + amount.length()) + 8;
        byte[] bytes = new byte[Math.max(64, capacity)];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, request.getRequestId());
        offset += 8;
        buffer.putLong(offset, request.getUserId());
        offset += 8;
        offset += buffer.putStringAscii(offset, currency);
        offset += buffer.putStringAscii(offset, amount);
        buffer.putLong(offset, request.getTimestamp());
        offset += 8;
        return new AeronMessage(buffer, offset);
    }

    @Override
    public LockRequestProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        LockRequestProto request = new LockRequestProto();
        request.setRequestId(buffer.getLong(currentOffset));
        currentOffset += 8;
        request.setUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        String currency = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + currency.length();
        String amount = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + amount.length();
        request.setCurrency(currency.isEmpty() ? null : Currency.valueOf(currency));
        request.setAmount(amount.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amount));
        request.setTimestamp(buffer.getLong(currentOffset));
        return request;
    }
}
