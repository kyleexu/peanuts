package com.ganten.peanuts.protocol.codec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

public class ExecutionReportCodec extends AbstractCodec<ExecutionReportProto> {

    private static final ExecutionReportCodec INSTANCE = new ExecutionReportCodec();

    private ExecutionReportCodec() {
        super();
    }

    public static ExecutionReportCodec getInstance() {
        return INSTANCE;
    }

    @Override
    public AeronMessage encode(ExecutionReportProto report) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;
        buffer.putLong(offset, report.getOrderId());
        offset += 8;
        buffer.putLong(offset, report.getUserId());
        offset += 8;
        buffer.putLong(offset, report.getCounterpartyOrderId());
        offset += 8;
        buffer.putInt(offset, report.getContract().ordinal());
        offset += 4;
        buffer.putInt(offset, report.getSide().ordinal());
        offset += 4;
        buffer.putInt(offset, report.getExecType().getCode());
        offset += 4;
        buffer.putInt(offset, report.getOrderStatus().getCode());
        offset += 4;
        offset += buffer.putStringAscii(offset,
                report.getMatchedPrice() == null ? "" : report.getMatchedPrice().toPlainString());
        offset += buffer.putStringAscii(offset,
                report.getMatchedQuantity() == null ? "" : report.getMatchedQuantity().toPlainString());
        buffer.putLong(offset, report.getTimestamp());
        offset += 8;

        return new AeronMessage(buffer, offset);
    }

    @Override
    public ExecutionReportProto decode(DirectBuffer buffer, int offset) {
        int currentOffset = offset;
        ExecutionReportProto report = new ExecutionReportProto();
        report.setOrderId(buffer.getLong(currentOffset));
        currentOffset += 8;
        report.setUserId(buffer.getLong(currentOffset));
        currentOffset += 8;
        report.setCounterpartyOrderId(buffer.getLong(currentOffset));
        currentOffset += 8;
        report.setContract(com.ganten.peanuts.common.enums.Contract.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;
        report.setSide(com.ganten.peanuts.common.enums.Side.values()[buffer.getInt(currentOffset)]);
        currentOffset += 4;
        int execTypeCode = buffer.getInt(currentOffset);
        currentOffset += 4;
        report.setExecType(com.ganten.peanuts.common.enums.ExecType.fromCode(execTypeCode));
        int orderStatusCode = buffer.getInt(currentOffset);
        currentOffset += 4;
        report.setOrderStatus(com.ganten.peanuts.common.enums.OrderStatus.fromCode(orderStatusCode));
        String matchedPrice = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + Math.max(matchedPrice.length(), 0);
        String matchedQuantity = buffer.getStringAscii(currentOffset);
        currentOffset += 4 + Math.max(matchedQuantity.length(), 0);
        report.setMatchedPrice(matchedPrice.isEmpty() ? null : new java.math.BigDecimal(matchedPrice));
        report.setMatchedQuantity(matchedQuantity.isEmpty() ? null : new java.math.BigDecimal(matchedQuantity));
        report.setTimestamp(buffer.getLong(currentOffset));
        // currentOffset += 8; // (not needed unless you want to use next offset)
        return report;
    }
}
