package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 紐づきキャラ (メール): the user has sent at least one message to this {@link Chara}. */
@Entity
@Table(name = "USER_CHARA_LINK")
public class UserCharaLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "CHARA_ID", nullable = false)
    private Long charaId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCharaId() { return charaId; }
    public void setCharaId(Long charaId) { this.charaId = charaId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
