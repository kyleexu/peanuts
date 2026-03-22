# Aeron 订阅与 Raft 集成说明

本文档说明 `protocol` 模块里 **`AbstractAeronSubscriber`**、**`RaftApplyClient`**、**`CodecRaftStateMachine`** 的分工、调用顺序和配置含义，用于消除「Aeron / Raft / `apply` / `onMessage` / `isAccepted`」等常见疑惑；**第 2 节**给出 Raft 与业务状态机的术语与 **入队 / 提交 / 应用** 关系；**第 8 节**用字符图描述 **Publisher → Subscriber → Raft** 的端到端路径及两种 **`RaftApplyMode`** 的差异。

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

## 2. Raft 状态与状态机变化

本节区分 **Raft 元数据** 与 **业务状态机**，并约定「入队 / 提交 / 应用」与本文其它章节用语一致。

### 2.1 两套状态不要混

| 层次 | 含义 | 谁维护 |
|------|------|--------|
| **Raft 元数据** | 任期、角色（Follower / Candidate / Leader）、日志条目 `(term, index)`、`commitIndex`、各节点复制进度等 | JRaft |
| **业务状态机** | 业务含义下的领域状态（如内存结构）；由 **对「已提交」日志按序 apply** 得到 | `CodecRaftStateMachine` 及子类逻辑 |

**共识保证的是**：所有存活节点对 **可复制日志里每条命令的内容与顺序** 达成一致；**业务状态** = 对 **同一条日志序列** 做 **确定性** `apply` 的结果。

### 2.2 单条日志条目在状态机视角下的阶段

对索引为 `i` 的一条日志：

| 阶段 | 含义 |
|------|------|
| **未出现** | 任何节点日志中尚无索引 `i`（或尚未稳定追加）。 |
| **已追加、未提交** | 条目已写入部分节点日志，但 Raft **尚未**将该索引标为 **committed**。**正式业务状态**不应依赖「仅追加未提交」的条目作为最终事实。 |
| **已提交（committed）** | 满足 Raft 安全规则后，索引 `i` 被标为已提交；从 **集群语义** 上，该条命令的顺序与内容已 **不可被分叉共识推翻**（在算法假设内）。 |
| **已应用（applied）** | 本节点状态机已 **按序** 执行到索引 `i`，即本节点 **`appliedIndex ≥ i`**。 |

**状态机「变化」**：对 **官方、可对外宣称与日志一致** 的业务状态而言，发生在 **已提交之后** 的 **`StateMachine#onApply`（及本框架里随之触发的 `onRaftLogCommitted` / `RaftApplyMode` 分支）**。

### 2.3 业务状态的归纳定义

设业务状态为 \(S\)，第 \(k\) 条 **已提交** 且轮到本节点 apply 的命令为 \(c_k\)（来自日志字节解码）：

- **初始**：\(S_0\)（空状态，或自快照恢复后的基线）。
- **转移**：\(S_{k} = f(S_{k-1}, c_k)\)，其中 \(f\) 在 **所有节点上必须相同且确定性**，才能保证副本间业务状态一致。

### 2.4 与 `isAccepted`、`RaftApplyMode` 的关系

- **`RaftApplyResult#isAccepted`（入队）**：仅表示 leader 侧 **`Node#apply` 已接收任务**，对应上文 **「已追加」路径的早期**，**不是** committed，**也不是** 状态机已 apply。
- **`AFTER_COMMIT` 下的 `onMessage`**：与 **已提交后再进入状态机回调** 的路径对齐，适合要与 **提交顺序** 一致的业务副作用。
- **`ON_AERON_POLL` 下的 `onMessage`**：在 **入队成功后、commit 完成前** 于 Aeron 线程执行，属于 **产品定义的「早执行」**，**不等价**于上表中的 **「已应用（Raft 语义）」**；若需写 DB / Kafka 作为权威副本，应另行评估幂等与顺序，或改用 **`AFTER_COMMIT`**。

### 2.5 节点角色（简述）

```
Follower ──(选举超时)──► Candidate ──(赢得多数选票)──► Leader
    ▲                                                    │
    └──────────(发现更高任期 / 跟随新 Leader)────────────┘
```

客户端 **`apply` 提案** 由 **Leader** 接收并复制；各节点在 **committed** 前缀上 **按序 apply** 到状态机。单节点部署时仍可有 Leader，只是无多副本网络故事。

---

## 3. `onMessage` 会在哪条线程、什么时候被调用？

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

## 4. `apply(payload)` 是同步还是异步？会立刻调用 `onRaftLogCommitted` 吗？

- **`RaftApplyClient#apply`** 对调用线程而言是 **同步返回**的：内部调用 `Node#apply(task)` 后，通常 **立即** 返回 `RaftApplyResult`。
- **`onRaftLogCommitted`**（经 `CodecRaftStateMachine` 调到 **`RaftMessageApplyHandler#onCommitted`**）是在 **日志条目被 commit 并 apply 到状态机时** 由 **Raft 内部线程** 异步触发的，**不是** `apply` 返回前同步执行。
- 因此：**先** `apply` 返回，**后**（过一段时间、在别的线程）才可能 **`onRaftLogCommitted`**。

---

## 5. `RaftApplyResult#isAccepted()` 的语义

**不是**「多数派已提交」，**也不是**「状态机已执行完」。

| `accepted` | 含义 |
|------------|------|
| **true** | 当前节点是 **leader**，且 **`Node#apply` 已成功接收该 Task（提案入队）**。 |
| **false** | **未** 把提案交给 Raft，例如非 leader、节点未就绪等。 |

若要感知 **提交成功 / 失败**，应使用 JRaft **`Task` 上的 `Closure`**（`done.run(Status)`），或在状态机里根据 **apply 结果** 处理；**不能**单靠 `isAccepted()` 表示 commit 完成。

---

## 6. `Closure` 与 `localApply`（状态机里）

- **`Closure`**：随 `Task` 提交；在 **本条日志 apply 到状态机** 时，若本节点是 **发起该 Task 的节点**，`Iterator#done()` 非空，需在应用后调用 **`done.run(Status)`** 通知 JRaft 该提案已处理完毕。
- **`localApply`**（在 `CodecRaftStateMachine` 中）：`iter.done() != null` 表示 **本条 entry 对应本机发起的 propose**，需要完成 `Closure`；跟随者上复制来的 entry 通常 **无** `done`。

---

## 7. `AFTER_COMMIT` 与 `ON_AERON_POLL` 是否弄反了？

**没有弄反。**

- **AFTER_COMMIT**：`onMessage` 在 **提交后的状态机回调**里执行 → **更晚**、更贴日志顺序。
- **ON_AERON_POLL**：`onMessage` 在 **`apply` 成功返回后、Aeron 线程里**执行 → **更早**、不等 commit。

---

## 8. 端到端调用链（字符图）

以下类名、方法名与 `AbstractAeronSubscriber`、`RaftApplyClient`、`CodecRaftStateMachine` 源码一致；**线程**分为 **Aeron poll 线程**（`AeronPollWorker` 驱动 `subscription.poll`）与 **Raft 内部线程**（JRaft 复制、commit、`StateMachine#onApply`）。

### 8.1 直观类比（读图前）

- **Aeron**：像 **快递**，把一帧字节送到本机，`decode` 成业务对象 `M`。
- **Raft**：像 **登记本**，`encode(M)→payload` 后 `Node#apply` 把 **同一条命令** 记入可复制日志；**入队**快，**多数派提交（commit）** 与 **状态机 apply** 往往更晚、在别的线程完成。

两种 `RaftApplyMode` 只决定：**业务回调 `onMessage(M)` 是在「登记还没办完」时跑，还是「登记在共识意义上已办完」之后跑。**

### 8.2 公共前半段（两种模式相同）

**发送端（任意进程）：**

```
  Publication
      │
      │  offer（写入与 channel + streamId 对应的链路）
      ▼
  channel + streamId   ← 与 Subscriber 的 Subscription 一致
```

**接收端（`AbstractAeronSubscriber` 所在 JVM）：**

```
  AeronPollWorker 循环
      │
      ▼
  subscription.poll(fragmentHandler, fragmentLimit)
      │
      │  有 fragment 时
      ▼
  FragmentHandler 回调 (buffer, offset, length, header)
      │
      ▼
  M message = decode(buffer, offset)     // codec.decode
      │
      ▼
  handleMessage(message)
      │
      ├─ raftEnabled == false  →  onMessage(message); return;
      │
      └─ raftEnabled == true  →  继续
```

**进入 Raft 前（两种模式相同）：**

```
  handleMessage(message)
      │
      ▼
  AeronMessage aeronMessage = codec.encode(message)
  byte[] payload = new byte[aeronMessage.getLength()]
  aeronMessage.getBuffer().getBytes(0, payload)
      │
      ▼
  RaftApplyResult result = raftApplyClient.apply(payload)
      │
      ▼
  RaftApplyClient#apply:
      · 若本机不是 leader → reject（isAccepted=false），未入队
      · 若是 leader → Task(payload); node.apply(task); return ok（isAccepted=true）
        此处仅表示「提案入队」，复制 / commit 在之后异步完成
```

到此：**Aeron 负责把一帧变成 `M`，再变成 `payload` 交给 JRaft；`apply` 对调用线程同步返回。**

### 8.3 `RaftApplyMode.AFTER_COMMIT`（按线程拆开）

```
线程:  Aeron poll 线程                              线程: Raft 内部（JRaft）
      │                                                    │
(1)   poll → FragmentHandler                             │
(2)   decode → M                                        │
(3)   handleMessage(M)                                  │
(4)   encode(M) → payload[]                             │
(5)   raftApplyClient.apply(payload)                   │
      │   Node#apply(Task) 入队                          │
      │   return（accepted=true 表示入队成功）            │
      │                                                    │
(6)   handleMessage 内 raftApplyMode == AFTER_COMMIT：   │
      · 不调用 onMessage(M)                             │
      │                                                    │
      └──────────────────────────────────────────────────┼──► 异步：复制日志条目
                                                           │
                                                           ├──► commit（多数派）
                                                           │
                                                           ▼
                                                    CodecRaftStateMachine#onApply(Iterator)
                                                           │
                                                           │  对每条已提交条目：bytes → decode → M
                                                           │
                                                           ▼
                                                    RaftMessageApplyHandler#onCommitted
                                                    (= onRaftLogCommitted)
                                                           │
(7)                                                       ▼
                                                    onRaftLogCommitted:
                                                    raftApplyMode == AFTER_COMMIT
                                                           │
                                                           ▼
                                                    onMessage(M)    ◄── 业务默认在此执行
                                                           │
                                                           ▼
                                                    done.run(Status.OK())（若 localApply）
```

**要点：** (1)～(5) 在 **同一条 Aeron poll 线程**上很快结束；(7) 在 **另一条线程**，须等 **commit + 状态机 onApply**，故 **`onMessage` 晚于「入队」**。

### 8.4 `RaftApplyMode.ON_AERON_POLL`（按线程拆开）

```
线程:  Aeron poll 线程                              线程: Raft 内部（JRaft）
      │                                                   │
(1)   poll → FragmentHandler                            │
(2)   decode → M                                       │
(3)   handleMessage(M)                                  │
(4)   encode(M) → payload[]                             │
(5)   raftApplyClient.apply(payload)                    │
      │   Node#apply(Task) 入队                         │
      │   return accepted=true                          │
      │                                                   │
(6)   handleMessage 内：                                │
      isAccepted && ON_AERON_POLL                       │
      │                                                   │
      └──► onMessage(M)  ◄── 立刻，仍在 Aeron poll 线程   │
                                                           │
      └──────────────────────────────────────────────────┼──► 异步：复制 / commit / onApply
                                                           │    （与 (6) 并行，更晚结束）
                                                           ▼
                                                    CodecRaftStateMachine#onApply(Iterator)
                                                           │
                                                           ▼
                                                    onRaftLogCommitted(M, ...)
                                                           │
      onRaftLogCommitted 内: raftApplyMode == ON_AERON_POLL
                                                           │
                                                           ├── 不调用 onMessage(M)（避免与 (6) 重复）
                                                           │
                                                           └── done.run(Status.OK())（若 localApply）
```

**要点：** (6) 中 **`onMessage` 在「入队刚返回」之后立即执行**；下方 **commit → onApply → onRaftLogCommitted** 仍会执行，但 **`onMessage` 不再从 `onRaftLogCommitted` 进入**。

### 8.5 两种模式对照表

| 步骤 | `AFTER_COMMIT` | `ON_AERON_POLL` |
|------|----------------|-----------------|
| `handleMessage` 里 `apply` 返回之后 | **不**调 `onMessage` | **若 accepted** 则 **调** `onMessage` |
| `onRaftLogCommitted` 里 | **调** `onMessage` | **不**调 `onMessage`，仅 `Closure` 等 |
| `onMessage` 与 commit 的先后（业务语义） | **晚于** commit | **早于** commit（早执行内存路径） |

### 8.6 时间线对比（只盯 `onMessage`）

```
        AFTER_COMMIT                    ON_AERON_POLL
        ────────────                    ─────────────
Aeron 收到 M                            Aeron 收到 M
    ↓                                       ↓
交给 Raft（入队）                        交给 Raft（入队）
    ↓                                       ↓
（此处不调 onMessage）                   （此处立刻 onMessage）← 早
    ↓                                       ↓
… commit …                              … commit …（后台）
    ↓
onMessage  ← 晚，与登记对齐               （onMessage 已在上面跑过）
```

---

## 9. 配置参考

各模块 Subscriber 的 `AeronProperties` 由 **`com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory`** 从 **`com.ganten.peanuts.common.constant.Constants`** 组装：

- **`standardSubscriber(streamId, fragmentLimit, launchEmbeddedDriver)`**：通用订阅；`AERON_SUBSCRIBER_RAFT_ENABLED` 为 true 时，按 `streamId` 设置独立 `raftDataPath` / `raftGroupId` / `raftServerId`（端口 `7000 + streamId`）/ `raftInitConf`（单节点），避免同 JVM 多 Subscriber 端口与目录冲突。
- **`accountLockRequestSubscriber()`**：与标准订阅相同 Raft 布局（stream `2101`），仅 `raftApplyMode` 使用 **`ACCOUNT_RAFT_APPLY_MODE_NAME`**。

不通过 Spring YAML/properties 注入；仅 **`application.yml` 中 `server.port`** 等与本机制无关项可保留。

| 字段 | 说明 |
|------|------|
| `raftEnabled` | 是否启用 Raft 客户端与 `apply` 路径。 |
| `raftDataPath` / `groupId` / `serverId` / `initConf` | Raft 节点与集群参数，见 `RaftBootstrap`。 |
| `raftApplyMode` | 通用订阅用常量 `AERON_SUBSCRIBER_RAFT_APPLY_MODE_NAME`；account 锁请求用 `ACCOUNT_RAFT_APPLY_MODE_NAME`，见第 3 节与第 8 节。 |

---

## 10. 相关类索引

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

## 11. FAQ

**Q：未开 Raft 与开 Raft 会重复调 `onMessage` 吗？**  
A：不会。`raftEnabled == false` 时只在 `handleMessage` 开头调用一次 `onMessage` 并 `return`；开 Raft 时走 `apply` 与模式分支。

**Q：Raft 日志已经持久化了，为什么还要在状态机里写业务？**  
A：Raft 持久的是 **命令字节流**；是否在状态机里更新内存/发副作用，由产品决定。本框架通过 **`RaftApplyMode`** 决定 **`onMessage`** 在「提交后」还是「apply 返回后」触发。

**Q：`ON_AERON_POLL` 下 `onMessage` 和 `onRaftLogCommitted` 都会跑吗？**  
A：`onRaftLogCommitted` 仍会执行，但其中 **只有** `AFTER_COMMIT` 才会调 `onMessage`；`ON_AERON_POLL` 时 **`onRaftLogCommitted` 不调 `onMessage`**，只完成 `Closure`。

---

文档版本与代码同步：若 API 变更，请同时更新本文与上述类的 JavaDoc。
