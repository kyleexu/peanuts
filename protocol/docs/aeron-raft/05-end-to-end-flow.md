# 5. 端到端调用链（Mermaid）

类名、方法名与 `AbstractAeronSubscriber`、`RaftApplyClient`、`CodecRaftStateMachine` 源码一致；**线程**分为 **Aeron poll 线程**（`AeronPollWorker` 驱动 `subscription.poll`）与 **Raft 内部线程**（JRaft 复制、commit、`StateMachine#onApply`）。下列图在支持 **Mermaid** 的 Markdown 预览中渲染。

## 5.1 直观类比（读图前）

- **Aeron**：像 **快递**，把一帧字节送到本机，`decode` 成业务对象 `M`。
- **Raft**：像 **登记本**，`encode(M)→payload` 后 `Node#apply` 把 **同一条命令** 记入可复制日志；**入队**快，**commit** 与 **状态机 apply** 往往更晚、在别的线程完成。

两种 `RaftApplyMode` 只决定：**业务回调 `onMessage(M)` 是在「登记还没办完」时跑，还是「登记在共识意义上已办完」之后跑。**

## 5.2 公共前半段（两种模式相同）

**发送端（任意进程 → 与 Subscriber 同一 channel + streamId）：**

```mermaid
flowchart LR
  Pub["Publication.offer<br/>写入一帧"]
  Ch["channel + streamId<br/>与 Subscription 一致"]
  Pub --> Ch
```

**接收端（`AbstractAeronSubscriber` 所在 JVM）：**

```mermaid
flowchart TD
  W["AeronPollWorker 循环<br/>调用 subscription.poll"]
  Po["subscription.poll"]
  FH["FragmentHandler 回调<br/>(buffer, offset, length, header)"]
  Dec["decode(buffer, offset) → 业务对象 M"]
  H["handleMessage(M)"]
  W --> Po --> FH --> Dec --> H
  H -->|"raftEnabled == false"| OM["onMessage(M)；return<br/>纯 Aeron"]
  H -->|"raftEnabled == true"| Rpath["encode(M) → payload[]<br/>进入 RaftApplyClient"]
```

**入队（`RaftApplyClient#apply`，与两种模式相同）：**

```mermaid
flowchart TD
  H2["handleMessage 内：<br/>AeronMessage = codec.encode(M)<br/>payload = 字节数组"]
  A["raftApplyClient.apply(payload)"]
  L{"本机是 Raft Leader？"}
  Rj["返回 reject<br/>isAccepted = false<br/>（未入队）"]
  Ok["Node#apply(Task)<br/>提案入队成功"]
  N["返回 isAccepted = true<br/>仅表示入队；复制与 commit 在后台异步"]
  H2 --> A --> L
  L -->|否| Rj
  L -->|是| Ok --> N
```

**小结：** Aeron 把一帧变成 `M` 再变成 `payload`；`apply` 对调用线程 **同步返回**。

## 5.3 `RaftApplyMode.AFTER_COMMIT`（时序）

说明：`onApply` 与 `onRaftLogCommitted` 在 **Raft 内部线程**执行；`onMessage` 在 **`onRaftLogCommitted`** 里被调用（与 Aeron poll 线程不同）。

```mermaid
sequenceDiagram
  participant AP as Aeron poll 线程
  participant RJ as Raft 内部 JRaft
  participant SM as CodecRaftStateMachine
  participant Sub as AbstractAeronSubscriber

  AP->>AP: subscription.poll → FragmentHandler
  AP->>AP: decode → M
  AP->>AP: handleMessage(M)：encode → payload[]
  AP->>RJ: raftApplyClient.apply(payload)，内部 Node#apply(Task)
  RJ-->>AP: 同步返回 RaftApplyResult（isAccepted）
  Note over AP: AFTER_COMMIT：handleMessage 内<br/>此时不调 onMessage(M)

  RJ->>RJ: 异步：AppendEntries 复制
  RJ->>RJ: commit（多数派）
  RJ->>SM: StateMachine#onApply(Iterator)
  SM->>SM: 从 Iterator 取字节 → codec.decode → M
  SM->>Sub: RaftMessageApplyHandler#onCommitted<br/>即 onRaftLogCommitted(M, localApply, done)
  Sub->>Sub: raftApplyMode == AFTER_COMMIT → onMessage(M)
  Sub->>Sub: localApply 时 done.run(Status.OK)
```

**要点：** `apply` 返回前的步骤在 **Aeron poll 线程**；`onMessage` 在 **commit 之后**、经 **`onRaftLogCommitted`** 触发，**晚于「入队」**。

## 5.4 `RaftApplyMode.ON_AERON_POLL`（时序）

说明：必须先 **`AP->>RJ: apply`**，才有 **`RJ-->>AP: accepted`**；`onMessage` 在 **Aeron poll 线程**、**apply 返回后立刻**调用；Raft 侧 **commit → onApply → onRaftLogCommitted** 仍异步进行，但 **`onRaftLogCommitted` 内不再调 `onMessage`**（仅 `Closure` 等）。

```mermaid
sequenceDiagram
  participant AP as Aeron poll 线程
  participant RJ as Raft 内部 JRaft
  participant SM as CodecRaftStateMachine
  participant Sub as AbstractAeronSubscriber

  AP->>AP: poll → decode → handleMessage：encode → payload[]
  AP->>RJ: raftApplyClient.apply(payload)，内部 Node#apply(Task)
  RJ-->>AP: 同步返回 isAccepted = true
  AP->>AP: handleMessage 内：<br/>立即 onMessage(M)（仍在 Aeron poll 线程）

  Note over RJ,Sub: 以下与上一行并行、在 Raft 线程异步执行，更晚结束
  RJ->>RJ: 复制 → commit → 调度 onApply
  RJ->>SM: StateMachine#onApply(Iterator)
  SM->>Sub: onRaftLogCommitted(M, localApply, done)
  Note over Sub: ON_AERON_POLL：此处不再 onMessage，<br/>仅 Closure / done.run 等
```

**要点：** **`onMessage` 在「入队返回后立刻」**；**commit → onApply → onRaftLogCommitted** 仍会执行，但业务 **`onMessage` 只走上面一步，不从 `onRaftLogCommitted` 再进**。

## 5.5 两种模式对照表

| 步骤 | `AFTER_COMMIT` | `ON_AERON_POLL` |
|------|----------------|-----------------|
| `handleMessage` 里 `apply` 返回之后 | **不**调 `onMessage` | **若 accepted** 则 **调** `onMessage` |
| `onRaftLogCommitted` 里 | **调** `onMessage` | **不**调 `onMessage`，仅 `Closure` 等 |
| `onMessage` 与 commit 的先后（业务语义） | **晚于** commit | **早于** commit（早执行内存路径） |

## 5.6 时间线对比（只盯 `onMessage`）

```mermaid
flowchart TB
  subgraph AC ["模式 AFTER_COMMIT"]
    AC1["Aeron 收到一帧 → decode → M"] --> AC2["apply 入队（不调 onMessage）"]
    AC2 --> AC3["异步：commit → onApply → onRaftLogCommitted"]
    AC3 --> AC4["onMessage(M)：晚，与已提交日志对齐"]
  end
  subgraph OAP ["模式 ON_AERON_POLL"]
    O1["Aeron 收到一帧 → decode → M"] --> O2["apply 入队"]
    O2 --> O3["立刻 onMessage(M)：早，同 Aeron 线程"]
    O2 --> O4["异步：commit / onApply / onRaftLogCommitted（后台）"]
  end
```

---

**上一篇：** [4. 几种「apply」](./04-apply-naming.md)  
**下一篇：** [6. 配置与类索引](./06-config-and-classes.md)
