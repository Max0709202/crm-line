package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** ユーザー詳細 › プロフィール of one member (no row = nothing set). Same fields as {@link Chara}. */
@Entity
@Table(name = "USER_PROFILE")
public class UserProfile {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "PREF")
    private String pref;

    @Column(name = "BLOOD")
    private String blood;

    @Column(name = "SIGN")
    private String sign;

    @Column(name = "AGE")
    private Integer age;

    @Column(name = "PROFILE")
    private String profile;

    /** {@code /img/<HTML_IMAGE.ID>}, null = 写真なし. */
    @Column(name = "PHOTO_URL")
    private String photoUrl;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPref() { return pref; }
    public void setPref(String pref) { this.pref = pref; }
    public String getBlood() { return blood; }
    public void setBlood(String blood) { this.blood = blood; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
