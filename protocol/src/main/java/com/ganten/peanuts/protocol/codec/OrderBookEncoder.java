package com.ganten.peanuts.protocol.codec;

import java.util.PriorityQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.protocol.model.EncodedMessage;

/**
 * Encode order-book snapshots for Aeron transport.
 */
@Component
public class OrderBookEncoder {

    private static final int MAX_ORDERS_PER_SIDE = 50;

    public EncodedMessage encode(Contract contract, PriorityQueue<Order> buyOrders, PriorityQueue<Order> sellOrders) {
        byte[] bytes = new byte[4096];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;

        buffer.putInt(offset, contract.ordinal());
        offset += 4;

        buffer.putLong(offset, System.currentTimeMillis());
        offset += 8;

        offset = encodeSide(buffer, offset, buyOrders);
        offset = encodeSide(buffer, offset, sellOrders);

        return new EncodedMessage(buffer, offset);
    }

    private int encodeSide(UnsafeBuffer buffer, int offset, PriorityQueue<Order> orders) {
        int count = 0;
        int countOffset = offset;
        offset += 4;

        for (Order order : orders) {
            if (count >= MAX_ORDERS_PER_SIDE) {
                break;
            }

            buffer.putLong(offset, order.getOrderId());
            offset += 8;
            buffer.putLong(offset, order.getUserId());
            offset += 8;
            offset += buffer.putStringAscii(offset, order.getPrice() == null ? "" : order.getPrice().toPlainString());
            offset += buffer.putStringAscii(offset,
                    order.getTotalQuantity() == null ? "" : order.getTotalQuantity().toPlainString());
            offset += buffer.putStringAscii(offset,
                    order.getFilledQuantity() == null ? "" : order.getFilledQuantity().toPlainString());
            buffer.putLong(offset, order.getTimestamp());
            offset += 8;
            count++;
        }

        buffer.putInt(countOffset, count);
        return offset;
    }
}
