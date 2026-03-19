package com.ganten.peanuts.gateway;

import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.model.EncodedOrder;

@Component
public class OrderEncoder {

    public EncodedOrder encode(Order order) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);

        int offset = 0;
        buffer.putLong(offset, order.getOrderId());
        offset += 8;
        buffer.putLong(offset, order.getUserId());
        offset += 8;
        buffer.putInt(offset, order.getContract().ordinal());
        offset += 4;
        buffer.putInt(offset, order.getSide().ordinal());
        offset += 4;
        buffer.putInt(offset, order.getOrderType().ordinal());
        offset += 4;
        buffer.putInt(offset, order.getTimeInForce().ordinal());
        offset += 4;
        offset += buffer.putStringAscii(offset, order.getPrice() == null ? "" : order.getPrice().toPlainString());
        offset += buffer.putStringAscii(offset,
                order.getTotalQuantity() == null ? "" : order.getTotalQuantity().toPlainString());
        buffer.putLong(offset, order.getTimestamp());
        offset += 8;

        return new EncodedOrder(buffer, offset);
    }
}
