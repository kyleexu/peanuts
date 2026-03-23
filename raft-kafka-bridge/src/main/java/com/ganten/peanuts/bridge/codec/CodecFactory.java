package com.ganten.peanuts.bridge.codec;

import java.util.function.Function;

import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.codec.AbstractCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.model.OrderProto;
import com.ganten.peanuts.protocol.model.TradeProto;

public final class CodecFactory {

    private CodecFactory() {
    }

    public static CodecSpec specForStreamId(int streamId) {
        switch (streamId) {
            case 2003:
                return trade();
            case 2004:
                return orderBook();
            case 2002:
                return executionReport();
            case 2102:
                return lockResponse();
            case 2001:
                return order();
            default:
                throw new IllegalArgumentException("unsupported streamId: " + streamId);
        }
    }

    private static CodecSpec trade() {
        AbstractCodec codec = TradeCodec.getInstance();
        Function<Object, String> keyExtractor = message -> String.valueOf(((TradeProto) message).getTradeId());
        return new CodecSpec(codec, keyExtractor);
    }

    private static CodecSpec orderBook() {
        AbstractCodec codec = OrderBookCodec.getInstance();
        Function<Object, String> keyExtractor = message -> {
            OrderBookProto snap = (OrderBookProto) message;
            String contract = snap.getContract() == null ? "null" : snap.getContract().name();
            return contract + "-" + snap.getTimestamp();
        };
        return new CodecSpec(codec, keyExtractor);
    }

    private static CodecSpec executionReport() {
        AbstractCodec codec = ExecutionReportCodec.getInstance();
        Function<Object, String> keyExtractor = message -> String.valueOf(((ExecutionReportProto) message).getOrderId());
        return new CodecSpec(codec, keyExtractor);
    }

    private static CodecSpec lockResponse() {
        AbstractCodec codec = LockResponseCodec.getInstance();
        Function<Object, String> keyExtractor = message -> String.valueOf(((LockResponseProto) message).getRequestId());
        return new CodecSpec(codec, keyExtractor);
    }

    private static CodecSpec order() {
        AbstractCodec codec = OrderCodec.getInstance();
        Function<Object, String> keyExtractor = message -> String.valueOf(((OrderProto) message).getOrderId());
        return new CodecSpec(codec, keyExtractor);
    }

    public static final class CodecSpec {
        private final AbstractCodec codec;
        private final Function<Object, String> keyExtractor;

        private CodecSpec(AbstractCodec codec, Function<Object, String> keyExtractor) {
            this.codec = codec;
            this.keyExtractor = keyExtractor;
        }

        public AbstractCodec getCodec() {
            return codec;
        }

        public Function<Object, String> getKeyExtractor() {
            return keyExtractor;
        }
    }
}

