package com.ganten.peanuts.sync.kafka;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中继到 Kafka 的一条「复制日志」事件（当前由 Aeron 解码后封装；日后可换成 Raft 已提交条目）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaftLogEnvelope {

    private String role;
    private int streamId;
    private String entryType;
    private long emittedAtMs;
    private Map<String, Object> payload;
}
