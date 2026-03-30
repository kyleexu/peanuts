# Maker 模块配置说明

maker 模块包含两个调度器：

- **MakerLadderScheduler** — 做市商，按梯形挂单策略持续在买卖两侧维持挂单
- **TakerFlowScheduler** — 模拟吃单方，定期向盘口发送市价/IOC 订单制造成交流量

---

## 一、MakerLadderScheduler 参数

### 1. 开关与节奏

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `enabled` | `maker.random-order.enabled` | `true` | 总开关。`false` 时 Maker 和 Taker 调度器同时停止。 |
| `fixed-delay-ms` | `maker.random-order.fixed-delay-ms` | `1000` | Maker 每 tick 的间隔（ms）。每 tick 随机选一个合约，尝试补充挂单。 |

---

### 2. 价格档位（核心）

| 参数 | 配置键 | 必填 | 说明 |
|------|--------|------|------|
| `ladderLevels` | `maker.random-order.ladder-levels` | **是** | 梯形挂单的总层数。每 tick 从 1 到 N 层中随机选一层下单。 |
| `ladderStepBps` | `maker.random-order.ladder-step-bps` | **是** | 相邻档位之间的价格间距，单位 bps（1 bps = 0.01%）。 |

**价格计算公式：**

```
BUY  价格 = mid × (1 - ladderStepBps × level / 10000)
SELL 价格 = mid × (1 + ladderStepBps × level / 10000)
```

**示例**（mid = 67000，ladder-step-bps = 3，ladder-levels = 10）：

| 层数 | 偏移 | BUY 价格 | SELL 价格 |
|------|------|----------|-----------|
| 1 | 3 bps | 66979.9 | 67020.1 |
| 2 | 6 bps | 66959.8 | 67040.2 |
| 5 | 15 bps | 66899.5 | 67100.5 |
| 10 | 30 bps | 66799.0 | 67201.0 |

最大买卖价差（第 10 层两侧）= 402 USDT ≈ 60 bps。

> **调优提示：** `ladder-step-bps` 越小，挂单越集中在盘口，价格走势越平滑；越大则模拟流动性越稀疏，价格波动幅度越大。

---

### 3. 挂单数量上限

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `maxActiveOrdersPerContract` | `maker.random-order.max-active-orders-per-contract` | `12` | 每个合约允许同时存活的最大挂单数（买+卖合计）。达到上限后本 tick 不再下新单。 |
| `minOrdersPerSide` | `maker.random-order.min-orders-per-side` | `3` | 每侧（买/卖）最少维持的挂单数。若某一侧不足，会触发 `rebalanceSideSlots` 从对侧撤超额单来腾出空间。 |

> `maxActiveOrdersPerContract` 必须 ≥ 2，`minOrdersPerSide` 被自动限制在 `[1, maxActiveOrdersPerContract/2]` 范围内。

---

### 4. 每单名义金额（Notional）

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `minLadderNotionalUsdt` | `maker.random-order.min-ladder-notional-usdt` | `120` | 单笔挂单名义金额的下限（USDT）。 |
| `maxLadderNotionalUsdt` | `maker.random-order.max-ladder-notional-usdt` | `400` | 单笔挂单名义金额的上限（USDT）。 |

实际名义金额由层数和随机噪声共同决定：**靠近 mid 的层（层数居中）金额更大，远端层金额更小**，再叠加 ±30% 的随机扰动。最终数量 = 名义金额 / 挂单价格。

---

### 5. Ladder 重建触发条件

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `rebalanceDeviationBps` | `maker.random-order.rebalance-deviation-bps` | `25` | 当前市价与上次建仓中间价的偏差超过此阈值（bps）时，撤销全部挂单并重建。 |
| `rebalanceIntervalMs` | `maker.random-order.rebalance-interval-ms` | `15000` | 无论价格是否偏移，每隔此时间（ms）强制重建一次 ladder。 |

满足任一条件即触发：撤掉所有当前 ladder 订单，下一 tick 以新的中间价重新开始建仓。

---

### 6. 库存偏置（Inventory Skew）

系统会根据所有 maker 用户的持仓与目标持仓的偏差，自动向买侧或卖侧倾斜挂单概率，起到被动对冲效果。

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `inventoryTargetBaseQty` | `maker.random-order.inventory-target-base-qty` | `0` | 目标 base 持仓量（如 BTC 数量）。系统会向该目标靠拢。 |
| `inventorySoftLimitBaseQty` | `maker.random-order.inventory-soft-limit-base-qty` | `30` | 持仓偏差的"软上限"，超过此量时偏置达到最大。相当于归一化分母。 |
| `inventoryBiasCap` | `maker.random-order.inventory-bias-cap` | `0.5` | 库存偏置对挂单概率的最大影响幅度，范围 [0.05, 0.95]。例如 0.5 表示最极端情况下买单概率可从 50% 调整到 75% 或 25%。 |

**偏置计算逻辑：**

```
delta      = aggregateBaseQty - inventoryTargetBaseQty
normalized = delta / inventorySoftLimitBaseQty   （限制在 [-1, 1]）
inventoryBias = -normalized × inventoryBiasCap
```

持仓多 → 偏置为负 → 更多卖单；持仓少 → 偏置为正 → 更多买单。
库存偏置与随机游走偏置叠加后共同影响买卖单的概率分配。

---

### 7. 余额门槛

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `minAvailableQuote` | `maker.random-order.min-available-quote` | `1000` | 选择买单用户时，其 USDT 可用余额的最低要求。低于此值的用户不会被选中。 |
| `minAvailableBase` | `maker.random-order.min-available-base` | `0.5` | 选择卖单用户时，其 base 币（如 BTC）可用余额的最低要求。 |
| `balance-warn-interval-ms` | `maker.random-order.balance-warn-interval-ms` | `30000` | 余额接口拉取失败时，降级使用缓存值并打 warn 日志的最小间隔（ms），防止日志刷屏。 |

---

## 二、TakerFlowScheduler 参数

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `takerEnabled` | `maker.random-order.taker-enabled` | `true` | Taker 独立开关（`enabled` 为 `false` 时 Taker 也会停止）。 |
| `taker-fixed-delay-ms` | `maker.random-order.taker-fixed-delay-ms` | `200` | Taker 每 tick 的间隔（ms）。默认比 Maker 频率高 5 倍，持续消耗盘口流动性。 |
| `minTakerNotionalUsdt` | `maker.random-order.min-taker-notional-usdt` | `30` | 单笔吃单名义金额下限（USDT）。 |
| `maxTakerNotionalUsdt` | `maker.random-order.max-taker-notional-usdt` | `180` | 单笔吃单名义金额上限（USDT）。 |
| `takerSweepBps` | `maker.random-order.taker-sweep-bps` | `1.5` | Taker 报单时在 best ask/bid 基础上额外加的价格冗余（bps），确保能扫到盘口。买单在 ask 上加，卖单在 bid 上减。 |
| `takerResultTimeoutMs` | `maker.random-order.taker-result-timeout-ms` | `1500` | Taker 订单等待回执的超时时间（ms）。超时后无论是否成交，均从 pending 列表移除。 |

---

## 三、行情 WebSocket 参数

Maker 通过 WebSocket 订阅外部交易所的实时成交价，写入 `TickerCache`，作为挂单的中间价基准。**在 `TickerCache` 获得第一条价格之前，Maker 不会下任何单。**

| 参数 | 配置键 | 默认值 | 说明 |
|------|--------|--------|------|
| `enabled` | `maker.ticker.websocket.enabled` | `true` | WebSocket 行情服务开关。关闭后 `TickerCache` 永远为空，Maker 也不会下单。 |
| `urls` | `maker.ticker.websocket.urls` | `wss://stream-pro.hashkey.com/quote/ws/v2` | WebSocket 端点，多个地址用逗号分隔，断连后轮询重试。 |
| `symbols` | `maker.ticker.websocket.symbols` | `BTCUSDT,ETHUSDT` | 订阅的交易对，逗号分隔。 |
| `reconnect-delay-ms` | `maker.ticker.websocket.reconnect-delay-ms` | `3000` | 断连后重连等待时间（ms）。 |
| `heartbeat-interval-ms` | `maker.ticker.websocket.heartbeat-interval-ms` | `10000` | ping 心跳间隔（ms）。 |

---

## 四、当前配置值（application.yml）

```yaml
maker:
  ticker:
    websocket:
      enabled: true
      urls: wss://stream-pro.hashkey.com/quote/ws/v2
      symbols: BTCUSDT,ETHUSDT
      reconnect-delay-ms: 3000
      heartbeat-interval-ms: 10000
  random-order:
    enabled: true
    fixed-delay-ms: 1000
    ladder-levels: 10          # 10 层挂单
    ladder-step-bps: 3         # 每层 3 bps，最远档偏离 mid 30 bps
    min-ladder-notional-usdt: 120
    max-ladder-notional-usdt: 500
    max-active-orders-per-contract: 50
    min-orders-per-side: 20
    min-available-quote: 1000
    min-available-base: 0.5
    balance-warn-interval-ms: 30000
```
