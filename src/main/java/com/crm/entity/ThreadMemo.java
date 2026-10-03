package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * やり取りメモ on the 受信ボックス thread page (client request 2026-10-02): one memo for the
 * user card and one for the キャラ card, per user × キャラ. STAFF_ID is 0 while キャラ are
 * not defined yet.
 */
@Entity
@Table(name = "THREAD_MEMO")
public class ThreadMemo {

    public static final String TARGET_MEMBER = "member";
    public static final String TARGET_STAFF = "staff";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TARGET", nullable = false, length = 16)
    private String target;

    @Column(name = "STAFF_ID", nullable = false)
    private Long staffId;

    @Column(name = "MEMO", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
