package com.ganten.peanuts.raftpoc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;

/**
 * 启动单节点 Raft，演示：propose → commit → 状态机内存 → {@link DownstreamSink}。
 * <p>
 * 用法:
 * <pre>
 *   java -cp ... com.ganten.peanuts.raftpoc.DemoRaftMain &lt;dataPath&gt; &lt;groupId&gt; &lt;serverId&gt; &lt;initConf&gt;
 *   例（单节点）: ... /tmp/raft-demo peanuts-raft 127.0.0.1:8801 127.0.0.1:8801
 * </pre>
 * 可选：{@code -Dkafka.bootstrap.servers=127.0.0.1:9092} 、
 * {@code -Dkafka.topic=peanuts.raft.committed}
 */
public final class DemoRaftMain {

    private static final Logger LOG = LoggerFactory.getLogger(DemoRaftMain.class);

    private DemoRaftMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: DemoRaftMain <dataPath> <groupId> <serverId> <initConf>");
            System.err.println("Example: DemoRaftMain /tmp/raft-demo peanuts-raft 127.0.0.1:8801 127.0.0.1:8801");
            System.exit(1);
        }
        String dataPath = args[0];
        String groupId = args[1];
        String serverIdStr = args[2];
        String initConfStr = args[3];

        DownstreamSink downstream = buildSink();
        NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setElectionTimeoutMs(1000);
        nodeOptions.setDisableCli(false);
        nodeOptions.setSnapshotIntervalSecs(30);

        PeerId serverId = new PeerId();
        if (!serverId.parse(serverIdStr)) {
            throw new IllegalArgumentException("bad serverId: " + serverIdStr);
        }
        Configuration initConf = new Configuration();
        if (!initConf.parse(initConfStr)) {
            throw new IllegalArgumentException("bad initConf: " + initConfStr);
        }
        nodeOptions.setInitialConf(initConf);

        DemoRaftServer server = new DemoRaftServer(dataPath, groupId, serverId, nodeOptions, downstream);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            if (downstream instanceof KafkaDownstreamSink) {
                ((KafkaDownstreamSink) downstream).close();
            }
        }));

        Node node = server.getNode();
        waitUntilLeader(node, serverId, 30_000L);

        proposeUtf8(node, serverId, "PUT|hello|raft-demo-1");
        proposeUtf8(node, serverId, "PUT|step|consensus-to-kafka");

        LOG.info("Memory store after demo: {}", server.getFsm().getStoreView());

        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        server.close();
        if (downstream instanceof KafkaDownstreamSink) {
            ((KafkaDownstreamSink) downstream).close();
        }
    }

    private static DownstreamSink buildSink() {
        String kafka = System.getProperty("kafka.bootstrap.servers");
        if (kafka != null && !kafka.trim().isEmpty()) {
            String topic = System.getProperty("kafka.topic", "peanuts.raft.committed");
            LOG.info("Using Kafka downstream bootstrap={} topic={}", kafka, topic);
            return new KafkaDownstreamSink(kafka.trim(), topic);
        }
        LOG.info("No -Dkafka.bootstrap.servers; using logging sink only");
        return new LoggingDownstreamSink();
    }

    private static boolean isSelfLeader(Node node, PeerId self) {
        PeerId leader = node.getLeaderId();
        return leader != null && leader.equals(self);
    }

    private static void waitUntilLeader(Node node, PeerId self, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isSelfLeader(node, self)) {
                LOG.info("This node is leader.");
                return;
            }
            Thread.sleep(200L);
        }
        throw new IllegalStateException("leader not ready in time");
    }

    private static void proposeUtf8(Node node, PeerId self, String line) throws InterruptedException {
        if (!isSelfLeader(node, self)) {
            throw new IllegalStateException("not leader, cannot propose");
        }
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        Task task = new Task();
        task.setData(ByteBuffer.wrap(bytes));
        CountDownLatch latch = new CountDownLatch(1);
        final Status[] result = new Status[1];
        task.setDone(new Closure() {
            @Override
            public void run(Status status) {
                result[0] = status;
                if (!status.isOk()) {
                    LOG.error("task failed: {}", status);
                } else {
                    LOG.info("task committed: {}", line);
                }
                latch.countDown();
            }
        });
        node.apply(task);
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("commit timeout");
        }
        if (result[0] == null || !result[0].isOk()) {
            throw new IllegalStateException("commit failed: " + (result[0] == null ? "null" : result[0]));
        }
    }
}
