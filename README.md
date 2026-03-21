# Aeron Integration Guide

## 1. 目的

本文档用于说明 peanuts 项目中 Aeron 的使用方式、消息链路、性能策略和常见问题。

## 2. 当前使用场景

项目里主要有两类 Aeron 交互语义：

1. 请求-响应（Request/Response）

- 示例：order 请求 account 做资金锁定。
- 发送端通过 `Publication.offer(...)` 发请求。
- 再通过响应流读取对应 `requestId` 的结果并等待完成。

1. 单向分发（One-way / Fire-and-forget）

- 示例：order 向 match 异步分发订单。
- 仅 `offer(...)` 投递，不等待回包。

注意：Aeron 底层发送 API 都是 `offer`，区别在业务协议是否定义了“回响应流”。

## 3. 核心链路

### 3.1 下单主链路

1. order 收到下单请求。
2. `AccountLockAeronClient` 发锁定请求到 account。
3. account 消费请求并返回锁定结果。
4. 锁定成功后，order 通过 `AeronOrderDispatcher` 分发订单到 match（发送端：`Order` -> `protocol.OrderCommand` -> `OrderCodec.encode` -> Aeron `offer(...)`）。
5. match 的 `AeronOrderSubscriber` 订阅订单并撮合（接收端：`OrderCodec.decode` 解码 `OrderCommand` -> 映射为 domain `Order` -> `matchService.match(...)`）。
6. match 发布两类下游数据：
  - 执行回报 execution report：`AeronExecutionReportPublisher`（协议模型在 `protocol.model.ExecutionReport`）。
  - 成交 trade：`AeronTradePublisher`（domain `Trade` -> `protocol.TradeEvent` -> `TradeCodec.encode`）。
7. match 还会发布订单簿快照到下游（domain `OrderBook` -> `protocol.RawOrderBookSnapshot` -> `OrderBookCodec.encode`）。
8. account 的 `AccountTradeAeronProcessor` 消费成交流（`TradeCodec.decode` 解码 `TradeEvent` -> 映射为 domain `Trade` -> `accountService.applyTrade(...)`）。
9. market 消费成交与订单簿流：
  - 成交：`MarketTradeAeronSubscriber`（`TradeEvent` -> domain `Trade` -> `marketDataService.onTrade(...)`）
  - 订单簿：`MarketOrderBookAeronSubscriber`（`RawOrderBookSnapshot` -> `OrderBookAggregationService.onOrderBook(...)`）

## 4. 关键 Stream 约定

按照业务流程，顺序如下：


| streamId | 发送                | 接收                | 用途     | 传输模型                                  |
| -------- | ----------------- | ----------------- | ------ | ------------------------------------- |
| 2101     | `order`   | `account` | 资金锁定请求 | `protocol.model.AccountLockRequest`   |
| 2102     | `account` | `order`   | 资金锁定响应 | `protocol.model.AccountLockResponse`  |
| 2001     | `order`   | `match`    | 订单入站   | `protocol.model.OrderCommand`         |
| 2002     | `match`    | `null`            | 执行回报   | `protocol.model.ExecutionReport`      |
| 2003     | `match`    | `account` | 成交明细   | `protocol.model.TradeEvent`           |
| 2003     | `match`    | `market`  | 成交明细   | `protocol.model.TradeEvent`           |
| 2004     | `match`    | `market`  | 订单簿快照  | `protocol.model.RawOrderBookSnapshot` |


对于 `传输模型` 编解码器整理：


| 传输模型                   | streamId | 编码器                                           | 解码器                                           |
| ---------------------- | -------- | --------------------------------------------- | --------------------------------------------- |
| `AccountLockRequest`   | 2101     | `AccountLockMessageCodec.encodeRequest(...)`  | `AccountLockMessageCodec.decodeRequest(...)`  |
| `AccountLockResponse`  | 2102     | `AccountLockMessageCodec.encodeResponse(...)` | `AccountLockMessageCodec.decodeResponse(...)` |
| `OrderCommand`         | 2001     | `OrderCodec.encode(...)`                    | `OrderCodec.decode(...)`                    |
| `ExecutionReport`      | 2002     | `ExecutionReportEncoder.encode(...)`          | `-`                                           |
| `TradeEvent`           | 2003     | `TradeCodec.encode(...)`                    | `TradeCodec.decode(...)`               |
| `RawOrderBookSnapshot` | 2004     | `OrderBookCodec.encode(...)`                | `OrderBookCodec.decode(...)`           |


## 5. 高性能轮询策略

项目已统一从 `@Scheduled` 轮询改为专用线程持续 poll，工具类位于：

- `common/src/main/java/com/ganten/peanuts/common/aeron/AeronPollWorker.java`

该 worker 特点：

1. 单独 daemon 线程，持续执行 poll。
2. 使用 `BackoffIdleStrategy` 动态平衡延迟和 CPU。
3. 支持统一异常回调。
4. 支持优雅关闭（interrupt + join）。

## 6. offer 返回值说明

`publication.offer(...)` 返回值语义：

- `> 0`：发送成功。
- `< 0`：发送失败或暂时不可发送（如背压、未连接、关闭等）。

建议：

1. 对关键消息做重试或降级。
2. 记录负返回码分布，观察背压趋势。
3. 不在热路径打印过多 INFO 日志，避免吞吐下降。

## 7. 实践建议

1. 热路径尽量避免阻塞 I/O。
2. 控制消息体大小，降低编码/拷贝开销。
3. 将关键参数配置化：fragment limit、idle strategy 参数、超时。
4. 对 request-response 场景设置合理超时和失败兜底。
5. 增加指标监控：

- polls/s
- fragments/s
- offer success/fail ratio
- p95/p99 latency

## 8. 常见问题排查

1. 现象：大量 back pressure

- 检查消费者是否跟得上、stream 分片限制是否过低、日志是否过重。

1. 现象：请求超时

- 检查响应流是否启动、requestId 是否正确关联、poll 线程是否活跃。

1. 现象：CPU 过高

- 调整 idle strategy 参数，必要时按环境区分低延迟模式与省 CPU 模式。

## 9. 相关代码位置

- `order/.../account/AccountLockAeronClient.java`
- `account/.../messaging/AccountLockAeronProcessor.java`
- `order/.../dispatcher/AeronOrderDispatcher.java`
- `match/.../messaging/AeronOrderSubscriber.java`
- `match/.../messaging/AeronExecutionReportPublisher.java`
- `match/.../messaging/AeronTradePublisher.java`
- `match/.../messaging/AeronOrderBookPublisher.java`
- `account/.../messaging/AccountTradeAeronProcessor.java`
- `market/.../messaging/MarketTradeAeronSubscriber.java`
- `market/.../messaging/MarketOrderBookAeronSubscriber.java`
- `common/.../aeron/AeronPollWorker.java`

## 10. 配置治理约定（运维视角）

为避免线上/灰度环境出现“代码与配置认知不一致”，当前项目约定如下：

1. Aeron 关键参数以 `common/.../constant/Constants.java` 为准：
   - channel（`AERON_CHANNEL`）
   - streamId（`AERON_STREAM_ID_*`）
   - driver 策略（`AERON_LAUNCH_EMBEDDED_DRIVER*`）
   - fragment limit（`AERON_FRAGMENT_LIMIT*`）
2. 各服务的 `*BeanConfiguration` 只做装配，不再写硬编码字面量。
3. `@Bean(name = "...AeronProperties")` 与 `@Qualifier("...AeronProperties")` 必须一一对应。
4. 若需要改 stream/channel，先改常量，再做全链路验证（见下文“变更检查清单”）。

当前 driver 分工：

- `match`：`launchEmbeddedDriver=true`（负责启动 embedded MediaDriver）
- `order` / `account` / `market`：`launchEmbeddedDriver=false`（仅连接现有 driver）

## 11. 启停与健康检查

推荐启动顺序：

1. `match`（先启动 driver 与核心撮合链路）
2. `account`
3. `market`
4. `order`

上线前最小检查：

1. 编译检查：`mvn -q compile`
2. 单测检查：`mvn -q test -DskipITs`
3. 日志检查（每个服务）：
   - publisher ready / subscriber ready 是否出现
   - channel 与 streamId 是否符合本 README 的约定
4. 业务烟测：
   - 下单后应看到：锁请求/响应 -> 订单入撮合 -> 成交 -> 账户结算 -> 行情更新

## 12. Stream 变更检查清单

任一 streamId 变更都需要同时核对以下位置：

1. `Constants` 中对应 `AERON_STREAM_ID_*`
2. 发送端 Bean（publisher 使用的 `...AeronProperties`）
3. 接收端 Bean（subscriber 使用的 `...AeronProperties`）
4. README 的 stream 表
5. 至少一条端到端下单链路验证

建议将每次 stream 变更作为单独 PR，避免和业务逻辑改动混在一起，便于回滚和审计。

