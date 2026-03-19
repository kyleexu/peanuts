package com.ganten.peanuts.engine.codec;

import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.ExecutionReport;
import com.ganten.peanuts.engine.model.EncodedMessage;

@Component
public class ExecutionReportEncoder {

    public EncodedMessage encode(ExecutionReport report) {
        byte[] bytes = new byte[256];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;

        buffer.putLong(offset, report.getOrderId());
        offset += 8;
        buffer.putLong(offset, report.getUserId());
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

        return new EncodedMessage(buffer, offset);
    }
}
