package com.ganten.peanuts.gateway.mapping;

import java.math.BigDecimal;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;

/**
 * Gateway -> account-service lock protocol mapper.
 */
public final class LockRequestProtocolMapper {

    private LockRequestProtocolMapper() {
    }

    public static LockRequestProto toLockRequest(Order order, long requestId, long timestamp) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.getSide() == null) {
            throw new IllegalArgumentException("order.side must not be null");
        }

        Currency currency;
        BigDecimal amount;
        if (order.getSide() == Side.BUY) {
            if (order.getPrice() == null) {
                throw new IllegalArgumentException("buy order price required for account lock");
            }
            currency = order.getContract().getQuote();
            amount = order.getPrice().multiply(order.getTotalQuantity());
        } else {
            currency = order.getContract().getBase();
            amount = order.getTotalQuantity();
        }

        LockRequestProto request = new LockRequestProto();
        request.setRequestId(requestId);
        request.setUserId(order.getUserId());
        request.setCurrency(currency);
        request.setAmount(amount);
        request.setTimestamp(timestamp);
        return request;
    }

    public static LockResponseProto toFailureResponse(long requestId, String message) {
        LockResponseProto response = new LockResponseProto();
        response.setRequestId(requestId);
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}

