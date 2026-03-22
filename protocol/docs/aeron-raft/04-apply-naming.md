# 4. 几种「apply」辨析

名字里都有 **apply**，但**不是同一个东西**。可记成 **三类**（外加一个容易混的「模式名」）。

## 4.1 `RaftApplyClient#apply`（提案入队）

- **谁调**：`AbstractAeronSubscriber#handleMessage` 里，对 **`payload` 字节**调用。
- **干什么**：内部调 JRaft 的 **`Node#apply(Task)`**，把这条命令**交给 Raft**（leader 上入队）。
- **什么时候算完**：**当前线程马上返回**；只表示「交上去了」，**不是** commit，**也不是**状态机执行完。
- **和论文**：对应「上层把命令交给 Raft」这一步。

## 4.2 `Node#apply`（JRaft API）

- **谁调**：`RaftApplyClient` 里。
- **干什么**：Raft 库接收 **Task**（带 `ByteBuffer`），往 **可复制日志**里走（后面还有复制、commit）。
- **注意**：这是 **API 名**，与下面 **状态机 `onApply`** 不是同一个方法。

## 4.3 `StateMachine#onApply` / `CodecRaftStateMachine#onApply`（日志已提交后应用）

- **谁调**：JRaft **在内部**，当日志条目 **已经 committed** 且轮到本节点应用时。
- **干什么**：按序处理日志里的字节，你们这里 **decode 成 `M`**，再调 **`onRaftLogCommitted`**（即 `RaftMessageApplyHandler#onCommitted`）。
- **语义**：这才是 Raft 论文里 **「应用到状态机」**；与 **`Node#apply` 入队** 差一整段 **异步**。

## 4.4 `RaftApplyMode`（不是第四种 apply）

- **`AFTER_COMMIT` / `ON_AERON_POLL`**：只决定 **`onMessage(M)` 在什么时候被调**（与 commit / 入队的先后关系），**不是**又一个叫 apply 的 API。
- 可理解成：**业务回调时机策略**；与 `Node#apply` / `onApply` 重名容易混，别归成一类。

## 4.5 对照表

| 名称 | 大致含义 | 典型线程 |
|------|----------|----------|
| `RaftApplyClient.apply` | 把 payload **提交给 Raft 入队** | 一般是 **Aeron poll 线程** |
| `Node#apply` | 同上（库 API） | 同上 |
| `CodecRaftStateMachine.onApply` | **已提交**日志条目 **应用到状态机** | **Raft 内部线程** |
| `RaftApplyMode` | **何时调 `onMessage`**，不是第三种 apply | 取决于模式 |

**一句话：**

- **「入队」** → `RaftApplyClient#apply` / `Node#apply`。
- **「日志算数并执行到状态机」** → `CodecRaftStateMachine#onApply` → `onRaftLogCommitted`。
- **`RaftApplyMode`** 只管 **`onMessage` 与上两步的先后**，不叫第四个 apply。

---

**上一篇：** [3. `onMessage` 与模式](./03-onmessage-and-modes.md)  
**下一篇：** [5. 端到端调用链（Mermaid）](./05-end-to-end-flow.md)
