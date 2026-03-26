package com.ganten.peanuts.common.entity;

import com.ganten.peanuts.common.enums.RaftApplyMode;

import lombok.Data;

/**
 * 通用 Raft 参数。由业务模块通过 @ConfigurationProperties 进行装配。
 */
@Data
public class RaftProperties {
    private String dataPath;
    private String groupId;
    private String serverId;
    private String initConf;
    private RaftApplyMode raftApplyMode;
}
