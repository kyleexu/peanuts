package com.ganten.peanuts.protocol.aeron;

import io.aeron.CommonContext;
import lombok.Data;
/**
 * Shared Aeron transport properties.
 *
 * Each module can expose its own @ConfigurationProperties(prefix=...) bean
 * by extending this base class.
 */
@Data
public class AeronProperties {

    private boolean enabled = true;
    private String channel = "aeron:ipc";
    private int streamId = 0;

    // match-engine uses embedded driver (kept for compatibility)
    private boolean launchEmbeddedDriver = true;
    private String directory = CommonContext.getAeronDirectoryName();

    // Used by subscribers (match-engine/market), harmless default elsewhere
    private int fragmentLimit = 50;
}
