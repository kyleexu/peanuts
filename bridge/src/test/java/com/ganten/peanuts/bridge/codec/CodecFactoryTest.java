package com.ganten.peanuts.bridge.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganten.peanuts.protocol.codec.AbstractCodec;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;

import org.junit.jupiter.api.Test;

public class CodecFactoryTest {

    @Test
    public void shouldPickTradeCodecAndExtractKey() throws Exception {
        CodecFactory.CodecSpec spec = CodecFactory.specForStreamId(2003);
        assertNotNull(spec);

        AbstractCodec codec = spec.getCodec();
        assertEquals(TradeCodec.getInstance().getClass(), codec.getClass());

        TradeProto trade = new TradeProto();
        trade.setTradeId(123L);

        String key = spec.getKeyExtractor().apply(trade);
        assertEquals("123", key);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(trade);
        // JSON should not be empty
        assertNotNull(json);
        assertEquals(true, json.length() > 0);
    }

    @Test
    public void shouldPickOtherSupportedCodecs() {
        assertEquals(TradeCodec.getInstance().getClass(), CodecFactory.specForStreamId(2003).getCodec().getClass());
        assertEquals(OrderBookCodec.getInstance().getClass(), CodecFactory.specForStreamId(2004).getCodec().getClass());
        assertEquals(ExecutionReportCodec.getInstance().getClass(), CodecFactory.specForStreamId(2002).getCodec().getClass());
        assertEquals(LockResponseCodec.getInstance().getClass(), CodecFactory.specForStreamId(2102).getCodec().getClass());
        assertEquals(OrderCodec.getInstance().getClass(), CodecFactory.specForStreamId(2001).getCodec().getClass());
    }
}

