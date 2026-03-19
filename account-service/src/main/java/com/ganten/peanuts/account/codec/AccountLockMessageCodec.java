package com.ganten.peanuts.account.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.AccountLockRequest;
import com.ganten.peanuts.common.entity.AccountLockResponse;
import com.ganten.peanuts.common.enums.Currency;

@Component
public class AccountLockMessageCodec {

    public AccountLockRequest decodeRequest(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        AccountLockRequest request = new AccountLockRequest();
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

    public UnsafeBuffer encodeResponse(AccountLockResponse response) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, response.getRequestId());
        offset += 8;
        buffer.putByte(offset, response.isSuccess() ? (byte) 1 : (byte) 0);
        offset += 1;
        buffer.putStringAscii(offset, response.getMessage() == null ? "" : response.getMessage());
        return buffer;
    }
}
