package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** An {@link HtmlImage} attached to an outbound message or a broadcast (see MessageImageService). */
@Entity
@Table(name = "MESSAGE_IMAGE")
public class MessageImage {

    public static final String OWNER_MESSAGE = "MESSAGE";
    public static final String OWNER_BROADCAST = "BROADCAST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "OWNER_TYPE", nullable = false)
    private String ownerType;

    @Column(name = "OWNER_ID", nullable = false)
    private Long ownerId;

    @Column(name = "IMAGE_ID", nullable = false)
    private Long imageId;

    @Column(name = "SORT_NO", nullable = false)
    private Integer sortNo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (sortNo == null) sortNo = 0;
    }

    public Long getId() { return id; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
