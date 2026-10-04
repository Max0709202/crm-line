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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 権限設定 — one staff role: name / 担当者名 / color, the user fields it sees masked, the menu
 * items hidden from it, and its own login. The login itself is the {@link AdminUser} row
 * {@link #adminUserId} (created when the role's first password is set), so the rest of the
 * app's session handling is unchanged. See {@link com.crm.service.AdminRoleService}.
 */
@Entity
@Table(name = "ADMIN_ROLE")
public class AdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "HOLDER")
    private String holder;

    @Column(name = "COLOR", nullable = false)
    private String color;

    @Column(name = "LOGIN_ID", nullable = false)
    private String loginId;

    @Column(name = "ADMIN_USER_ID")
    private Long adminUserId;

    @Column(name = "MASK_FIELDS")
    private String maskFields;

    @Column(name = "HIDDEN_MENUS", columnDefinition = "TEXT")
    private String hiddenMenus;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean defaultRole;

    @Column(name = "PASSWORD_UPDATED_AT")
    private LocalDateTime passwordUpdatedAt;

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

    public List<String> getMaskList() { return split(maskFields); }
    public void setMaskList(Collection<String> v) { this.maskFields = String.join(",", v); }
    public List<String> getHiddenList() { return split(hiddenMenus); }
    public void setHiddenList(Collection<String> v) { this.hiddenMenus = String.join(",", v); }

    private static List<String> split(String v) {
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        for (String s : v.split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }
    public String getMaskFields() { return maskFields; }
    public void setMaskFields(String maskFields) { this.maskFields = maskFields; }
    public String getHiddenMenus() { return hiddenMenus; }
    public void setHiddenMenus(String hiddenMenus) { this.hiddenMenus = hiddenMenus; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isDefaultRole() { return defaultRole; }
    public void setDefaultRole(boolean defaultRole) { this.defaultRole = defaultRole; }
    public LocalDateTime getPasswordUpdatedAt() { return passwordUpdatedAt; }
    public void setPasswordUpdatedAt(LocalDateTime passwordUpdatedAt) { this.passwordUpdatedAt = passwordUpdatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
