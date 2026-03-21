package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;

import lombok.extern.slf4j.Slf4j;

/**
 * match → account / market 成交（stream 2003）；此处按撮合侧复制日志打印一条。
 */
@Slf4j
@Component
public class RaftTradeLogSubscriber extends AbstractAeronSubscriber<TradeProto, TradeCodec> {

    public RaftTradeLogSubscriber(@Qualifier("syncTradeAeronProperties") AeronProperties aeronProperties) {
        super(aeronProperties, TradeCodec.getInstance());
    }

    @Override
    protected void onMessage(TradeProto message) {
        log.info("[raft] role=match stream={} entry=Trade tradeId={} buyOrderId={} sellOrderId={} "
                + "buyUserId={} sellUserId={} contract={} price={} qty={} ts={}",
                Constants.AERON_STREAM_ID_TRADE,
                message.getTradeId(),
                message.getBuyOrderId(),
                message.getSellOrderId(),
                message.getBuyUserId(),
                message.getSellUserId(),
                message.getContract(),
                message.getPrice(),
                message.getQuantity(),
                message.getTimestamp());
    }
}
