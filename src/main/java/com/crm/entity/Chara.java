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

/** キャラ登録 — one operator character (男性 / 女性) with its profile and photo. */
@Entity
@Table(name = "CHARA")
public class Chara {

    public static final String GENDER_MALE = "male";
    public static final String GENDER_FEMALE = "female";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "GENDER", nullable = false)
    private String gender;

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

    /** {@link CharaFolder} id, null = 未分類. */
    @Column(name = "FOLDER_ID")
    private Long folderId;

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
    /** Only for a display-only (never saved) sender — see MemberSiteService#lineSender. */
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
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
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
