package com.ganten.peanuts.common.constant;

import java.util.Arrays;
import java.util.List;

import io.aeron.CommonContext;

public class Constants {

    public static final List<Integer> multiplierList = Arrays.asList(1, 5, 10, 50, 100);

    public static final long ACCOUNT_LOCK_TIMEOUT_MS = 800L; // 800ms

    public static final String AERON_CHANNEL = "aeron:ipc";
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

    public static final String AERON_SUBSCRIBER_RAFT_APPLY_MODE_NAME = "ON_AERON_POLL";

    /**
     * 单节点 Raft 监听端口 = {@value #RAFT_PORT_BASE} + {@code streamId}，保证同 JVM 内不同 stream 不冲突。
     */
    public static final int RAFT_PORT_BASE = 7000;

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

    // --- account 锁请求订阅仅覆盖 apply 模式（与 {@code RaftApplyMode} 枚举名一致）---
    /** 枚举名：ON_AERON_POLL 或 AFTER_COMMIT */
    public static final String ACCOUNT_RAFT_APPLY_MODE_NAME = "ON_AERON_POLL";
}
