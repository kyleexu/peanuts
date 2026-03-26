package com.ganten.peanuts.common.enums;

import com.ganten.peanuts.common.entity.AeronProperties;

import io.aeron.CommonContext;
import lombok.Getter;

@Getter
public enum AeronStream {

    LOCK_REQUEST(2101, 20, true, RaftApplyMode.ON_AERON_POLL),
    LOCK_RESPONSE(2102, 20, true, RaftApplyMode.ON_AERON_POLL),
    TRADE(2003, 100, true, RaftApplyMode.ON_AERON_POLL),
    ORDER_BOOK(2004, 100, true, RaftApplyMode.ON_AERON_POLL),
    ORDER(2001, 50, true, RaftApplyMode.ON_AERON_POLL),
    EXECUTION_REPORT(2002, 50, true, RaftApplyMode.ON_AERON_POLL),
    ;

    private final int fragmentLimit;
    private final boolean enableRaft;
    private final int streamId;
    private final RaftApplyMode raftApplyMode;

    AeronStream(int streamId, int fragmentLimit, boolean enableRaft, RaftApplyMode raftApplyMode) {
        this.streamId = streamId;
        this.fragmentLimit = fragmentLimit;
        this.enableRaft = enableRaft;
        this.raftApplyMode = raftApplyMode;
    }

    public AeronProperties toProperties() {
        return toProperties("default");
    }

    public AeronProperties toProperties(String instanceTag) {
        String normalizedTag = normalizeTag(instanceTag);
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setEnableRaft(enableRaft);
        aeronProperties.setStreamId(this.streamId);
        aeronProperties.setFragmentLimit(this.fragmentLimit);
        if (enableRaft) {
            aeronProperties.setRaftApplyMode(this.raftApplyMode);
            aeronProperties.setRaftDataPath(
                    System.getProperty("java.io.tmpdir") + "/peanuts-raft/stream-" + this.streamId + "-" + normalizedTag);
            aeronProperties.setRaftGroupId("peanuts-stream-" + this.streamId + "-" + normalizedTag);
            int raftPort = 7000 + this.streamId + portOffset(normalizedTag);
            aeronProperties.setRaftServerId("127.0.0.1:" + raftPort);
            aeronProperties.setRaftInitConf("127.0.0.1:" + raftPort);
        } else {
            aeronProperties.setRaftApplyMode(RaftApplyMode.DISABLE);
        }

        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setLaunchEmbeddedDriver(false);
        aeronProperties.setEnabled(true);
        aeronProperties.setChannel("aeron:ipc");
        return aeronProperties;
    }

    private static String normalizeTag(String instanceTag) {
        if (instanceTag == null || instanceTag.trim().isEmpty()) {
            return "default";
        }
        return instanceTag.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    private static int portOffset(String normalizedTag) {
        if ("default".equals(normalizedTag)) {
            return 0;
        }
        return 1000 + Math.abs(normalizedTag.hashCode() % 1000);
    }
}
