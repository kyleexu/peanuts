package com.ganten.peanuts.protocol.raft;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RaftApplyResult {

    private final boolean accepted;
    private final String reason;

    public static RaftApplyResult ok() {
        return new RaftApplyResult(true, "ok");
    }

    public static RaftApplyResult reject(String reason) {
        return new RaftApplyResult(false, reason);
    }
}
