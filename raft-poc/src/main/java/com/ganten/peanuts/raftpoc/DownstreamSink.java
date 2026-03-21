package com.ganten.peanuts.raftpoc;

/**
 * 每条日志在状态机 apply 之后、对下游可见前的出口（例如 Kafka）。
 */
public interface DownstreamSink {

    void onCommitted(long logIndex, long logTerm, byte[] command);
}
