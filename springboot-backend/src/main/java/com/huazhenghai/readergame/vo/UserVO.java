package com.huazhenghai.readergame.vo;

/**
 * 用户视图对象 (脱敏, 不含密码).
 */
public class UserVO {

    private Long id;
    private String username;
    private String email;
    private String role;
    private String createdAt;

    public UserVO() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String username;
        private String email;
        private String role;
        private String createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder createdAt(String createdAt) { this.createdAt = createdAt; return this; }

        public UserVO build() {
            UserVO vo = new UserVO();
            vo.id = id;
            vo.username = username;
            vo.email = email;
            vo.role = role;
            vo.createdAt = createdAt;
            return vo;
        }
    }
}
