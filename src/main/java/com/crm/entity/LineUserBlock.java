package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** The LINE user (one {@link LineUser} row = one user × one キャラ) has blocked the キャラ. */
@Entity
@Table(name = "LINE_USER_BLOCK")
public class LineUserBlock {

    @Id
    @Column(name = "LINE_USER_ROW_ID")
    private Long lineUserRowId;

    @Column(name = "BLOCKED_AT", nullable = false)
    private LocalDateTime blockedAt;

    public Long getLineUserRowId() { return lineUserRowId; }
    public void setLineUserRowId(Long lineUserRowId) { this.lineUserRowId = lineUserRowId; }
    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }
}
