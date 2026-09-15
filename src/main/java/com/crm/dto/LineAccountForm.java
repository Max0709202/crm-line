package com.crm.dto;

import com.crm.entity.LineAccount;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Create/edit form for {@link LineAccount}. {@code channelSecret}/{@code accessToken} are
 * plain text here (the operator's input) — {@code LineAccountService} encrypts them before
 * persisting, and on edit a blank value means "keep the existing encrypted value", same
 * convention as {@code CarrierPoolForm}'s SMTP password field.
 */
public class LineAccountForm {

    private Long parentAccountId;

    @NotBlank(message = "アカウント名を入力してください")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String officialAccountId;

    @NotBlank(message = "Channel IDを入力してください")
    @Size(max = 255)
    private String channelId;

    @Size(max = 2000)
    private String channelSecret;

    @Size(max = 4000)
    private String accessToken;

    public Long getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(Long parentAccountId) { this.parentAccountId = parentAccountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOfficialAccountId() { return officialAccountId; }
    public void setOfficialAccountId(String officialAccountId) { this.officialAccountId = officialAccountId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getChannelSecret() { return channelSecret; }
    public void setChannelSecret(String channelSecret) { this.channelSecret = channelSecret; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public static LineAccountForm from(LineAccount a) {
        LineAccountForm f = new LineAccountForm();
        f.parentAccountId = a.getParentAccountId();
        f.name = a.getName();
        f.officialAccountId = a.getOfficialAccountId();
        f.channelId = a.getChannelId();
        // channelSecret/accessToken deliberately left blank — never round-tripped to the UI.
        return f;
    }
}
