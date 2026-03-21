# recovery

本模块提供 **Raft 日志回收（压缩 / 截断 / 恢复）** 的抽象层，便于后续接入具体 Raft 实现（如 JRaft、Copycat、自研等）而不污染业务服务代码。

## 职责

| 概念 | 说明 |
|------|------|
| **Compaction（压缩）** | 在快照之后丢弃旧日志条目，回收磁盘 |
| **Truncation（截断）** | 跟随 leader 时丢弃冲突前缀日志 |
| **Recovery（恢复）** | 启动时从快照 + 日志重放状态机 |

## 包结构

- `com.ganten.peanuts.raft.api` — 对外接口
- `com.ganten.peanuts.raft.model` — 日志段、快照元数据等轻量模型
- `com.ganten.peanuts.raft.impl` — 默认空实现（占位）

## 使用方式

业务或 `match` / `account` 等模块在引入 `recovery` 后，注入 `RaftLogRecycler` 的实现类即可；未接入前使用 `NoOpRaftLogRecycler`。

## 后续可接

- 与持久化目录布局约定（`log.dir`、`snapshot.dir`）
- 与监控指标（回收耗时、释放字节数、失败次数）
