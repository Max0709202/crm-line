package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** A mail reply sent from the サポート窓口 to one inquiry (placeholders already filled). */
@Entity
@Table(name = "SUPPORT_REPLY")
public class SupportReply {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "INQUIRY_ID", nullable = false)
    private Long inquiryId;

    @Column(name = "STAFF_NAME", length = 255)
    private String staffName;

    @Column(name = "SUBJECT", length = 500)
    private String subject;

    @Column(name = "BODY", columnDefinition = "LONGTEXT")
    private String body;

    @Column(name = "STATUS", nullable = false, length = 8)
    private String status;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getInquiryId() { return inquiryId; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
