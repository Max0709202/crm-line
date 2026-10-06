package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** Subject a templated キャラ mail is sent with (メールテンプレート設定 › メール通知); see MailMessageTemplateService. */
@Entity
@Table(name = "MESSAGE_SENT_SUBJECT")
public class MessageSentSubject {

    @Id
    @Column(name = "MESSAGE_ID")
    private Long messageId;

    @Column(name = "SUBJECT", nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
