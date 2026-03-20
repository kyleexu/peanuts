package com.ganten.peanuts.protocol.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.common.enums.TimeInForce;

@Component
public class OrderDecoder {

    public Order decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;

        Order order = new Order();
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
