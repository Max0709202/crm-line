package com.crm.line.dto;

/**
 * Response shape of LINE's {@code GET /v2/bot/info} — used only to verify a stored
 * Channel Access Token actually authenticates, not persisted beyond the connection check.
 * Field names match LINE's JSON exactly (snake_case) via Jackson's default binding;
 * see {@link com.crm.line.LineApiClient#getBotInfo(String)}.
 */
public class LineBotInfoResponse {

    private String userId;
    private String basicId;
    private String displayName;
    private String pictureUrl;
    private String chatMode;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBasicId() { return basicId; }
    public void setBasicId(String basicId) { this.basicId = basicId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPictureUrl() { return pictureUrl; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }
    public String getChatMode() { return chatMode; }
    public void setChatMode(String chatMode) { this.chatMode = chatMode; }
}
