# Raft 流程
1. Node#apply(task) 被调用（leader 本机提案入队）
   1. 如果是 ON_AERON_POLL 模式，那么就是在此时调用 onMessage；
2. Raft 复制到 follower（协议层）
3. 达到多数后 commit（协议层）
4. 各节点状态机触发 onApply，进入 CodecRaftStateMachine#onApply
5. applyHandler.onCommitted(...) 被调用
   1. 在 subscriber 场景里，如果是 AFTER_COMMIT 模式就是 onRaftCommitted，具体逻辑是onMessage；
   2. 在 bridge 场景里是 RaftKafkaMessageApplyHandler#onCommitted