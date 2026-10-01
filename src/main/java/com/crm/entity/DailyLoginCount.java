package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 入金レポート ログイン数 for one day, saved at 23:59 by {@link com.crm.service.LoginCountService}. */
@Entity
@Table(name = "DAILY_LOGIN_COUNT")
public class DailyLoginCount {

    @Id
    @Column(name = "LOGIN_DATE")
    private LocalDate loginDate;

    @Column(name = "LOGIN_COUNT", nullable = false)
    private int loginCount;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public LocalDate getLoginDate() { return loginDate; }
    public void setLoginDate(LocalDate loginDate) { this.loginDate = loginDate; }
    public int getLoginCount() { return loginCount; }
    public void setLoginCount(int loginCount) { this.loginCount = loginCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
