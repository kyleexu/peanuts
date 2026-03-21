package com.ganten.peanuts.raftpoc;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无 Kafka 时的占位：打印「共识已提交 → 下游可见」的事件。
 */
public final class LoggingDownstreamSink implements DownstreamSink {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingDownstreamSink.class);

    @Override
    public void onCommitted(long logIndex, long logTerm, byte[] command) {
        String payload = command == null ? "" : new String(command, StandardCharsets.UTF_8);
        LOG.info("[downstream] committed index={} term={} payload={}", logIndex, logTerm, payload);
    }
}
