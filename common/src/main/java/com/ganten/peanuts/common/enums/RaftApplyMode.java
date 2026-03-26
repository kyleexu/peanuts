package com.ganten.peanuts.common.enums;

/**
 * 启用 Raft 时，子类 {@code onMessage}（内存业务）在何处执行。
 * <p>
 * 注意：{@code RaftBootstrap#apply} 只是把提案交给 Raft，<strong>不会</strong>同步调用本类
 * {@code onRaftLogCommitted}；后者在日志提交后由 Raft 在<strong>另一条线程</strong>上回调。
 */
public enum RaftApplyMode {

    DISABLE,

    /**
     * 在状态机 {@code onRaftLogCommitted} 里调用（日志已提交、即将 apply 时），与 Raft 日志顺序一致，延迟更高。
     */
    AFTER_COMMIT,

    /**
     * 在 Aeron poll 线程里，{@code apply} 返回成功后立即调用（不等提交完成），延迟更低，与强一致语义不对齐。
     */
    ON_AERON_POLL
}
