package com.ganten.peanuts.protocol.raft;

import java.nio.ByteBuffer;

import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用提案客户端：仅 leader 接受 apply。
 */
@Slf4j
public class RaftApplyClient {

    @Getter
    private final RaftBootstrap raftBootstrap;

    public RaftApplyClient(RaftBootstrap raftBootstrap) {
        this.raftBootstrap = raftBootstrap;
    }

    /**
     * 将 payload 作为日志条目交给当前 leader 的 Raft 节点；成功仅表示<strong>提案已入队</strong>。
     * 复制与提交、状态机 {@code onApply} 均为异步，请用 Task 的 Closure 或状态机感知完成与错误。
     */
    public RaftApplyResult apply(byte[] payload) {
        if (raftBootstrap.getNode() == null) {
            return RaftApplyResult.reject("raft node not ready");
        }
        PeerId leader = raftBootstrap.getNode().getLeaderId();
        PeerId self = raftBootstrap.getServerPeerId();
        if (leader == null || self == null || !leader.equals(self)) {
            return RaftApplyResult.reject("raft not leader");
        }

        Task task = new Task();
        task.setData(ByteBuffer.wrap(payload));
        // 达成共识之后，会调用 done 里面的方式
        task.setDone(status -> log.info("raft apply callback status={}", status));
        raftBootstrap.getNode().apply(task);
        // 提交到 Raft 队列后即返回；状态机在日志 commit 后异步 apply，与 CodecRaftStateMachine 回调
        return RaftApplyResult.ok();
    }
}
