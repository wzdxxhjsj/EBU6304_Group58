package com.group58.recruit.model;

/**
 * One TA application to a module posting. Named {@code RecruitmentApplication} to avoid confusion with UI "application".
 */
public final class RecruitmentApplication {

    private String applicationId;
    private String taUserId;
    private String moduleId;
    private String appliedRoleName;
    private ApplicationStatus status;
    private boolean allowAdjustment;
    private String cvSnapshotPath;
    private String cvFilePath;
    private String moDecisionBy;
    private String decisionTime;
    private String createdAt;
    private String updatedAt;

    public RecruitmentApplication() {
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTaUserId() {
        return taUserId;
    }

    public void setTaUserId(String taUserId) {
        this.taUserId = taUserId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getAppliedRoleName() {
        return appliedRoleName;
    }

    public void setAppliedRoleName(String appliedRoleName) {
        this.appliedRoleName = appliedRoleName;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public boolean isAllowAdjustment() {
        return allowAdjustment;
    }

    public void setAllowAdjustment(boolean allowAdjustment) {
        this.allowAdjustment = allowAdjustment;
    }

    public String getCvSnapshotPath() {
        return cvSnapshotPath;
    }

    public void setCvSnapshotPath(String cvSnapshotPath) {
        this.cvSnapshotPath = cvSnapshotPath;
    }

    public String getCvFilePath() {
        return cvFilePath;
    }

    public void setCvFilePath(String cvFilePath) {
        this.cvFilePath = cvFilePath;
    }

    public String getMoDecisionBy() {
        return moDecisionBy;
    }

    public void setMoDecisionBy(String moDecisionBy) {
        this.moDecisionBy = moDecisionBy;
    }

    public String getDecisionTime() {
        return decisionTime;
    }

    public void setDecisionTime(String decisionTime) {
        this.decisionTime = decisionTime;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
