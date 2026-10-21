package com.huazhenghai.readergame.vo;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 玩家日志条目 VO.
 */
public class LogEntry {

    private Long id;
    private String message;
    private String type;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;

    public LogEntry() {
    }

    public LogEntry(Long id, String message, String type, LocalDateTime createdAt) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.createdAt = createdAt;
    }

    public LogEntry(Long id, String message, String type, LocalDateTime createdAt,
                    Map<String, Object> metadata) {
        this(id, message, type, createdAt);
        this.metadata = metadata;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String message;
        private String type;
        private LocalDateTime createdAt;
        private Map<String, Object> metadata;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public LogEntry build() {
            return new LogEntry(id, message, type, createdAt, metadata);
        }
    }
}
