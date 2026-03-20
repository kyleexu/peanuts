package com.ganten.peanuts.protocol.codec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.OrderBookSnapshotProto;
import com.ganten.peanuts.protocol.model.OrderBookSnapshotProto.OrderLevel;

public class OrderBookCodec extends AbstractCodec<OrderBookSnapshotProto> {

    private static final OrderBookCodec INSTANCE = new OrderBookCodec();

    private static final int MAX_ORDERS_PER_SIDE = 50;

    private OrderBookCodec() {
        super();
    }

    public static OrderBookCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(OrderBookSnapshotProto snapshot) {
        byte[] bytes = new byte[4096];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;

        buffer.putInt(offset, snapshot.getContract().ordinal());
        offset += 4;

        buffer.putLong(offset, snapshot.getTimestamp());
        offset += 8;

        offset = encodeSide(buffer, offset, snapshot.getBidOrders());
        offset = encodeSide(buffer, offset, snapshot.getAskOrders());

        return new AeronMessage(buffer, offset);
    }

    @Override
    public OrderBookSnapshotProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        OrderBookSnapshotProto snapshot = new OrderBookSnapshotProto();

        int contractOrdinal = buffer.getInt(currentOffset);
        currentOffset += 4;
        snapshot.setContract(Contract.values()[contractOrdinal]);

        snapshot.setTimestamp(buffer.getLong(currentOffset));
        currentOffset += 8;

        int buyCount = buffer.getInt(currentOffset);
        currentOffset += 4;

        List<OrderLevel> bidOrders = new ArrayList<OrderLevel>(buyCount);
        for (int i = 0; i < buyCount; i++) {
            DecodeResult result = decodeOrder(buffer, currentOffset);
            currentOffset = result.nextOffset;
            if (result.order != null) {
                bidOrders.add(result.order);
            }
        }

        int sellCount = buffer.getInt(currentOffset);
        currentOffset += 4;

        List<OrderLevel> askOrders = new ArrayList<OrderLevel>(sellCount);
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

    private int encodeSide(UnsafeBuffer buffer, int offset, List<OrderLevel> orders) {
        int count = 0;
        int countOffset = offset;
        offset += 4;

        for (OrderLevel order : orders) {
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

    private static DecodeResult decodeOrder(DirectBuffer buffer, int offset) {
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

        OrderLevel order = new OrderLevel();
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
        private final OrderLevel order;
        private final int nextOffset;

        private DecodeResult(OrderLevel order, int nextOffset) {
            this.order = order;
            this.nextOffset = nextOffset;
        }
    }
}

