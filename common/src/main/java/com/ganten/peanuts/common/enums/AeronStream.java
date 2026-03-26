package com.ganten.peanuts.common.enums;

import com.ganten.peanuts.common.entity.AeronProperties;

import io.aeron.CommonContext;
import lombok.Getter;

@Getter
public enum AeronStream {

    LOCK_REQUEST(2101, true, RaftApplyMode.ON_AERON_POLL),
    LOCK_RESPONSE(2102, true, RaftApplyMode.ON_AERON_POLL),
    TRADE(2003, true, RaftApplyMode.ON_AERON_POLL),
    ORDER_BOOK(2004, true, RaftApplyMode.ON_AERON_POLL),
    ORDER(2001, true, RaftApplyMode.ON_AERON_POLL),
    EXECUTION_REPORT(2002, true, RaftApplyMode.ON_AERON_POLL),
    ;

    private final boolean enableRaft;
    private final int streamId;
    private final RaftApplyMode raftApplyMode;

    AeronStream(int streamId, boolean enableRaft, RaftApplyMode raftApplyMode) {
        this.streamId = streamId;
        this.enableRaft = enableRaft;
        this.raftApplyMode = raftApplyMode;
    }

    public AeronProperties toProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(this.streamId);
        aeronProperties.setRaftApplyMode(this.raftApplyMode);
        aeronProperties.setFragmentLimit(50);

        aeronProperties.setRaftDataPath(System.getProperty("java.io.tmpdir"));
        aeronProperties.setRaftGroupId("peanuts-stream-" + this.streamId);
        aeronProperties.setRaftServerId("127.0.0.1:" + (7000 + this.streamId));
        aeronProperties.setRaftInitConf("127.0.0.1:" + (7000 + this.streamId));

        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setLaunchEmbeddedDriver(false);
        aeronProperties.setEnabled(true);
        aeronProperties.setChannel("aeron:ipc");
        return aeronProperties;
    }
}
