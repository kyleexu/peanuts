package com.ganten.peanuts.market.codec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.OrderBookOrderSnapshot;
import com.ganten.peanuts.market.model.RawOrderBookSnapshot;

@Component
public class OrderBookMessageCodec {

    public RawOrderBookSnapshot decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        RawOrderBookSnapshot snapshot = new RawOrderBookSnapshot();

        int contractOrdinal = buffer.getInt(currentOffset);
        currentOffset += 4;
        snapshot.setContract(Contract.values()[contractOrdinal]);

        snapshot.setTimestamp(buffer.getLong(currentOffset));
        currentOffset += 8;

        int buyCount = buffer.getInt(currentOffset);
        currentOffset += 4;

        List<OrderBookOrderSnapshot> bidOrders = new ArrayList<OrderBookOrderSnapshot>(buyCount);
        for (int i = 0; i < buyCount; i++) {
            DecodeResult result = decodeOrder(buffer, currentOffset);
            currentOffset = result.nextOffset;
            if (result.order != null) {
                bidOrders.add(result.order);
            }
        }

        int sellCount = buffer.getInt(currentOffset);
        currentOffset += 4;

        List<OrderBookOrderSnapshot> askOrders = new ArrayList<OrderBookOrderSnapshot>(sellCount);
        for (int i = 0; i < sellCount; i++) {
            DecodeResult result = decodeOrder(buffer, currentOffset);
            currentOffset = result.nextOffset;
            if (result.order != null) {
                askOrders.add(result.order);
            }
        }

        snapshot.setBidOrders(bidOrders);
        snapshot.setAskOrders(askOrders);
        return snapshot;
    }

    private DecodeResult decodeOrder(DirectBuffer buffer, int offset) {
        int currentOffset = offset;

        long orderId = buffer.getLong(currentOffset);
        currentOffset += 8;
        long userId = buffer.getLong(currentOffset);
        currentOffset += 8;

        String priceText = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + priceText.length();

        String totalQuantityText = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + totalQuantityText.length();

        String filledQuantityText = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + filledQuantityText.length();

        long timestamp = buffer.getLong(currentOffset);
        currentOffset += 8;

        if (priceText.isEmpty() || totalQuantityText.isEmpty()) {
            return new DecodeResult(null, currentOffset);
        }

        BigDecimal price = new BigDecimal(priceText);
        BigDecimal total = new BigDecimal(totalQuantityText);
        BigDecimal filled = filledQuantityText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(filledQuantityText);
        BigDecimal remaining = total.subtract(filled);

        if (remaining.signum() <= 0) {
            return new DecodeResult(null, currentOffset);
        }

        OrderBookOrderSnapshot order = new OrderBookOrderSnapshot();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setPrice(price);
        order.setTotalQuantity(total);
        order.setFilledQuantity(filled);
        order.setRemainingQuantity(remaining);
        order.setTimestamp(timestamp);
        return new DecodeResult(order, currentOffset);
    }

    private static final class DecodeResult {
        private final OrderBookOrderSnapshot order;
        private final int nextOffset;

        private DecodeResult(OrderBookOrderSnapshot order, int nextOffset) {
            this.order = order;
            this.nextOffset = nextOffset;
        }
    }
}
