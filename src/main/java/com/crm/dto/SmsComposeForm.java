package com.crm.dto;

import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/** SMS has no subject line, unlike {@link MessageComposeForm}. */
public class SmsComposeForm {

    @NotBlank(message = "本文を入力してください")
    private String body;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledAt;

    private Long replyToMessageId;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public Long getReplyToMessageId() { return replyToMessageId; }
    public void setReplyToMessageId(Long replyToMessageId) { this.replyToMessageId = replyToMessageId; }

    /** 画像添付 (メール / SMS: shown on the 返信画面) / 画像挿入 (LINE: sent as images) — HTML画像 ids. */
    private java.util.List<Long> imageIds;

    public java.util.List<Long> getImageIds() { return imageIds; }
    public void setImageIds(java.util.List<Long> imageIds) { this.imageIds = imageIds; }
}
