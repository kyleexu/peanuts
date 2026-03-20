package com.ganten.peanuts.engine.utils;

import java.math.BigDecimal;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

public class ExecutionReportBuilder {
    public static ExecutionReportProto buildTradeReport(Order order, long counterpartyOrderId, BigDecimal matchedPrice,
            BigDecimal matchedQuantity) {
        ExecutionReportProto report = buildReport(order, ExecType.TRADE, matchedPrice, matchedQuantity);
        report.setCounterpartyOrderId(counterpartyOrderId);
        return report;
    }

    public static ExecutionReportProto buildReport(Order order, ExecType execType, BigDecimal matchedPrice,
            BigDecimal matchedQuantity) {
        ExecutionReportProto report = new ExecutionReportProto();
        report.setOrderId(order.getOrderId());
        report.setUserId(order.getUserId());
        report.setContract(order.getContract());
        report.setSide(order.getSide());
        report.setExecType(execType);
        report.setOrderStatus(order.getOrderStatus());
        report.setMatchedPrice(matchedPrice);
        report.setMatchedQuantity(matchedQuantity);
        report.setTimestamp(System.currentTimeMillis());
        return report;
    }

}
