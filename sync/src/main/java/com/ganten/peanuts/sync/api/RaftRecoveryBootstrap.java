package com.ganten.peanuts.sync.api;

import com.ganten.peanuts.sync.model.SnapshotInfo;

/**
 * 节点启动时从快照 + 日志恢复状态机的入口抽象。
 * 具体 Raft 库会提供 API；此处仅定义与本项目集成的边界。
 */
public interface RaftRecoveryBootstrap {

    /**
     * @return 若存在最新快照则返回其元信息，否则返回 null
     */
    SnapshotInfo loadLatestSnapshotMetadata();

    /**
     * 从快照之后的第一条索引开始重放日志（由具体实现对接状态机）。
     *
     * @param startLogIndexInclusive 应从哪条日志开始 apply（通常为 lastIncludedIndex + 1）
     */
    void replayLogsFrom(long startLogIndexInclusive);
}
