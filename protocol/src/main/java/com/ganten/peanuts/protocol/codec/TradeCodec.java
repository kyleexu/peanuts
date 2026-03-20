package com.ganten.peanuts.protocol.codec;

import java.math.BigDecimal;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.TradeProto;

public class TradeCodec extends AbstractCodec<TradeProto> {

    private static final TradeCodec INSTANCE = new TradeCodec();

    private TradeCodec() {
        super();
    }

    public static TradeCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(TradeProto trade) {
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

        return new AeronMessage(buffer, offset);
    }

    @Override
    public TradeProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        TradeProto trade = new TradeProto();
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

