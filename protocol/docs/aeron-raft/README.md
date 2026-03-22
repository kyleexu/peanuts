# Aeron 与 Raft 文档索引

本目录按主题拆分，便于查阅与维护。从 **[总览（01）](./01-overview.md)** 开始即可。

| 文档 | 内容 |
|------|------|
| [01-overview.md](./01-overview.md) | Aeron / Raft 职责划分 |
| [02-raft-state.md](./02-raft-state.md) | Raft 元数据与业务状态机、入队/提交/应用、节点角色 |
| [03-onmessage-and-modes.md](./03-onmessage-and-modes.md) | `onMessage` 三种路径、`apply` 同步性、`isAccepted`、`Closure`、两种模式是否弄反 |
| [04-apply-naming.md](./04-apply-naming.md) | 几种「apply」名称辨析（入队 vs 状态机 onApply vs `RaftApplyMode`） |
| [05-end-to-end-flow.md](./05-end-to-end-flow.md) | Publisher → Subscriber → Raft 端到端（Mermaid） |
| [06-config-and-classes.md](./06-config-and-classes.md) | `AeronSubscriberPropertiesFactory` / `Constants`、相关类索引 |
| [07-faq.md](./07-faq.md) | 常见问题 |
| [08-kafka-architecture.md](./08-kafka-architecture.md) | 六组件：Publisher → … → Kafka → 下游（Mermaid） |

---

与代码同步：若 `AbstractAeronSubscriber`、`RaftApplyClient`、`CodecRaftStateMachine` 等行为变更，请同步更新对应小节。
