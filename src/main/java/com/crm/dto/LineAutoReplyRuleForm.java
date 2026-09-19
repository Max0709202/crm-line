package com.crm.dto;

import javax.validation.constraints.NotBlank;

/** Create form for {@link com.crm.entity.LineAutoReplyRule}. No edit form — wording/keyword
 *  changes are done by deleting and re-adding, keeping this a small first cut. */
public class LineAutoReplyRuleForm {

    @NotBlank(message = "トリガー種別を選択してください")
    private String triggerType;

    private String keyword;

    @NotBlank(message = "返信本文を入力してください")
    private String replyBody;

    private Integer sortOrder;

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getReplyBody() { return replyBody; }
    public void setReplyBody(String replyBody) { this.replyBody = replyBody; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
