package com.ganten.peanuts.engine.utils;

import java.math.BigDecimal;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

public class ExecutionReportBuilder {
    /**
     * 构建交易执行报告
     */
    public static ExecutionReportProto buildTradeReport(Order order, long buyOrderId, long sellOrderId,
            BigDecimal matchedPrice,
            BigDecimal matchedQuantity, long tradeId) {
        ExecutionReportProto report = new ExecutionReportProto();
        report.setOrderId(order.getOrderId());
        report.setUserId(order.getUserId());
        report.setContract(order.getContract());
        report.setSide(order.getSide());
        report.setExecType(ExecType.TRADE);
        report.setOrderStatus(order.getOrderStatus());
        report.setMatchedPrice(matchedPrice);
        report.setMatchedQuantity(matchedQuantity);
        report.setTimestamp(System.currentTimeMillis());
        report.setBuyOrderId(buyOrderId);
        report.setSellOrderId(sellOrderId);
        report.setTradeId(tradeId);
        return report;
    }

    public static ExecutionReportProto buildCancelReport(Order order) {
        ExecutionReportProto report = new ExecutionReportProto();
        report.setOrderId(order.getOrderId());
        report.setUserId(order.getUserId());
        report.setContract(order.getContract());
        report.setSide(order.getSide());
        report.setExecType(ExecType.CANCELED);
        report.setOrderStatus(order.getOrderStatus());
        report.setTimestamp(System.currentTimeMillis());
        return report;
    }

}
