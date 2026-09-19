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
 * An automatic reply rule for one {@link LineAccount}: either fired once when a contact
 * follows the account ({@link #TRIGGER_FOLLOW}), or fired on the first inbound message whose
 * text contains {@link #keyword} ({@link #TRIGGER_KEYWORD}, case-insensitive). Rules are
 * evaluated in {@link #sortOrder} order; the first matching active rule wins.
 */
@Entity
@Table(name = "LINE_AUTO_REPLY_RULE")
public class LineAutoReplyRule {

    public static final String TRIGGER_FOLLOW = "FOLLOW";
    public static final String TRIGGER_KEYWORD = "KEYWORD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LINE_ACCOUNT_ID", nullable = false)
    private Long lineAccountId;

    @Column(name = "TRIGGER_TYPE", nullable = false, length = 16)
    private String triggerType;

    /** Only meaningful when triggerType=KEYWORD. */
    @Column(name = "KEYWORD", length = 255)
    private String keyword;

    @Column(name = "REPLY_BODY", columnDefinition = "LONGTEXT", nullable = false)
    private String replyBody;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (isActive == null) isActive = true;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLineAccountId() { return lineAccountId; }
    public void setLineAccountId(Long lineAccountId) { this.lineAccountId = lineAccountId; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getReplyBody() { return replyBody; }
    public void setReplyBody(String replyBody) { this.replyBody = replyBody; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
