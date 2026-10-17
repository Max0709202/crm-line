package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 専用HTML slot ⑪〜㉚ of one user (slots ①〜⑩ are CRM_USER.MEMO..MEMO_10). Blank slots have no row. */
@Entity
@Table(name = "CRM_USER_MEMO_SLOT")
public class CrmUserMemoSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "SLOT_NO", nullable = false)
    private Integer slotNo;

    @Column(name = "HTML", nullable = false, columnDefinition = "LONGTEXT")
    private String html;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getSlotNo() { return slotNo; }
    public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
