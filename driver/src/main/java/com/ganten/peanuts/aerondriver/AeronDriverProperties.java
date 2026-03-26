package com.ganten.peanuts.aerondriver;

import io.aeron.CommonContext;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aeron.driver")
public class AeronDriverProperties {

    /**
     * Must match publisher/subscriber Aeron directory.
     */
    private String directory = CommonContext.getAeronDirectoryName();

    /**
     * Clear stale directory when the standalone driver boots.
     */
    private boolean dirDeleteOnStart = true;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isDirDeleteOnStart() {
        return dirDeleteOnStart;
    }

    public void setDirDeleteOnStart(boolean dirDeleteOnStart) {
        this.dirDeleteOnStart = dirDeleteOnStart;
    }
}
