package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** LINEアカウント編集 › 写真 of one LINE account (キャラ). */
@Entity
@Table(name = "LINE_ACCOUNT_PHOTO")
public class LineAccountPhoto {

    @Id
    @Column(name = "LINE_ACCOUNT_ID")
    private Long lineAccountId;

    /** {@code /img/<HTML_IMAGE.ID>}. */
    @Column(name = "PHOTO_URL", nullable = false)
    private String photoUrl;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getLineAccountId() { return lineAccountId; }
    public void setLineAccountId(Long lineAccountId) { this.lineAccountId = lineAccountId; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
