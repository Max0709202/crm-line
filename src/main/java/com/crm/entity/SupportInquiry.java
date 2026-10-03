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

/** One サポート窓口 inquiry: a mail to the support address, or a WEB form submission. */
@Entity
@Table(name = "SUPPORT_INQUIRY")
public class SupportInquiry {

    public static final String CHANNEL_MAIL = "mail";
    public static final String CHANNEL_WEB = "web";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_DONE = "done";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CHANNEL", nullable = false, length = 8)
    private String channel;

    @Column(name = "NAME", length = 255)
    private String name;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "SUBJECT", length = 500)
    private String subject;

    @Column(name = "BODY", columnDefinition = "LONGTEXT")
    private String body;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "FORM_TYPE", length = 100)
    private String formType;

    @Column(name = "ORDER_NO", length = 100)
    private String orderNo;

    @Column(name = "FORM_PAGE", length = 255)
    private String formPage;

    @Column(name = "STATUS", nullable = false, length = 8)
    private String status = STATUS_OPEN;

    @Column(name = "TO_ADDRESS", length = 255)
    private String toAddress;

    @Column(name = "MESSAGE_ID_HEADER", length = 512)
    private String messageIdHeader;

    @Column(name = "TAG_AMOUNT", length = 255)
    private String tagAmount;

    @Column(name = "TAG_PRODUCT", length = 255)
    private String tagProduct;

    @Column(name = "TAG_FULL_ADDRESS", length = 500)
    private String tagFullAddress;

    @Column(name = "TAG_DATE_JP", length = 100)
    private String tagDateJp;

    @Column(name = "RECEIVED_AT", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (receivedAt == null) receivedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getFormPage() { return formPage; }
    public void setFormPage(String formPage) { this.formPage = formPage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public String getMessageIdHeader() { return messageIdHeader; }
    public void setMessageIdHeader(String messageIdHeader) { this.messageIdHeader = messageIdHeader; }
    public String getTagAmount() { return tagAmount; }
    public void setTagAmount(String tagAmount) { this.tagAmount = tagAmount; }
    public String getTagProduct() { return tagProduct; }
    public void setTagProduct(String tagProduct) { this.tagProduct = tagProduct; }
    public String getTagFullAddress() { return tagFullAddress; }
    public void setTagFullAddress(String tagFullAddress) { this.tagFullAddress = tagFullAddress; }
    public String getTagDateJp() { return tagDateJp; }
    public void setTagDateJp(String tagDateJp) { this.tagDateJp = tagDateJp; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
