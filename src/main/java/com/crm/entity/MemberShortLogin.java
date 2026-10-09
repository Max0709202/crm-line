package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 短縮URL of the 自動ログインURL ({@code /m/{token}}) — the member's short token, for LINE (LINE設定 短縮URL). */
@Entity
@Table(name = "MEMBER_SHORT_LOGIN")
public class MemberShortLogin {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "TOKEN", nullable = false, unique = true)
    private String token;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
