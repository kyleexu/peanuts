package com.ganten.peanuts.market.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;

@Component
public class TradeMessageCodec {

    public Trade decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        Trade trade = new Trade();
        trade.setTradeId(buffer.getLong(currentOffset));
        currentOffset += 8;
        trade.setBuyOrderId(buffer.getLong(currentOffset));
        currentOffset += 8;
        trade.setSellOrderId(buffer.getLong(currentOffset));
        currentOffset += 8;
        trade.setBuyUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        trade.setSellUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        trade.setContract(Contract.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;

        String price = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + price.length();
        String quantity = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + quantity.length();

        trade.setPrice(price.isEmpty() ? BigDecimal.ZERO : new BigDecimal(price));
        trade.setQuantity(quantity.isEmpty() ? BigDecimal.ZERO : new BigDecimal(quantity));
        trade.setTimestamp(buffer.getLong(currentOffset));
        return trade;
    }
}
