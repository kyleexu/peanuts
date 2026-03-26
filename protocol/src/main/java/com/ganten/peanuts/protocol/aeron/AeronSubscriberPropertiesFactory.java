package com.ganten.peanuts.protocol.aeron;

import com.ganten.peanuts.common.constant.Constants;

import io.aeron.CommonContext;

/**
 * 各模块 {@link AbstractAeronSubscriber} 使用的 {@link AeronProperties} 均从 {@link Constants} 组装，避免分散硬编码。
 */
public final class AeronSubscriberPropertiesFactory {

    /**
     * 单节点 Raft 监听端口 = {@value #RAFT_PORT_BASE} + {@code streamId}，保证同 JVM 内不同 stream 不冲突。
     */
    public static final int RAFT_PORT_BASE = 7000;
    public static final boolean AERON_ENABLED = true;
    public static final boolean AERON_LAUNCH_EMBEDDED_DRIVER = false;
    public static final boolean AERON_LAUNCH_EMBEDDED_DRIVER_MATCH_ENGINE = true;
    public static final String AERON_DIRECTORY = CommonContext.getAeronDirectoryName();
    public static final int AERON_FRAGMENT_LIMIT = 10;
    public static final int AERON_FRAGMENT_LIMIT_LOCK_RESPONSE = 20;
    public static final int AERON_FRAGMENT_LIMIT_MARKET = 100;


    public static final int AERON_STREAM_ID_LOCK_RESPONSE = 2102;
    public static final int AERON_STREAM_ID_LOCK_REQUEST = 2101;
    public static final int AERON_STREAM_ID_TRADE = 2003;
    public static final int AERON_STREAM_ID_ORDER_BOOK = 2004;
    public static final int AERON_STREAM_ID_ORDER = 2001;
    public static final int AERON_STREAM_ID_EXECUTION_REPORT = 2002;

    /**
     * 各模块 Aeron Subscriber 默认 Raft：与 {@code AeronProperties} 字段默认一致，显式写出便于统一维护。
     */
    public static final boolean AERON_SUBSCRIBER_RAFT_ENABLED = true;



    private static final RaftApplyMode DEFAULT_SUBSCRIBER_RAFT_APPLY_MODE = RaftApplyMode.ON_AERON_POLL;
    private static final RaftApplyMode ACCOUNT_LOCK_REQUEST_RAFT_APPLY_MODE = RaftApplyMode.ON_AERON_POLL;

    private AeronSubscriberPropertiesFactory() {
    }

    /**
     * 标准订阅：transport + {@link Constants#AERON_SUBSCRIBER_RAFT_ENABLED}；开启时按 {@code streamId}
     * 分配独立 dataPath / group / 端口，避免同进程多 Subscriber 冲突。
     */
    public static AeronProperties standardSubscriber(int streamId, int fragmentLimit, boolean launchEmbeddedDriver) {
        AeronProperties p = new AeronProperties();
        p.setEnabled(AERON_ENABLED);
        p.setChannel("aeron:ipc");
        p.setStreamId(streamId);
        p.setLaunchEmbeddedDriver(launchEmbeddedDriver);
        p.setDirectory(AERON_DIRECTORY);
        p.setFragmentLimit(fragmentLimit);
        if (AERON_SUBSCRIBER_RAFT_ENABLED) {
            p.setRaftDataPath(subscriberRaftDataPath(streamId));
            p.setRaftGroupId(subscriberRaftGroupId(streamId));
            p.setRaftServerId(subscriberRaftServerId(streamId));
            p.setRaftInitConf(subscriberRaftInitConf(streamId));
        }
        p.setRaftApplyMode(DEFAULT_SUBSCRIBER_RAFT_APPLY_MODE);
        return p;
    }

    /**
     * account 锁请求订阅：与 {@link #standardSubscriber} 相同 Raft 布局（按 stream 2101），仅覆盖
     * apply mode。
     */
    public static AeronProperties accountLockRequestSubscriber() {
        AeronProperties p = standardSubscriber(
                AERON_STREAM_ID_LOCK_REQUEST,
                AERON_FRAGMENT_LIMIT,
                AERON_LAUNCH_EMBEDDED_DRIVER);
        p.setRaftApplyMode(ACCOUNT_LOCK_REQUEST_RAFT_APPLY_MODE);
        return p;
    }

    public static String subscriberRaftDataPath(int streamId) {
        return System.getProperty("java.io.tmpdir") + "/peanuts-raft/stream-" + streamId;
    }

    public static String subscriberRaftGroupId(int streamId) {
        return "peanuts-stream-" + streamId;
    }

    public static String subscriberRaftServerId(int streamId) {
        return "127.0.0.1:" + (RAFT_PORT_BASE + streamId);
    }

    public static String subscriberRaftInitConf(int streamId) {
        return subscriberRaftServerId(streamId);
    }
}
