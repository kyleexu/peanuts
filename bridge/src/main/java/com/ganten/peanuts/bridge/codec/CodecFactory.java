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

    public static CodecSpec<?> specForStreamId(int streamId) {
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

    private static CodecSpec<TradeProto> trade() {
        AbstractCodec<TradeProto> codec = TradeCodec.getInstance();
        Function<TradeProto, String> keyExtractor = message -> String.valueOf(((TradeProto) message).getTradeId());
        return new CodecSpec<TradeProto>(codec, keyExtractor);
    }

    private static CodecSpec<OrderBookProto> orderBook() {
        AbstractCodec<OrderBookProto> codec = OrderBookCodec.getInstance();
        Function<OrderBookProto, String> keyExtractor = message -> {
            OrderBookProto snap = (OrderBookProto) message;
            String contract = snap.getContract() == null ? "null" : snap.getContract().name();
            return contract + "-" + snap.getTimestamp();
        };
        return new CodecSpec<>(codec, keyExtractor);
    }

    private static CodecSpec<ExecutionReportProto> executionReport() {
        AbstractCodec<ExecutionReportProto> codec = ExecutionReportCodec.getInstance();
        Function<ExecutionReportProto, String> keyExtractor = message -> String.valueOf(((ExecutionReportProto) message).getOrderId());
        return new CodecSpec<>(codec, keyExtractor);
    }

    private static CodecSpec<LockResponseProto> lockResponse() {
        AbstractCodec<LockResponseProto> codec = LockResponseCodec.getInstance();
        Function<LockResponseProto, String> keyExtractor = message -> String.valueOf(((LockResponseProto) message).getRequestId());
        return new CodecSpec<>(codec, keyExtractor);
    }

    private static CodecSpec<OrderProto> order() {
        AbstractCodec<OrderProto> codec = OrderCodec.getInstance();
        Function<OrderProto, String> keyExtractor = message -> String.valueOf(((OrderProto) message).getOrderId());
        return new CodecSpec<OrderProto>(codec, keyExtractor);
    }

    public static final class CodecSpec<T> {
        private final AbstractCodec<T> codec;
        private final Function<T, String> keyExtractor;

        private CodecSpec(AbstractCodec<T> codec, Function<T, String> keyExtractor) {
            this.codec = codec;
            this.keyExtractor = keyExtractor;
        }

        public AbstractCodec<T> getCodec() {
            return codec;
        }

        public Function<T, String> getKeyExtractor() {
            return keyExtractor;
        }
    }
}

