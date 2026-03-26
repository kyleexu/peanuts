package com.ganten.peanuts.common.entity;

import com.ganten.peanuts.common.enums.RaftApplyMode;

import lombok.Data;

/**
 * Shared Aeron transport properties.
 *
 * Each module can expose its own @ConfigurationProperties(prefix=...) bean
 * by extending this base class.
 */
@Data
public class AeronProperties {

    private boolean enabled;
    private String channel;
    private int streamId;

    // match uses embedded driver (kept for compatibility)
    private boolean launchEmbeddedDriver;
    private String directory;

    // Used by subscribers (match/market), harmless default elsewhere
    private int fragmentLimit;

    // Raft properties (used by subscribers)
    private String raftDataPath;
    private String raftGroupId;
    private String raftServerId;
    private String raftInitConf;

    /**
     * {@link RaftApplyMode}。
     */
    private RaftApplyMode raftApplyMode;

    /**
     * 将嵌套在 Aeron 配置中的 Raft 字段转为 {@link RaftProperties}，供 Subscriber 构建
     * {@code RaftBootstrap}。
     */
    public RaftProperties toRaftProperties() {
        RaftProperties rp = new RaftProperties();
        rp.setRaftApplyMode(raftApplyMode);
        rp.setDataPath(raftDataPath);
        rp.setGroupId(raftGroupId);
        rp.setServerId(raftServerId);
        rp.setInitConf(raftInitConf);
        return rp;
    }
}
