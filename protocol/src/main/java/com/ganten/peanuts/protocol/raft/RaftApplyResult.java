package com.ganten.peanuts.protocol.raft;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RaftApplyResult {

    /**
     * true：本机作为 leader 已把提案交给 {@code Node#apply}（入队成功），<strong>不</strong>表示多数派已提交、也不表示状态机已执行。
     * false：未调用 apply（例如非 leader、节点未就绪）。
     */
    private final boolean accepted;
    private final String reason;

    public static RaftApplyResult ok() {
        return new RaftApplyResult(true, "ok");
    }

    public static RaftApplyResult reject(String reason) {
        return new RaftApplyResult(false, reason);
    }
}
