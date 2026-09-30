package com.crm.dto;

public class DomainSettingForm {
    private String replyBaseUrl;
    private Boolean replyRandomSubdomainEnabled;
    private Integer replyRandomSubdomainLength;
    private String replyFixedSubdomain;

    private String fromBaseDomain;
    private Boolean fromRandomLocalEnabled;
    private Integer fromRandomLocalLength;
    private String fromFixedLocal;

    private Boolean bindingExpireEnabled;
    private Integer bindingExpireDays;

    private Boolean replyOgpEnabled;
    private String replyOgpTitle;
    private String replyOgpDescription;
    private String replyOgpImageUrl;

    private String siteName;

    public String getReplyBaseUrl() { return replyBaseUrl; }
    public void setReplyBaseUrl(String replyBaseUrl) { this.replyBaseUrl = replyBaseUrl; }
    public Boolean getReplyRandomSubdomainEnabled() { return replyRandomSubdomainEnabled; }
    public void setReplyRandomSubdomainEnabled(Boolean replyRandomSubdomainEnabled) { this.replyRandomSubdomainEnabled = replyRandomSubdomainEnabled; }
    public Integer getReplyRandomSubdomainLength() { return replyRandomSubdomainLength; }
    public void setReplyRandomSubdomainLength(Integer replyRandomSubdomainLength) { this.replyRandomSubdomainLength = replyRandomSubdomainLength; }
    public String getReplyFixedSubdomain() { return replyFixedSubdomain; }
    public void setReplyFixedSubdomain(String replyFixedSubdomain) { this.replyFixedSubdomain = replyFixedSubdomain; }
    public String getFromBaseDomain() { return fromBaseDomain; }
    public void setFromBaseDomain(String fromBaseDomain) { this.fromBaseDomain = fromBaseDomain; }
    public Boolean getFromRandomLocalEnabled() { return fromRandomLocalEnabled; }
    public void setFromRandomLocalEnabled(Boolean fromRandomLocalEnabled) { this.fromRandomLocalEnabled = fromRandomLocalEnabled; }
    public Integer getFromRandomLocalLength() { return fromRandomLocalLength; }
    public void setFromRandomLocalLength(Integer fromRandomLocalLength) { this.fromRandomLocalLength = fromRandomLocalLength; }
    public String getFromFixedLocal() { return fromFixedLocal; }
    public void setFromFixedLocal(String fromFixedLocal) { this.fromFixedLocal = fromFixedLocal; }
    public Boolean getBindingExpireEnabled() { return bindingExpireEnabled; }
    public void setBindingExpireEnabled(Boolean bindingExpireEnabled) { this.bindingExpireEnabled = bindingExpireEnabled; }
    public Integer getBindingExpireDays() { return bindingExpireDays; }
    public void setBindingExpireDays(Integer bindingExpireDays) { this.bindingExpireDays = bindingExpireDays; }
    public Boolean getReplyOgpEnabled() { return replyOgpEnabled; }
    public void setReplyOgpEnabled(Boolean replyOgpEnabled) { this.replyOgpEnabled = replyOgpEnabled; }
    public String getReplyOgpTitle() { return replyOgpTitle; }
    public void setReplyOgpTitle(String replyOgpTitle) { this.replyOgpTitle = replyOgpTitle; }
    public String getReplyOgpDescription() { return replyOgpDescription; }
    public void setReplyOgpDescription(String replyOgpDescription) { this.replyOgpDescription = replyOgpDescription; }
    public String getReplyOgpImageUrl() { return replyOgpImageUrl; }
    public void setReplyOgpImageUrl(String replyOgpImageUrl) { this.replyOgpImageUrl = replyOgpImageUrl; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
}
