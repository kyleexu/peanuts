# Aeron Integration Guide

## 1. 构建

```bash
mvn package -DskipTests
```

## 2. 服务启动顺序

> ⚠️ 注意：`order` 和 `market` 都使用 **8080 端口**，不能同时运行。

### 模式一：完整链路（需要 Kafka）

```bash
# 1. match（先启动 embedded MediaDriver 与核心撮合引擎）
java -jar match/target/match-1.0.0-SNAPSHOT.jar

# 2. account（账户服务）
java -jar account/target/account-1.0.0-SNAPSHOT.jar

# 3. bridge（Kafka ↔ Aeron 桥接，需要 Kafka 运行在 localhost:9092）
java -jar bridge/target/bridge-1.0.0-SNAPSHOT.jar

# 4. market 或 order（二选一，都用 8080 端口）
java -jar market/target/market-1.0.0-SNAPSHOT.jar
# 或
java -jar order/target/order-1.0.0-SNAPSHOT.jar
```

### 模式二：仅 Core 服务（无需 Kafka）

```bash
# 1. match（必须先启动）
java -jar match/target/match-1.0.0-SNAPSHOT.jar

# 2. account
java -jar account/target/account-1.0.0-SNAPSHOT.jar

# 3. order（端口 8080）
java -jar order/target/order-1.0.0-SNAPSHOT.jar
```

## 3. 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| `order` | 8080 | HTTP API 接收下单请求 |
| `account` | 8081 | HTTP API 账户查询 |
| `market` | 8082 | WebSocket 推送行情数据 |
| `match` | - | 无 HTTP 端口，纯 Aeron 撮合 |
| `bridge` | - | 无 HTTP 端口，Kafka ↔ Aeron 桥接 |

## 4. Stream ID 与服务对应关系

| Stream ID | 发送方 | 接收方 | 消息类型 | Codec | 说明 |
|-----------|--------|--------|----------|-------|------|
| **2001** | `order` | `match` | `OrderProto` | `OrderCodec` | 订单入站 |
| **2002** | `match` | `order` | `ExecutionReportProto` | `ExecutionReportCodec` | 执行回报 |
| **2003** | `match` | `account`, `market` | `TradeProto` | `TradeCodec` | 成交明细 |
| **2004** | `match` | `market` | `OrderBookProto` | `OrderBookCodec` | 订单簿快照 |
| **2101** | `order` | `account` | `LockRequestProto` | `LockRequestCodec` | 资金锁定请求 |
| **2102** | `account` | `order` | `LockResponseProto` | `LockResponseCodec` | 资金锁定响应 |

> 所有服务均使用 `aeron:ipc` 通道（IPC 模式，无需额外安装 Aeron）

## 5. 基础设施依赖

| 组件 | 必须 | 说明 |
|------|------|------|
| Aeron IPC | ✅ | 进程间通信，IPC 模式无需安装 |
| Kafka | 仅 bridge | `localhost:9092`，用于 bridge 模块 |
| jRaft | 仅 bridge | Kafka Streams 共识复制 |

### 5.1 Raft 配置（仅 bridge 模块）

Raft 用于 bridge 模块中 Kafka Streams 的共识复制。配置位于 `bridge/src/main/resources/application.yml`。

| Stream ID | Kafka Topic | Server ID | Raft 端口 | 数据路径 |
|-----------|-------------|-----------|-----------|----------|
| 2002 | `peanuts-execution-report` | 127.0.0.1 | 7002 | `/tmp/peanuts-raft/bridge/stream-2002` |
| 2003 | `peanuts-trade` | 127.0.0.1 | 7003 | `/tmp/peanuts-raft/bridge/stream-2003` |
| 2004 | `peanuts-order-book` | 127.0.0.1 | 7004 | `/tmp/peanuts-raft/bridge/stream-2004` |
| 2102 | `peanuts-lock-response` | 127.0.0.1 | 7102 | `/tmp/peanuts-raft/bridge/stream-2102` |

> 其他模块（order、account、market、match）无需 Raft 配置，使用纯 Aeron IPC 通信。

### 5.2 Raft 配置项说明

| 配置项 | 说明 |
|--------|------|
| `streamId` | 对应的 Aeron Stream ID |
| `topic` | Kafka Topic 名称 |
| `raft.dataPath` | Raft 数据存储路径 |
| `raft.groupId` | Raft 组 ID |
| `raft.serverId` | 当前节点地址 (`IP:RAFT_PORT`) |
| `raft.initConf` | 初始节点配置（单节点时与 serverId 相同）|

## 6. 上线前最小检查

1. 编译检查：`mvn -q compile`
2. 单测检查：`mvn -q test -DskipITs`
3. 日志检查（每个服务）：
   - publisher ready / subscriber ready 是否出现
   - channel 与 streamId 是否符合本 README 的约定
4. 业务烟测：
   - 下单后应看到：锁请求/响应 -> 订单入撮合 -> 成交 -> 账户结算 -> 行情更新
