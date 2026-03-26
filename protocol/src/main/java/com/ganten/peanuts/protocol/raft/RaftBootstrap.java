package com.ganten.peanuts.protocol.raft;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用 Raft 启动器。
 */
@Slf4j
public class RaftBootstrap {

    private final RaftProperties properties;
    private final StateMachineAdapter stateMachine;

    private RaftGroupService raftGroupService;
    @Getter
    private Node node;
    @Getter
    private PeerId serverPeerId;

    public RaftBootstrap(RaftProperties properties, StateMachineAdapter stateMachine) {
        this.properties = properties;
        this.stateMachine = stateMachine;
    }

    /**
     * 启动 Raft（非 Spring 托管时需显式调用，例如由
     * {@link com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber} 构建客户端时）。
     */
    public void start() throws IOException {
        File dataDir = new File(properties.getDataPath());
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("failed to create raft data dir: " + properties.getDataPath());
        }
        PeerId serverId = new PeerId();
        if (!serverId.parse(properties.getServerId())) {
            throw new IllegalArgumentException("raft.server-id invalid: " + properties.getServerId());
        }
        this.serverPeerId = serverId;

        Configuration initConf = new Configuration();
        if (!initConf.parse(properties.getInitConf())) {
            throw new IllegalArgumentException("raft.init-conf invalid: " + properties.getInitConf());
        }

        NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setElectionTimeoutMs(1000);
        nodeOptions.setDisableCli(false);
        nodeOptions.setSnapshotIntervalSecs(30);
        nodeOptions.setInitialConf(initConf);
        nodeOptions.setFsm(stateMachine);
        nodeOptions.setLogUri(properties.getDataPath() + File.separator + "log");
        nodeOptions.setRaftMetaUri(properties.getDataPath() + File.separator + "raft_meta");
        nodeOptions.setSnapshotUri(properties.getDataPath() + File.separator + "snapshot");

        RpcServer rpcServer = RaftRpcServerFactory.createRaftRpcServer(serverId.getEndpoint());
        this.raftGroupService = new RaftGroupService(properties.getGroupId(), serverId, nodeOptions, rpcServer);
        this.node = raftGroupService.start();
    }

    /**
     * 将 payload 作为日志条目交给当前 leader 的 Raft 节点；成功仅表示<strong>提案已入队</strong>。
     * 复制与提交、状态机 {@code onApply} 均为异步，请用 Task 的 Closure 或状态机感知完成与错误。
     */
    public ApplyResult apply(byte[] payload) {
        if (this.getNode() == null) {
            return ApplyResult.reject("raft node not ready");
        }
        PeerId leader = this.getNode().getLeaderId();
        PeerId self = this.getServerPeerId();
        if (leader == null || self == null || !leader.equals(self)) {
            return ApplyResult.reject("raft not leader");
        }

        Task task = new Task();
        task.setData(ByteBuffer.wrap(payload));
        // 达成共识之后，会调用 done 里面的方式
        // task.setDone(status -> log.info("raft apply callback status={}", status));
        task.setDone(null);
        this.getNode().apply(task);
        // 提交到 Raft 队列后即返回；状态机在日志 commit 后异步 apply，与 CodecRaftStateMachine 回调
        return ApplyResult.ok();
    }

    public void shutdown() {
        if (raftGroupService != null) {
            raftGroupService.shutdown();
            raftGroupService = null;
            node = null;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ApplyResult {

        /**
         * true：本机作为 leader 已把提案交给
         * {@code Node#apply}（入队成功），<strong>不</strong>表示多数派已提交、也不表示状态机已执行。
         * false：未调用 apply（例如非 leader、节点未就绪）。
         */
        private final boolean accepted;
        private final String reason;

        public static ApplyResult ok() {
            return new ApplyResult(true, "ok");
        }

        public static ApplyResult reject(String reason) {
            return new ApplyResult(false, reason);
        }
    }
}
