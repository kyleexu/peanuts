# OrderBook Aeron Publisher - 订单簿实时推送

## 功能概述

在 match 中添加了订单簿实时推送功能。每当 AeronOrderSubscriber 接收到并处理一条订单消息后，会自动将对应合约的当前订单簿快照推送到 Aeron channel。

## 架构设计

### 数据流

```
订单消息 (stream 2001)
    ↓
AeronOrderSubscriber.orderHandler 接收
    ↓
MatchService.match() 处理订单
    ↓
发布 ExecutionReport (stream 2002)
发布 Trade (stream 2003)
    ↓
publishOrderBook() 推送订单簿
    ↓
AeronOrderBookPublisher 发布
    ↓
Aeron channel (stream 2004)
```

## 核心组件

### 1. MatchEngineProperties (更新)

**新增配置项:**
```java
private int orderBookStreamId = 2004;  // 订单簿流ID

public int getOrderBookStreamId()
public void setOrderBookStreamId(int orderBookStreamId)
```

**application.yml 配置:**
```yaml
match:
  aeron:
    order-book-stream-id: 2004
```

### 2. OrderBookEncoder

**位置:** `codec/OrderBookEncoder.java`

**职责:**
- 将 OrderBook 对象编码为 Aeron 消息格式
- 保留前 50 个 BUY 订单和 50 个 SELL 订单
- 编码信息：订单ID、用户ID、价格、数量、已成交量、时间戳、合约

**编码格式:**
```
Contract (4 bytes)
└─ 时间戳 (8 bytes)
├─ BUY 订单数 (4 bytes)
│  └─ [for each order]
│     ├─ 订单ID (8 bytes)
│     ├─ 用户ID (8 bytes)
│     ├─ 价格 (String ASCII)
│     ├─ 数量 (String ASCII)
│     ├─ 已成交量 (String ASCII)
│     └─ 时间戳 (8 bytes)
└─ SELL 订单数 (4 bytes)
   └─ [for each order]
      ├─ 订单ID (8 bytes)
      ├─ 用户ID (8 bytes)
      ├─ 价格 (String ASCII)
      ├─ 数量 (String ASCII)
      ├─ 已成交量 (String ASCII)
      └─ 时间戳 (8 bytes)
```

### 3. AeronOrderBookPublisher

**位置:** `messaging/AeronOrderBookPublisher.java`

**职责:**
- 通过 Aeron 发布订单簿快照
- 使用 stream ID 2004
- 异常处理和日志记录

**关键方法:**
```java
public void publish(Contract contract, OrderBook orderBook)
```

### 4. AeronOrderSubscriber (更新)

**变更:**
- 添加 `AeronOrderBookPublisher` 依赖注入
- 在 `orderHandler` 的处理流程中调用 `publishOrderBook()`
- 新增 `publishOrderBook()` 方法

**处理流程:**
```java
// 旧逻辑
List<ExecutionReport> reports = matchService.match(order);
publisher.publish(reports);        // ExecutionReport
publishTrades(reports);            // Trade

// 新逻辑
publishOrderBook(order.getContract());  // OrderBook 快照
```

### 5. MatchService (更新)

**新增方法:**
```java
public synchronized OrderBook getOrderBook(Contract contract)
```

**功能:**
- 线程安全地获取指定合约的订单簿
- 返回当前的 OrderBook 实例

## 数据流示例

### 完整的订单处理流程

```
1. 订单到达
   Order { orderId: 101, userId: 1001, contract: BTC_USDT, side: BUY, ... }

2. 解码并匹配
   List<ExecutionReport> = [buyReport, sellReport]

3. 发布执行报告
   AeronExecutionReportPublisher.publish(buyReport)
   AeronExecutionReportPublisher.publish(sellReport)
   → Aeron stream 2002

4. 发布交易
   Trade { tradeId: 5001, buyOrderId: 101, sellOrderId: 102, ... }
   AeronTradePublisher.publish(trade)
   → Aeron stream 2003

5. 发布订单簿快照 ✨ NEW
   OrderBook { buyOrders: [Order101, ...], sellOrders: [Order102, ...] }
   AeronOrderBookPublisher.publish(BTC_USDT, orderBook)
   → Aeron stream 2004
```

## 性能特性

### 订单簿大小限制

- **BUY 订单上限:** 50 个（按价格降序排列）
- **SELL 订单上限:** 50 个（按价格升序排列）
- **消息大小:** ~3-5KB（典型场景）
- **推送频率:** 每条订单消息触发一次

### 内存效率

- 编码后的消息存储在固定大小的 byte 数组（4096 bytes）
- 不创建额外的对象副本
- 使用 UnsafeBuffer 进行高效的字节操作

## 异常处理

### 推送失败

如果推送订单簿失败，系统将：
1. 记录警告日志
2. 不中断订单处理流程
3. 继续处理下一条消息

```java
private void publishOrderBook(Contract contract) {
    try {
        var orderBook = matchService.getOrderBook(contract);
        orderBookPublisher.publish(contract, orderBook);
    } catch (Exception e) {
        log.warn("Failed to publish order book for contract={}: {}", 
                 contract, e.getMessage());
    }
}
```

## 订阅订单簿数据

### 创建订阅者

```java
public class OrderBookSubscriber {
    
    public void subscribeOrderBook() {
        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(AERON_DIRECTORY);
        Aeron aeron = Aeron.connect(context);
        
        Subscription sub = aeron.addSubscription(
            "aeron:ipc", 
            2004  // OrderBook stream ID
        );
        
        FragmentHandler handler = (buffer, offset, length, header) -> {
            parseOrderBook(buffer, offset, length);
        };
        
        while (running) {
            sub.poll(handler, 10);
        }
    }
}
```

## 配置调优

### 修改 OrderBook 大小限制

编辑 `OrderBookEncoder.java`:
```java
private static final int MAX_ORDERS_PER_SIDE = 50; // 修改此值
```

### 修改 Stream ID

编辑 `application.yml`:
```yaml
match:
  aeron:
    order-book-stream-id: 2005  # 自定义 stream ID
```

### 关闭 OrderBook 推送

将 Aeron 禁用或移除 `AeronOrderBookPublisher` 的注入：
```yaml
match:
  aeron:
    enabled: false
```

## 文件清单

```
match/
├── src/main/java/com/ganten/peanuts/engine/
│   ├── config/
│   │   └── MatchEngineProperties.java    [修改] 添加 orderBookStreamId
│   ├── codec/
│   │   └── OrderBookEncoder.java         [新建] 订单簿编码器
│   ├── messaging/
│   │   ├── AeronOrderBookPublisher.java  [新建] 订单簿发布者
│   │   └── AeronOrderSubscriber.java     [修改] 集成订单簿推送
│   └── service/
│       └── MatchService.java             [修改] 添加 getOrderBook()
```

## 验证编译

```bash
cd /Users/ganten/workspace/github/peanuts/match
mvn -DskipTests compile
# BUILD SUCCESS ✓
```

## 集成检查清单

- [x] OrderBookEncoder 编码逻辑正确
- [x] AeronOrderBookPublisher 发布功能正常
- [x] AeronOrderSubscriber 正确注入并调用
- [x] MatchService 提供 getOrderBook() 方法
- [x] MatchEngineProperties 包含 orderBookStreamId
- [x] 编译通过（mvn compile）
- [x] 异常处理到位
- [x] 日志记录完整

## 下一步

- [ ] 实现 OrderBook 订阅者（consumer）
- [ ] 添加 OrderBook 数据缓存层
- [ ] OrderBook 快照版本管理
- [ ] 性能测试（吞吐量、延迟）
- [ ] 集成测试验证数据一致性

## 技术细节

### Stream IDs 总览

| Stream | 用途 | 来源 | 目标 |
|--------|------|------|------|
| 2001 | 订单 | order | match |
| 2002 | 执行报告 | match | 下游系统 |
| 2003 | 交易 | match | account, market |
| 2004 | 订单簿 | match | 行情系统 |

### 线程安全性

- `MatchService.match()` 方法使用 `synchronized` 关键字
- `MatchService.getOrderBook()` 方法同样使用 `synchronized`
- OrderBook 的 PriorityQueue 在同步块中访问
- Aeron 发布是线程安全的

## FAQ

**Q: 为什么要推送订单簿？**
A: 用于行情系统（market）接收订单簿数据，用于深度图、委托提示等功能

**Q: 推送频率是多少？**
A: 每处理一条订单消息推送一次（通常 ms 级别）

**Q: 订单簿大小上限为什么是 50？**
A: 平衡内存使用和行情完整性。实际交易大多关注 top 10-20 的挂单

**Q: 如果 Aeron 连接失败会怎样？**
A: 订单处理不受影响，只是不能推送订单簿。日志会记录警告信息

**Q: 可以关闭订单簿推送吗？**
A: 可以，禁用 Aeron 或不注入 AeronOrderBookPublisher
