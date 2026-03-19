package com.ganten.peanuts.engine.utils;

import java.math.BigDecimal;
import com.ganten.peanuts.common.entity.ExecutionReport;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.ExecType;

public class ExecutionReportBuilder {
    public static ExecutionReport buildTradeReport(Order order, long counterpartyOrderId, BigDecimal matchedPrice,
            BigDecimal matchedQuantity) {
        ExecutionReport report = buildReport(order, ExecType.TRADE, matchedPrice, matchedQuantity);
        report.setCounterpartyOrderId(counterpartyOrderId);
        return report;
    }

    public static ExecutionReport buildReport(Order order, ExecType execType, BigDecimal matchedPrice,
            BigDecimal matchedQuantity) {
        ExecutionReport report = new ExecutionReport();
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
