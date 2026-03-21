package com.ganten.peanuts.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class AccountBeanConfiguration {

    @Bean(name = "lockResponseAeronProperties")
    public AeronProperties lockResponseAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_RESPONSE);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return properties;
    }

    @Bean(name = "lockRequestAeronProperties")
    public AeronProperties lockRequestAeronProperties(
            @Value("${account.raft.enabled:false}") boolean raftEnabled,
            @Value("${account.raft.data-path:${java.io.tmpdir}/peanuts-account-raft}") String raftDataPath,
            @Value("${account.raft.group-id:peanuts-account}") String raftGroupId,
            @Value("${account.raft.server-id:127.0.0.1:8811}") String raftServerId,
            @Value("${account.raft.init-conf:127.0.0.1:8811}") String raftInitConf) {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_REQUEST);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        properties.setRaftEnabled(raftEnabled);
        properties.setRaftDataPath(raftDataPath);
        properties.setRaftGroupId(raftGroupId);
        properties.setRaftServerId(raftServerId);
        properties.setRaftInitConf(raftInitConf);
        return properties;
    }


    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_TRADE);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return properties;
    }
}
