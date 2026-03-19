package com.ganten.peanuts.engine.codec;

import java.util.PriorityQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.engine.model.EncodedMessage;
import com.ganten.peanuts.engine.model.OrderBook;

/**
 * 订单簿编码器，将 OrderBook 编码为 Aeron 消息
 */
@Component
public class OrderBookEncoder {

    private static final int MAX_ORDERS_PER_SIDE = 50; // 每一方最多保留50个订单

    public EncodedMessage encode(Contract contract, OrderBook orderBook) {
        byte[] bytes = new byte[4096];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        int offset = 0;

        // 编码 Contract
        buffer.putInt(offset, contract.ordinal());
        offset += 4;

        // 编码时间戳
        buffer.putLong(offset, System.currentTimeMillis());
        offset += 8;

        // 编码 BUY 订单 (降序排列)
        offset = encodeSide(buffer, offset, orderBook.getBuyOrders(), true);

        // 编码 SELL 订单 (升序排列)
        offset = encodeSide(buffer, offset, orderBook.getSellOrders(), false);

        return new EncodedMessage(buffer, offset);
    }

    /**
     * 编码订单一方的数据
     *
     * @param buffer 缓冲区
     * @param offset 当前偏移
     * @param orders 订单队列
     * @param isBuyOrders 是否是 BUY 订单
     * @return 新的偏移位置
     */
    private int encodeSide(UnsafeBuffer buffer, int offset, PriorityQueue<Order> orders, boolean isBuyOrders) {
        int count = 0;
        int countOffset = offset;
        offset += 4; // 预留位置存储订单数

        for (Order order : orders) {
            if (count >= MAX_ORDERS_PER_SIDE) {
                break;
            }

            // 编码订单 ID
            buffer.putLong(offset, order.getOrderId());
            offset += 8;

            // 编码用户 ID
            buffer.putLong(offset, order.getUserId());
            offset += 8;

            // 编码价格
            offset += buffer.putStringAscii(offset, order.getPrice() == null ? "" : order.getPrice().toPlainString());

            // 编码总数量
            offset += buffer.putStringAscii(offset,
                    order.getTotalQuantity() == null ? "" : order.getTotalQuantity().toPlainString());

            // 编码已成交数量
            offset += buffer.putStringAscii(offset,
                    order.getFilledQuantity() == null ? "" : order.getFilledQuantity().toPlainString());

            // 编码时间戳
            buffer.putLong(offset, order.getTimestamp());
            offset += 8;

            count++;
        }

        // 回写订单数
        buffer.putInt(countOffset, count);

        return offset;
    }
}
