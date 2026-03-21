package com.ganten.peanuts.sync.model;

import lombok.Builder;
import lombok.Value;

/**
 * 描述一段连续 Raft 日志在存储上的元信息（用于回收决策，不含条目正文）。
 */
@Value
@Builder
public class LogSegmentInfo {

    /** 段内首条日志索引（含） */
    long firstIndex;

    /** 段内末条日志索引（含） */
    long lastIndex;

    /** 段文件路径或存储键 */
    String location;

    /** 近似占用字节，用于监控与回收优先级 */
    long approximateBytes;
}
