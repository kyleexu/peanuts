package com.ganten.peanuts.protocol.raft;

import lombok.Data;

/**
 * 通用 Raft 参数。由业务模块通过 @ConfigurationProperties 进行装配。
 */
@Data
public class RaftProperties {

    private boolean enabled = false;
    private String dataPath;
    private String groupId;
    private String serverId;
    private String initConf;
}
