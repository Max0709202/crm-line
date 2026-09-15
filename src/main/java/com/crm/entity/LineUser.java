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
 * Links a {@link CrmUser} to a LINE {@code userId} within one {@link LineAccount}.
 * {@code crmUserId} is nullable — null means "a real person messaged this Official Account
 * but hasn't been matched to a customer yet" (LINE's webhook carries no email/phone to
 * auto-match on, unlike inbound email/SMS). Such rows surface in an admin-facing
 * "unmatched LINE contacts" screen for manual linking — see {@code LineUserLinkService}.
 *
 * <p>{@code lastMessagePreview}/{@code lastMessageAt} exist because {@link Message#getUserId()}
 * is {@code NOT NULL} with a real FK to {@code CRM_USER} — an unlinked contact's message has
 * nowhere else to be shown until it's linked, so it's kept here instead (updated on every
 * inbound event regardless of link state, for a quick glance either way).
 */
@Entity
@Table(name = "LINE_USER")
public class LineUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CRM_USER_ID")
    private Long crmUserId;

    @Column(name = "LINE_USER_ID", nullable = false, length = 64)
    private String lineUserId;

    @Column(name = "LINE_ACCOUNT_ID", nullable = false)
    private Long lineAccountId;

    @Column(name = "LINE_DISPLAY_NAME")
    private String lineDisplayName;

    @Column(name = "LINE_PICTURE_URL")
    private String linePictureUrl;

    @Column(name = "LAST_MESSAGE_PREVIEW", columnDefinition = "TEXT")
    private String lastMessagePreview;

    @Column(name = "LAST_MESSAGE_AT")
    private LocalDateTime lastMessageAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCrmUserId() { return crmUserId; }
    public void setCrmUserId(Long crmUserId) { this.crmUserId = crmUserId; }
    public String getLineUserId() { return lineUserId; }
    public void setLineUserId(String lineUserId) { this.lineUserId = lineUserId; }
    public Long getLineAccountId() { return lineAccountId; }
    public void setLineAccountId(Long lineAccountId) { this.lineAccountId = lineAccountId; }
    public String getLineDisplayName() { return lineDisplayName; }
    public void setLineDisplayName(String lineDisplayName) { this.lineDisplayName = lineDisplayName; }
    public String getLinePictureUrl() { return linePictureUrl; }
    public void setLinePictureUrl(String linePictureUrl) { this.linePictureUrl = linePictureUrl; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isLinked() { return crmUserId != null; }
}
