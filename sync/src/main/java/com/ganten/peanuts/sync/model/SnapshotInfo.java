package com.ganten.peanuts.sync.model;

import lombok.Builder;
import lombok.Value;

/**
 * 快照元信息：压缩时不可删除 lastIncludedIndex 之前的日志。
 */
@Value
@Builder
public class SnapshotInfo {

    long lastIncludedTerm;

    long lastIncludedIndex;

    String snapshotLocation;
}
