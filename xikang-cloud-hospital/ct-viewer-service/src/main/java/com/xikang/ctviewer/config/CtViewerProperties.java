package com.xikang.ctviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ct-viewer")
public class CtViewerProperties {

    private String workDir = "./data/ct-viewer";
    private long volumeTtlSeconds = 7200;
    private Algo algo = new Algo();

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public long getVolumeTtlSeconds() {
        return volumeTtlSeconds;
    }

    public void setVolumeTtlSeconds(long volumeTtlSeconds) {
        this.volumeTtlSeconds = volumeTtlSeconds;
    }

    public Algo getAlgo() {
        return algo;
    }

    public void setAlgo(Algo algo) {
        this.algo = algo;
    }

    public static class Algo {
        private String baseUrl = "http://127.0.0.1:8106";
        private int connectTimeoutMs = 30_000;
        private int readTimeoutMs = 600_000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "http://127.0.0.1:8106" : baseUrl.trim();
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
