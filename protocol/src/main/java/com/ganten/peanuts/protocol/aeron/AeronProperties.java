package com.ganten.peanuts.protocol.aeron;

import io.aeron.CommonContext;
import com.ganten.peanuts.protocol.raft.RaftProperties;
import lombok.Data;

/**
 * Shared Aeron transport properties.
 *
 * Each module can expose its own @ConfigurationProperties(prefix=...) bean
 * by extending this base class.
 */
@Data
public class AeronProperties {

    private boolean enabled = true;
    private String channel = "aeron:ipc";
    private int streamId = 0;

    // match uses embedded driver (kept for compatibility)
    private boolean launchEmbeddedDriver = true;
    private String directory = CommonContext.getAeronDirectoryName();

    // Used by subscribers (match/market), harmless default elsewhere
    private int fragmentLimit = 50;

    // Raft properties (used by subscribers)
    private boolean raftEnabled = false;
    private String raftDataPath;
    private String raftGroupId;
    private String raftServerId;
    private String raftInitConf;

    /**
     * 将嵌套在 Aeron 配置中的 Raft 字段转为 {@link RaftProperties}，供 Subscriber 构建 {@code RaftBootstrap}。
     */
    public RaftProperties toRaftProperties() {
        RaftProperties rp = new RaftProperties();
        rp.setEnabled(raftEnabled);
        rp.setDataPath(raftDataPath);
        rp.setGroupId(raftGroupId);
        rp.setServerId(raftServerId);
        rp.setInitConf(raftInitConf);
        return rp;
    }
}
