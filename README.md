# Aeron Integration Guide

## 1. 目的

本文档用于说明 peanuts 项目中 Aeron 的使用方式、消息链路、性能策略和常见问题。

## 2. 当前使用场景

项目里主要有两类 Aeron 交互语义：

1. 请求-响应（Request/Response）
- 示例：order-gateway 请求 account-service 做资金锁定。
- 发送端通过 `Publication.offer(...)` 发请求。
- 再通过响应流读取对应 `requestId` 的结果并等待完成。

2. 单向分发（One-way / Fire-and-forget）
- 示例：order-gateway 向 match-engine 异步分发订单。
- 仅 `offer(...)` 投递，不等待回包。

注意：Aeron 底层发送 API 都是 `offer`，区别在业务协议是否定义了“回响应流”。

## 3. 核心链路

### 3.1 下单主链路

1. order-gateway 收到下单请求。
2. `AccountLockAeronClient` 发锁定请求到 account-service。
3. account-service 消费请求并返回锁定结果。
4. 锁定成功后，order-gateway 通过 `AeronOrderDispatcher` 分发订单到 match-engine。
5. match-engine 订阅订单并撮合。
6. match-engine 发布 execution report 与 trade。

## 4. 关键 Stream 约定

默认配置（可在各模块配置文件覆盖）：

按照业务流程，顺序如下：

- 订单入站（gateway -> match-engine）：`streamId=2001`
- 资金锁定请求（gateway -> account-service）：`lockRequestStreamId=2101`
- 资金锁定响应（account-service -> gateway）：`lockResponseStreamId=2102`
- 执行回报（match-engine -> 下游）：`outboundStreamId=2002`
- 成交明细（match-engine -> 下游）：`tradeStreamId=2003`

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

2. 现象：请求超时
- 检查响应流是否启动、requestId 是否正确关联、poll 线程是否活跃。

3. 现象：CPU 过高
- 调整 idle strategy 参数，必要时按环境区分低延迟模式与省 CPU 模式。

## 9. 相关代码位置

- `order-gateway/.../account/AccountLockAeronClient.java`
- `account-service/.../messaging/AccountLockAeronProcessor.java`
- `order-gateway/.../dispatcher/AeronOrderDispatcher.java`
- `match-engine/.../messaging/AeronOrderSubscriber.java`
- `match-engine/.../messaging/AeronExecutionReportPublisher.java`
- `match-engine/.../messaging/AeronTradePublisher.java`
- `common/.../aeron/AeronPollWorker.java`
