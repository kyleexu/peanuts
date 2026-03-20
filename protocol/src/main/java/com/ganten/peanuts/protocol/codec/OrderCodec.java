package com.ganten.peanuts.protocol.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.common.enums.TimeInForce;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.OrderProto;

public class OrderCodec extends AbstractCodec<OrderProto> {

    private static final OrderCodec INSTANCE = new OrderCodec();

    private OrderCodec() {
        super();
    }

    public static OrderCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(OrderProto order) {
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
        offset += buffer.putStringAscii(offset,
                order.getPrice() == null ? "" : order.getPrice().toPlainString());
        offset += buffer.putStringAscii(offset,
                order.getTotalQuantity() == null ? "" : order.getTotalQuantity().toPlainString());
        buffer.putLong(offset, order.getTimestamp());
        offset += 8;
        buffer.putInt(offset, order.getSource() == null ? -1 : order.getSource().ordinal());
        offset += 4;
        buffer.putInt(offset, order.getAction() == null ? -1 : order.getAction().ordinal());
        offset += 4;
        buffer.putLong(offset, order.getTargetOrderId());
        offset += 8;

        return new AeronMessage(buffer, offset);
    }

    @Override
    public OrderProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;

        OrderProto order = new OrderProto();
        order.setOrderId(buffer.getLong(currentOffset));
        currentOffset += 8;
        order.setUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        order.setContract(Contract.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;
        order.setSide(Side.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;
        order.setOrderType(OrderType.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;
        order.setTimeInForce(TimeInForce.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;

        String price = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + Math.max(price.length(), 0);
        String totalQuantity = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + Math.max(totalQuantity.length(), 0);

        order.setPrice(price.isEmpty() ? null : new BigDecimal(price));
        order.setTotalQuantity(totalQuantity.isEmpty() ? null : new BigDecimal(totalQuantity));
        order.setTimestamp(buffer.getLong(currentOffset));
        currentOffset += 8;

        int sourceOrdinal = buffer.getInt(currentOffset);
        if (sourceOrdinal >= 0) {
            order.setSource(Source.values()[sourceOrdinal]);
        }
        currentOffset += 4;

        int actionOrdinal = buffer.getInt(currentOffset);
        if (actionOrdinal >= 0) {
            order.setAction(OrderAction.values()[actionOrdinal]);
        }
        currentOffset += 4;

        order.setTargetOrderId(buffer.getLong(currentOffset));
        return order;
    }
}

