package com.ganten.peanuts.sync.impl;

import com.ganten.peanuts.sync.api.RaftLogRecycler;
import com.ganten.peanuts.sync.model.LogSegmentInfo;
import com.ganten.peanuts.sync.model.SnapshotInfo;

import java.util.Collections;
import java.util.List;

/**
 * 未接入真实 Raft 存储时的占位实现：不执行任何回收。
 */
public class NoOpRaftLogRecycler implements RaftLogRecycler {

    @Override
    public List<LogSegmentInfo> segmentsEligibleForCompaction(SnapshotInfo snapshot,
            List<LogSegmentInfo> existingSegments) {
        return Collections.emptyList();
    }

    @Override
    public long reclaim(List<LogSegmentInfo> toRemove) {
        return 0L;
    }
}
