# 6. 配置参考与相关类索引

## 6.1 配置参考

各模块 Subscriber 的 `AeronProperties` 由 **`com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory`** 从 **`com.ganten.peanuts.common.constant.Constants`** 组装：

- **`standardSubscriber(streamId, fragmentLimit, launchEmbeddedDriver)`**：通用订阅；`AERON_SUBSCRIBER_RAFT_ENABLED` 为 true 时，按 `streamId` 设置独立 `raftDataPath` / `raftGroupId` / `raftServerId`（端口 `7000 + streamId`）/ `raftInitConf`（单节点），避免同 JVM 多 Subscriber 端口与目录冲突。
- **`accountLockRequestSubscriber()`**：与标准订阅相同 Raft 布局（stream `2101`），仅 `raftApplyMode` 使用 **`ACCOUNT_RAFT_APPLY_MODE_NAME`**。

不通过 Spring YAML/properties 注入；仅 **`application.yml` 中 `server.port`** 等与本机制无关项可保留。

| 字段 | 说明 |
|------|------|
| `raftEnabled` | 是否启用 Raft 客户端与 `apply` 路径。 |
| `raftDataPath` / `groupId` / `serverId` / `initConf` | Raft 节点与集群参数，见 `RaftBootstrap`。 |
| `raftApplyMode` | 通用订阅用常量 `AERON_SUBSCRIBER_RAFT_APPLY_MODE_NAME`；account 锁请求用 `ACCOUNT_RAFT_APPLY_MODE_NAME`，见 [3. `onMessage` 与模式](./03-onmessage-and-modes.md) 与 [5. 端到端流](./05-end-to-end-flow.md)。 |

## 6.2 相关类索引

| 类 | 作用 |
|----|------|
| `AbstractAeronSubscriber` | Aeron poll、`handleMessage`、`onMessage`、`onRaftLogCommitted`。 |
| `AeronSubscriberPropertiesFactory` | 从 `Constants` 构造各模块 Subscriber 的 `AeronProperties`。 |
| `AeronProperties` | Aeron + Raft 开关与 `RaftApplyMode`。 |
| `RaftApplyMode` | `AFTER_COMMIT` / `ON_AERON_POLL`。 |
| `RaftApplyClient` | 封装 `Node#apply`，返回 `RaftApplyResult`。 |
| `RaftApplyResult` | `isAccepted()` 表示是否成功入队，非 commit 结果。 |
| `CodecRaftStateMachine` | 将日志字节 decode 为 `M`，再调 `RaftMessageApplyHandler`。 |
| `RaftMessageApplyHandler` | `onCommitted(M, localApply, Closure)`。 |

---

**上一篇：** [5. 端到端流](./05-end-to-end-flow.md)  
**下一篇：** [7. FAQ](./07-faq.md)
