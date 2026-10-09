package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** お知らせ (専用HTML): the 専用HTML the member last viewed, as a hash of its HTML. */
@Entity
@Table(name = "MEMBER_MEMO_SEEN")
public class MemberMemoSeen {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "SEEN_HASH", nullable = false)
    private String seenHash;

    @Column(name = "SEEN_AT", nullable = false)
    private LocalDateTime seenAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSeenHash() { return seenHash; }
    public void setSeenHash(String seenHash) { this.seenHash = seenHash; }
    public LocalDateTime getSeenAt() { return seenAt; }
    public void setSeenAt(LocalDateTime seenAt) { this.seenAt = seenAt; }
}
