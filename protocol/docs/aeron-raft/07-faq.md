# 7. FAQ

**Q：未开 Raft 与开 Raft 会重复调 `onMessage` 吗？**  
A：不会。`raftEnabled == false` 时只在 `handleMessage` 开头调用一次 `onMessage` 并 `return`；开 Raft 时走 `apply` 与模式分支。

**Q：Raft 日志已经持久化了，为什么还要在状态机里写业务？**  
A：Raft 持久的是 **命令字节流**；是否在状态机里更新内存/发副作用，由产品决定。本框架通过 **`RaftApplyMode`** 决定 **`onMessage`** 在「提交后」还是「apply 返回后」触发。

**Q：`ON_AERON_POLL` 下 `onMessage` 和 `onRaftLogCommitted` 都会跑吗？**  
A：`onRaftLogCommitted` 仍会执行，但其中 **只有** `AFTER_COMMIT` 才会调 `onMessage`；`ON_AERON_POLL` 时 **`onRaftLogCommitted` 不调 `onMessage`**，只完成 `Closure`。

---

**上一篇：** [6. 配置与类索引](./06-config-and-classes.md)  
**下一篇：** [8. Kafka 六组件架构](./08-kafka-architecture.md)
