package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** The {@link Chara} a broadcast / 差分ステップ / message is sent as (inbound: sent to). */
@Entity
@Table(name = "CHARA_REF")
public class CharaRef {

    public static final String OWNER_BROADCAST = "BROADCAST";
    public static final String OWNER_DIFF_STEP = "DIFF_STEP";
    public static final String OWNER_DIFF_SCHEDULE_STEP = "DIFF_SCHEDULE_STEP";
    public static final String OWNER_MESSAGE = "MESSAGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "OWNER_TYPE", nullable = false)
    private String ownerType;

    @Column(name = "OWNER_ID", nullable = false)
    private Long ownerId;

    @Column(name = "CHARA_ID", nullable = false)
    private Long charaId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getCharaId() { return charaId; }
    public void setCharaId(Long charaId) { this.charaId = charaId; }
}
