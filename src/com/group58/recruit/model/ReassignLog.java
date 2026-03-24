package com.group58.recruit.model;

/**
 * Admin reassign / final reject audit trail (v2); kept in model for forward compatibility.
 */
public final class ReassignLog {

    private String logId;
    private String applicationId;
    private String fromModuleId;
    private String toModuleId;
    private ReassignActionType actionType;
    private String adminUserId;
    private String reason;
    private String createdAt;

    public ReassignLog() {
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getFromModuleId() {
        return fromModuleId;
    }

    public void setFromModuleId(String fromModuleId) {
        this.fromModuleId = fromModuleId;
    }

    public String getToModuleId() {
        return toModuleId;
    }

    public void setToModuleId(String toModuleId) {
        this.toModuleId = toModuleId;
    }

    public ReassignActionType getActionType() {
        return actionType;
    }

    public void setActionType(ReassignActionType actionType) {
        this.actionType = actionType;
    }

    public String getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(String adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
