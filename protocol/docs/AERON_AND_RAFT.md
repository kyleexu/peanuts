# Aeron 订阅与 Raft 集成说明

`protocol` 模块中 **`AbstractAeronSubscriber`**、**`RaftApplyClient`**、**`CodecRaftStateMachine`** 等行为说明已拆成多篇文档，避免单文件过长。

## 文档入口

请从 **`[aeron-raft/README.md](./aeron-raft/README.md)`** 打开索引，按主题阅读：

| 主题 | 链接 |
|------|------|
| 索引（目录） | [aeron-raft/README.md](./aeron-raft/README.md) |
| Aeron / Raft 职责 | [aeron-raft/01-overview.md](./aeron-raft/01-overview.md) |
| Raft 状态与状态机 | [aeron-raft/02-raft-state.md](./aeron-raft/02-raft-state.md) |
| `onMessage`、模式、`isAccepted`、`Closure` | [aeron-raft/03-onmessage-and-modes.md](./aeron-raft/03-onmessage-and-modes.md) |
| 几种「apply」辨析 | [aeron-raft/04-apply-naming.md](./aeron-raft/04-apply-naming.md) |
| 端到端调用链（Mermaid） | [aeron-raft/05-end-to-end-flow.md](./aeron-raft/05-end-to-end-flow.md) |
| 配置与类索引 | [aeron-raft/06-config-and-classes.md](./aeron-raft/06-config-and-classes.md) |
| FAQ | [aeron-raft/07-faq.md](./aeron-raft/07-faq.md) |
| Kafka 六组件架构 | [aeron-raft/08-kafka-architecture.md](./aeron-raft/08-kafka-architecture.md) |

---

文档版本与代码同步：若 API 变更，请同步更新 **`aeron-raft/`** 下对应文件及本入口。
