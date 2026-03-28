package com.ganten.peanuts.maker.cache;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.maker.model.OrderSubmitRequest;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderExecutionStateCache {

    private final Map<Long, OrderExecutionState> byOrderId = new ConcurrentHashMap<Long, OrderExecutionState>();

    public void registerSubmittedOrder(OrderSubmitRequest order, String strategyTag) {
        if (order == null || order.getOrderId() == null || order.getOrderId().longValue() <= 0L) {
            return;
        }
        if (order.getAction() == null || order.getAction().getCode() != 1) {
            return;
        }

        long now = System.currentTimeMillis();
        byOrderId.putIfAbsent(order.getOrderId(), new OrderExecutionState(order.getOrderId().longValue(),
                order.getUserId(), order.getContract(), strategyTag, now));
    }

    public void applyExecutionReport(ExecutionReportProto report) {
        if (report == null || report.getOrderId() <= 0L) {
            return;
        }

        OrderExecutionState state = byOrderId.get(report.getOrderId());
        if (state == null) {
            return;
        }
        log.info("Applying execution report to order execution state. orderId={}, execType={}, orderStatus={}, matchedQty={}, matchedPrice={}",
                report.getOrderId(), report.getExecType(), report.getOrderStatus(),
                report.getMatchedQuantity(), report.getMatchedPrice());
        state.apply(report);
    }

    public OrderExecutionState get(long orderId) {
        return byOrderId.get(orderId);
    }

    public void remove(long orderId) {
        byOrderId.remove(orderId);
    }

    public static final class OrderExecutionState {
        private final long orderId;
        private final long userId;
        private final Contract contract;
        private final String strategyTag;
        private final long submittedAtMs;

        private volatile long lastUpdateAtMs;
        private volatile ExecType lastExecType;
        private volatile OrderStatus orderStatus;
        private volatile BigDecimal totalMatchedQuantity = BigDecimal.ZERO;
        private volatile BigDecimal lastMatchedPrice = BigDecimal.ZERO;

        public OrderExecutionState(long orderId, Long userId, Contract contract, String strategyTag,
                long submittedAtMs) {
            this.orderId = orderId;
            this.userId = userId == null ? 0L : userId.longValue();
            this.contract = contract;
            this.strategyTag = strategyTag;
            this.submittedAtMs = submittedAtMs;
            this.lastUpdateAtMs = submittedAtMs;
        }

        public synchronized void apply(ExecutionReportProto report) {
            if (report.getExecType() != null) {
                this.lastExecType = report.getExecType();
            }
            if (report.getOrderStatus() != null) {
                this.orderStatus = report.getOrderStatus();
            }
            if (report.getMatchedQuantity() != null && report.getMatchedQuantity().signum() > 0) {
                this.totalMatchedQuantity = this.totalMatchedQuantity.add(report.getMatchedQuantity());
            }
            if (report.getMatchedPrice() != null && report.getMatchedPrice().signum() > 0) {
                this.lastMatchedPrice = report.getMatchedPrice();
            }
            if (report.getTimestamp() > 0L) {
                this.lastUpdateAtMs = report.getTimestamp();
            } else {
                this.lastUpdateAtMs = System.currentTimeMillis();
            }
        }

        public long getOrderId() {
            return orderId;
        }

        public long getUserId() {
            return userId;
        }

        public Contract getContract() {
            return contract;
        }

        public String getStrategyTag() {
            return strategyTag;
        }

        public long getSubmittedAtMs() {
            return submittedAtMs;
        }

        public long getLastUpdateAtMs() {
            return lastUpdateAtMs;
        }

        public ExecType getLastExecType() {
            return lastExecType;
        }

        public OrderStatus getOrderStatus() {
            return orderStatus;
        }

        public BigDecimal getTotalMatchedQuantity() {
            return totalMatchedQuantity;
        }

        public BigDecimal getLastMatchedPrice() {
            return lastMatchedPrice;
        }

        public boolean hasAnyTrade() {
            return totalMatchedQuantity != null && totalMatchedQuantity.signum() > 0;
        }

        public boolean isTerminal() {
            if (orderStatus == OrderStatus.FILLED || orderStatus == OrderStatus.CANCELED
                    || orderStatus == OrderStatus.REJECTED || orderStatus == OrderStatus.EXPIRED) {
                return true;
            }
            return lastExecType == ExecType.CANCELED || lastExecType == ExecType.REJECTED;
        }
    }
}
