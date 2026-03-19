package com.ganten.peanuts.gateway.account;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.AccountLockRequest;
import com.ganten.peanuts.common.entity.AccountLockResponse;
import com.ganten.peanuts.gateway.model.EncodedOrder;

@Component
public class AccountLockMessageCodec {

    public EncodedOrder encodeRequest(AccountLockRequest request) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, request.getRequestId());
        offset += 8;
        buffer.putLong(offset, request.getUserId());
        offset += 8;
        offset += buffer.putStringAscii(offset, request.getAsset() == null ? "" : request.getAsset());
        offset += buffer.putStringAscii(offset, request.getAmount() == null ? "" : request.getAmount().toPlainString());
        buffer.putLong(offset, request.getTimestamp());
        offset += 8;
        return new EncodedOrder(buffer, offset);
    }

    public AccountLockResponse decodeResponse(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        AccountLockResponse response = new AccountLockResponse();
        response.setRequestId(buffer.getLong(currentOffset));
        currentOffset += 8;
        response.setSuccess(buffer.getByte(currentOffset) == 1);
        currentOffset += 1;
        response.setMessage(buffer.getStringAscii(currentOffset));
        return response;
    }

    public AccountLockRequest decodeRequest(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        AccountLockRequest request = new AccountLockRequest();
        request.setRequestId(buffer.getLong(currentOffset));
        currentOffset += 8;
        request.setUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        String asset = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + asset.length();
        String amount = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + amount.length();
        request.setAsset(asset);
        request.setAmount(amount.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amount));
        request.setTimestamp(buffer.getLong(currentOffset));
        return request;
    }

    public EncodedOrder encodeResponse(AccountLockResponse response) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, response.getRequestId());
        offset += 8;
        buffer.putByte(offset, response.isSuccess() ? (byte) 1 : (byte) 0);
        offset += 1;
        offset += buffer.putStringAscii(offset, response.getMessage() == null ? "" : response.getMessage());
        return new EncodedOrder(buffer, offset);
    }
}
