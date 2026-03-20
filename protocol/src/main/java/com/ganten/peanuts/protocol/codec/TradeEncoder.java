package com.ganten.peanuts.protocol.codec;

import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.protocol.model.EncodedMessage;

@Component
public class TradeEncoder {

    public EncodedMessage encode(Trade trade) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;

        buffer.putLong(offset, trade.getTradeId());
        offset += 8;
        buffer.putLong(offset, trade.getBuyOrderId());
        offset += 8;
        buffer.putLong(offset, trade.getSellOrderId());
        offset += 8;
        buffer.putLong(offset, trade.getBuyUserId());
        offset += 8;
        buffer.putLong(offset, trade.getSellUserId());
        offset += 8;
        buffer.putInt(offset, trade.getContract().ordinal());
        offset += 4;
        offset += buffer.putStringAscii(offset, trade.getPrice() == null ? "" : trade.getPrice().toPlainString());
        offset += buffer.putStringAscii(offset, trade.getQuantity() == null ? "" : trade.getQuantity().toPlainString());
        buffer.putLong(offset, trade.getTimestamp());
        offset += 8;

        return new EncodedMessage(buffer, offset);
    }
}
