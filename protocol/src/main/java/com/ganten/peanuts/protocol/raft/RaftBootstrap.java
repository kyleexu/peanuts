package com.ganten.peanuts.protocol.raft;

import java.io.File;
import java.io.IOException;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;

import lombok.Getter;

/**
 * 通用 Raft 启动器。
 */
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
     * 启动 Raft（非 Spring 托管时需显式调用，例如由 {@link com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber} 构建客户端时）。
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

    public void shutdown() {
        if (raftGroupService != null) {
            raftGroupService.shutdown();
            raftGroupService = null;
            node = null;
        }
    }
}
