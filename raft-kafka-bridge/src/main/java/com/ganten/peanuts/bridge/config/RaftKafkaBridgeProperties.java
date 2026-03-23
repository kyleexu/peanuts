package com.ganten.peanuts.bridge.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raft-kafka-bridge")
public class RaftKafkaBridgeProperties {

    private Kafka kafka = new Kafka();
    private List<StreamConfig> streams = new ArrayList<StreamConfig>();

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public List<StreamConfig> getStreams() {
        return streams;
    }

    public void setStreams(List<StreamConfig> streams) {
        this.streams = streams;
    }

    public static class Kafka {
        private String bootstrapServers;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }
    }

    public static class StreamConfig {
        private int streamId;
        private String topic;
        private Raft raft = new Raft();

        public int getStreamId() {
            return streamId;
        }

        public void setStreamId(int streamId) {
            this.streamId = streamId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public Raft getRaft() {
            return raft;
        }

        public void setRaft(Raft raft) {
            this.raft = raft;
        }
    }

    public static class Raft {
        private String dataPath;
        private String groupId;
        private String serverId;
        private String initConf;

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public String getInitConf() {
            return initConf;
        }

        public void setInitConf(String initConf) {
            this.initConf = initConf;
        }
    }
}

