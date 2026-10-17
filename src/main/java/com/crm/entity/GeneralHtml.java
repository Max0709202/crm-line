package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 全般HTML: one of the 30 HTML pages shown to anyone at /h/{urlKey} (see GeneralHtmlService). */
@Entity
@Table(name = "GENERAL_HTML")
public class GeneralHtml {

    @Id
    @Column(name = "SLOT_NO")
    private Integer slotNo;

    @Column(name = "TITLE", length = 100)
    private String title;

    @Column(name = "HTML", columnDefinition = "LONGTEXT")
    private String html;

    @Column(name = "URL_KEY", nullable = false, length = 32)
    private String urlKey;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public Integer getSlotNo() { return slotNo; }
    public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }
    public String getUrlKey() { return urlKey; }
    public void setUrlKey(String urlKey) { this.urlKey = urlKey; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
