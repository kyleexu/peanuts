package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.ExecutionReportCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

import lombok.extern.slf4j.Slf4j;

/**
 * match 执行回报（stream 2002）。
 */
@Slf4j
@Component
public class RaftExecutionReportLogSubscriber extends AbstractAeronSubscriber<ExecutionReportProto, ExecutionReportCodec> {

    public RaftExecutionReportLogSubscriber(
            @Qualifier("syncExecutionReportAeronProperties") AeronProperties aeronProperties) {
        super(aeronProperties, ExecutionReportCodec.getInstance());
    }

    @Override
    protected void onMessage(ExecutionReportProto message) {
        log.info("[raft] role=match stream={} entry=ExecutionReport orderId={} tradeId={} buyOrderId={} "
                + "sellOrderId={} userId={} contract={} side={} execType={} status={} price={} qty={} ts={}",
                Constants.AERON_STREAM_ID_EXECUTION_REPORT,
                message.getOrderId(),
                message.getTradeId(),
                message.getBuyOrderId(),
                message.getSellOrderId(),
                message.getUserId(),
                message.getContract(),
                message.getSide(),
                message.getExecType(),
                message.getOrderStatus(),
                message.getMatchedPrice(),
                message.getMatchedQuantity(),
                message.getTimestamp());
    }
}
