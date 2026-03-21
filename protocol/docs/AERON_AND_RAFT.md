# Aeron 订阅与 Raft 集成说明

本文档说明 `protocol` 模块里 **`AbstractAeronSubscriber`**、**`RaftApplyClient`**、**`CodecRaftStateMachine`** 的分工、调用顺序和配置含义，用于消除「Aeron / Raft / `apply` / `onMessage` / `isAccepted`」等常见疑惑。

---

## 1. 职责划分：Aeron 做什么、Raft 做什么

| 层次 | 作用 |
|------|------|
| **Aeron** | 进程间传输：订阅方从 `Subscription` poll 到 **一帧消息**，decode 成业务对象 `M`。不负责持久化或跨节点共识。 |
| **Raft** | 在 **本进程内** 提供 **可复制日志**：`Node#apply` 提交提案，日志经多数派 **commit** 后，由 **状态机** `StateMachine#onApply` 顺序应用。持久化与顺序由 Raft 实现保证。 |

二者关系可以概括为：

- **Aeron**：把「命令」送进本机。
- **Raft**（若启用）：把「同一条命令的字节流」变成 **集群承认的日志顺序**；业务是否在状态机里再处理，由 **`RaftApplyMode`** 决定。

---

## 2. `onMessage` 会在哪条线程、什么时候被调用？

子类实现 **`onMessage(M)`** 表示 **「内存路径上的业务处理」**（例如更新缓存、发回包）。根据是否启用 Raft以及 **`AeronProperties#raftApplyMode`**，共有 **三种** 互斥路径（**同一配置下不会对同一条消息执行两次 `onMessage`**）：

### 模式 A：未启用 Raft（纯 Aeron）

- **条件**：`raftEnabled == false`，或 Raft 客户端未创建成功（`raftApplyClient == null`）。
- **行为**：`handleMessage` **末尾**直接调用 **`onMessage`**（Aeron poll 线程）。
- **语义**：经典「收消息 → 处理」，无 Raft。

### 模式 B：启用 Raft + `RaftApplyMode.AFTER_COMMIT`（默认）

- **条件**：`raftEnabled == true` 且 `raftApplyMode == AFTER_COMMIT`。
- **行为**：
  1. Aeron 线程：`encode` → **`RaftApplyClient#apply(payload)`** → 仅表示 **leader 侧提案入队**（见下文 `isAccepted`）。
  2. **不**在 `handleMessage` 里调用 `onMessage`。
  3. 日志 **commit** 之后，Raft 在 **另一条线程** 上进入状态机 → **`onRaftLogCommitted`** → 在此调用 **`onMessage`**。
- **语义**：业务与 **已提交日志** 对齐，延迟通常 **高于** 模式 C，但更贴 Raft 顺序。

### 模式 C：启用 Raft + `RaftApplyMode.ON_AERON_POLL`

- **条件**：`raftEnabled == true` 且 `raftApplyMode == ON_AERON_POLL`。
- **行为**：
  1. Aeron 线程：`apply` 返回且 **`isAccepted() == true`** 后，**立刻**在同一线程调用 **`onMessage`**。
  2. **`onRaftLogCommitted` 中不会再调 `onMessage`**（仅完成 `Closure`）。
- **语义**：**不等** commit 完成就执行业务，延迟更低，但 **与「日志已提交」不对齐**，不适合需要强一致读写的场景。

---

## 3. `apply(payload)` 是同步还是异步？会立刻调用 `onRaftLogCommitted` 吗？

- **`RaftApplyClient#apply`** 对调用线程而言是 **同步返回**的：内部调用 `Node#apply(task)` 后，通常 **立即** 返回 `RaftApplyResult`。
- **`onRaftLogCommitted`**（经 `CodecRaftStateMachine` 调到 **`RaftMessageApplyHandler#onCommitted`**）是在 **日志条目被 commit 并 apply 到状态机时** 由 **Raft 内部线程** 异步触发的，**不是** `apply` 返回前同步执行。
- 因此：**先** `apply` 返回，**后**（过一段时间、在别的线程）才可能 **`onRaftLogCommitted`**。

---

## 4. `RaftApplyResult#isAccepted()` 的语义

**不是**「多数派已提交」，**也不是**「状态机已执行完」。

| `accepted` | 含义 |
|------------|------|
| **true** | 当前节点是 **leader**，且 **`Node#apply` 已成功接收该 Task（提案入队）**。 |
| **false** | **未** 把提案交给 Raft，例如非 leader、节点未就绪等。 |

若要感知 **提交成功 / 失败**，应使用 JRaft **`Task` 上的 `Closure`**（`done.run(Status)`），或在状态机里根据 **apply 结果** 处理；**不能**单靠 `isAccepted()` 表示 commit 完成。

---

## 5. `Closure` 与 `localApply`（状态机里）

- **`Closure`**：随 `Task` 提交；在 **本条日志 apply 到状态机** 时，若本节点是 **发起该 Task 的节点**，`Iterator#done()` 非空，需在应用后调用 **`done.run(Status)`** 通知 JRaft 该提案已处理完毕。
- **`localApply`**（在 `CodecRaftStateMachine` 中）：`iter.done() != null` 表示 **本条 entry 对应本机发起的 propose**，需要完成 `Closure`；跟随者上复制来的 entry 通常 **无** `done`。

---

## 6. `AFTER_COMMIT` 与 `ON_AERON_POLL` 是否弄反了？

**没有弄反。**

- **AFTER_COMMIT**：`onMessage` 在 **提交后的状态机回调**里执行 → **更晚**、更贴日志顺序。
- **ON_AERON_POLL**：`onMessage` 在 **`apply` 成功返回后、Aeron 线程里**执行 → **更早**、不等 commit。

---

## 7. 配置参考（Spring / YAML）

与 `AeronProperties` 相关的 Raft 字段（具体前缀以各模块 `@Bean` 为准，例如 account 的 `lockRequestAeronProperties`）：

| 配置项 | 说明 |
|--------|------|
| `raftEnabled` | 是否启用 Raft 客户端与 `apply` 路径。 |
| `raftDataPath` / `groupId` / `serverId` / `initConf` | Raft 节点与集群参数，见 `RaftBootstrap`。 |
| `raftApplyMode` | `AFTER_COMMIT`（默认）或 `ON_AERON_POLL`，见第 2 节。 |

示例（account 模块常见写法）：

```yaml
account:
  raft:
    enabled: true
    business-apply-mode: AFTER_COMMIT   # 或 ON_AERON_POLL（与 Java 枚举名一致时可用）
```

绑定字段名以 **`AeronProperties#setRaftApplyMode`** 及 `@Value` 的 property 名为准。

---

## 8. 相关类索引

| 类 | 作用 |
|----|------|
| `AbstractAeronSubscriber` | Aeron poll、`handleMessage`、`onMessage`、`onRaftLogCommitted`。 |
| `AeronProperties` | Aeron + Raft 开关与 `RaftApplyMode`。 |
| `RaftApplyMode` | `AFTER_COMMIT` / `ON_AERON_POLL`。 |
| `RaftApplyClient` | 封装 `Node#apply`，返回 `RaftApplyResult`。 |
| `RaftApplyResult` | `isAccepted()` 表示是否成功入队，非 commit 结果。 |
| `CodecRaftStateMachine` | 将日志字节 decode 为 `M`，再调 `RaftMessageApplyHandler`。 |
| `RaftMessageApplyHandler` | `onCommitted(M, localApply, Closure)`。 |

---

## 9. FAQ

**Q：`handleMessage` 里最后一行 `onMessage` 和 Raft 路径会重复吗？**  
A：不会。启用 Raft且 `raftApplyClient != null` 时，前面 `return`，**不会**执行最后的 `onMessage`；该 `onMessage` 仅用于 **未启用 Raft**。

**Q：Raft 日志已经持久化了，为什么还要在状态机里写业务？**  
A：Raft 持久的是 **命令字节流**；是否在状态机里更新内存/发副作用，由产品决定。本框架通过 **`RaftApplyMode`** 决定 **`onMessage`** 在「提交后」还是「apply 返回后」触发。

**Q：`ON_AERON_POLL` 下 `onMessage` 和 `onRaftLogCommitted` 都会跑吗？**  
A：`onRaftLogCommitted` 仍会执行，但其中 **只有** `AFTER_COMMIT` 才会调 `onMessage`；`ON_AERON_POLL` 时 **`onRaftLogCommitted` 不调 `onMessage`**，只完成 `Closure`。

---

文档版本与代码同步：若 API 变更，请同时更新本文与上述类的 JavaDoc。
