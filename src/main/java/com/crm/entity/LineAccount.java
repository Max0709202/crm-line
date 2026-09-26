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
 * A registered LINE Official Account (Messaging API channel). Supports a strict 2-level
 * tree: a null {@code parentAccountId} marks a parent, up to ~100 children point back at
 * one parent via {@code parentAccountId} — enforced in {@link com.crm.service.LineAccountService},
 * not by this entity (a child may not itself have children).
 *
 * <p>{@code channelSecret}/{@code accessToken} are always AES-256 encrypted at rest
 * (see {@link com.crm.util.AesEncryptionUtil}) — never returned in plaintext to the UI,
 * same convention as {@link CarrierAddressPool#getSmtpPassword()}.
 */
@Entity
@Table(name = "LINE_ACCOUNT")
public class LineAccount {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_UNUSED = "UNUSED";
    public static final String STATUS_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PARENT_ACCOUNT_ID")
    private Long parentAccountId;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "OFFICIAL_ACCOUNT_ID")
    private String officialAccountId;

    @Column(name = "CHANNEL_ID", nullable = false, unique = true)
    private String channelId;

    @Column(name = "CHANNEL_SECRET", nullable = false, columnDefinition = "TEXT")
    private String channelSecret;

    @Column(name = "ACCESS_TOKEN", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "WEBHOOK_TOKEN", nullable = false, unique = true)
    private String webhookToken;

    @Column(name = "IS_GROUP_CHAT_MODE")
    private Boolean isGroupChatMode;

    /** Used to pick ONE account when a customer is friended with more than one, for a
     *  「紐づきアカ」dynamic-account send (broadcast or diff-step) — lower value wins. */
    @Column(name = "LINKAGE_PRIORITY", nullable = false)
    private Integer linkagePriority;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "LAST_CONNECTION_CHECK_AT")
    private LocalDateTime lastConnectionCheckAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = STATUS_UNUSED;
        if (isGroupChatMode == null) isGroupChatMode = Boolean.FALSE;
        if (linkagePriority == null) linkagePriority = 100;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(Long parentAccountId) { this.parentAccountId = parentAccountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOfficialAccountId() { return officialAccountId; }
    public void setOfficialAccountId(String officialAccountId) { this.officialAccountId = officialAccountId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getChannelSecret() { return channelSecret; }
    public void setChannelSecret(String channelSecret) { this.channelSecret = channelSecret; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWebhookToken() { return webhookToken; }
    public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }
    public Boolean getIsGroupChatMode() { return isGroupChatMode; }
    public void setIsGroupChatMode(Boolean isGroupChatMode) { this.isGroupChatMode = isGroupChatMode; }
    public Integer getLinkagePriority() { return linkagePriority; }
    public void setLinkagePriority(Integer linkagePriority) { this.linkagePriority = linkagePriority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastConnectionCheckAt() { return lastConnectionCheckAt; }
    public void setLastConnectionCheckAt(LocalDateTime lastConnectionCheckAt) { this.lastConnectionCheckAt = lastConnectionCheckAt; }

    public boolean isParent() { return parentAccountId == null; }
}
