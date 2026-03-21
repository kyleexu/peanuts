package com.ganten.peanuts.sync.api;

import com.ganten.peanuts.sync.model.LogSegmentInfo;
import com.ganten.peanuts.sync.model.SnapshotInfo;

import java.util.List;

/**
 * Raft 日志回收：在快照与状态机一致的前提下，删除不再需要的日志段以释放空间。
 * <p>
 * 典型调用时机：安装快照成功后、或周期性后台任务。
 */
public interface RaftLogRecycler {

    /**
     * 根据当前快照，计算可安全删除的日志段（索引 &lt; lastIncludedIndex 的段可删，具体策略由实现决定）。
     *
     * @param snapshot        最新已持久化快照
     * @param existingSegments 当前已知的日志段列表
     * @return 建议删除的段（实现可返回空表示无需回收）
     */
    List<LogSegmentInfo> segmentsEligibleForCompaction(SnapshotInfo snapshot,
            List<LogSegmentInfo> existingSegments);

    /**
     * 执行物理删除或截断（实现内应保证幂等与并发安全）。
     *
     * @param toRemove 待删除段
     * @return 实际释放的近似字节数
     */
    long reclaim(List<LogSegmentInfo> toRemove);
}
