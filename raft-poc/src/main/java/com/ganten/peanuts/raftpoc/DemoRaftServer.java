package com.ganten.peanuts.raftpoc;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.rpc.RpcServer;

/**
 * 单节点 / 多节点 Raft 进程外壳（不含业务 RPC，仅 JRaft 与 Bolt）。
 */
public final class DemoRaftServer implements AutoCloseable {

    private final RaftGroupService raftGroupService;
    private final Node node;
    private final DemoStateMachine fsm;

    public DemoRaftServer(String dataPath, String groupId, PeerId serverId, NodeOptions nodeOptions,
            DownstreamSink downstream) throws IOException {
        FileUtils.forceMkdir(new File(dataPath));
        RpcServer rpcServer = RaftRpcServerFactory.createRaftRpcServer(serverId.getEndpoint());
        this.fsm = new DemoStateMachine(downstream);
        nodeOptions.setFsm(this.fsm);
        nodeOptions.setLogUri(dataPath + File.separator + "log");
        nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
        nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");
        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions, rpcServer);
        this.node = this.raftGroupService.start();
    }

    public Node getNode() {
        return node;
    }

    public DemoStateMachine getFsm() {
        return fsm;
    }

    @Override
    public void close() {
        if (raftGroupService != null) {
            raftGroupService.shutdown();
        }
    }
}
