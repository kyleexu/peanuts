package com.ganten.peanuts.protocol.aeron;

import com.ganten.peanuts.common.constant.Constants;

/**
 * 各模块 {@link AbstractAeronSubscriber} 使用的 {@link AeronProperties} 均从 {@link Constants} 组装，避免分散硬编码。
 */
public final class AeronSubscriberPropertiesFactory {

    private AeronSubscriberPropertiesFactory() {
    }

    /**
     * 标准订阅：transport + {@link Constants#AERON_SUBSCRIBER_RAFT_ENABLED}；开启时按 {@code streamId}
     * 分配独立 dataPath / group / 端口，避免同进程多 Subscriber 冲突。
     */
    public static AeronProperties standardSubscriber(int streamId, int fragmentLimit, boolean launchEmbeddedDriver) {
        AeronProperties p = new AeronProperties();
        p.setEnabled(Constants.AERON_ENABLED);
        p.setChannel(Constants.AERON_CHANNEL);
        p.setStreamId(streamId);
        p.setLaunchEmbeddedDriver(launchEmbeddedDriver);
        p.setDirectory(Constants.AERON_DIRECTORY);
        p.setFragmentLimit(fragmentLimit);
        if (Constants.AERON_SUBSCRIBER_RAFT_ENABLED) {
            p.setRaftDataPath(Constants.subscriberRaftDataPath(streamId));
            p.setRaftGroupId(Constants.subscriberRaftGroupId(streamId));
            p.setRaftServerId(Constants.subscriberRaftServerId(streamId));
            p.setRaftInitConf(Constants.subscriberRaftInitConf(streamId));
        }
        p.setRaftApplyMode(RaftApplyMode.valueOf(Constants.AERON_SUBSCRIBER_RAFT_APPLY_MODE_NAME));
        return p;
    }

    /**
     * account 锁请求订阅：与 {@link #standardSubscriber} 相同 Raft 布局（按 stream 2101），仅覆盖
     * {@link Constants#ACCOUNT_RAFT_APPLY_MODE_NAME}。
     */
    public static AeronProperties accountLockRequestSubscriber() {
        AeronProperties p = standardSubscriber(
                Constants.AERON_STREAM_ID_LOCK_REQUEST,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        p.setRaftApplyMode(RaftApplyMode.valueOf(Constants.ACCOUNT_RAFT_APPLY_MODE_NAME));
        return p;
    }
}
