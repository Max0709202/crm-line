package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * リレーサーバー設定 — the routing side of one {@link RelayServer}: its priority, which users it
 * sends for (フォルダ / 入金回数; nothing set = 全対象) and its last 疎通確認 result.
 */
@Entity
@Table(name = "RELAY_ROUTE")
public class RelayRoute {

    @Id
    @Column(name = "RELAY_ID")
    private Long relayId;

    @Column(name = "PRIORITY", nullable = false)
    private int priority;

    /** JSON array of folder names; null / empty = all folders. */
    @Column(name = "FOLDERS", columnDefinition = "TEXT")
    private String folders;

    @Column(name = "PAY_MIN")
    private Integer payMin;

    @Column(name = "PAY_MAX")
    private Integer payMax;

    @Column(name = "CHECK_STATUS")
    private String checkStatus;

    @Column(name = "CHECK_MESSAGE")
    private String checkMessage;

    @Column(name = "CHECKED_AT")
    private LocalDateTime checkedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getRelayId() { return relayId; }
    public void setRelayId(Long relayId) { this.relayId = relayId; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getFolders() { return folders; }
    public void setFolders(String folders) { this.folders = folders; }
    public Integer getPayMin() { return payMin; }
    public void setPayMin(Integer payMin) { this.payMin = payMin; }
    public Integer getPayMax() { return payMax; }
    public void setPayMax(Integer payMax) { this.payMax = payMax; }
    public String getCheckStatus() { return checkStatus; }
    public void setCheckStatus(String checkStatus) { this.checkStatus = checkStatus; }
    public String getCheckMessage() { return checkMessage; }
    public void setCheckMessage(String checkMessage) { this.checkMessage = checkMessage; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
