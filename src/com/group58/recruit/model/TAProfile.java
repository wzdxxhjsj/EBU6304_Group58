package com.group58.recruit.model;

import java.util.List;

/**
 * TA personal profile; one row per TA linked by {@code qmId}.
 */
public final class TAProfile {

    private String profileId;
    private String qmId;
    private String name;
    private String phone;
    private String email;
    private List<String> skills;
    private String cvFilePath;
    /** Whether this TA accepts reassignment to another module (profile-level, not per application). */
    private boolean allowAdjustment;
    private String updatedAt;

    public TAProfile() {
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getQmId() {
        return qmId;
    }

    public void setQmId(String qmId) {
        this.qmId = qmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getCvFilePath() {
        return cvFilePath;
    }

    public void setCvFilePath(String cvFilePath) {
        this.cvFilePath = cvFilePath;
    }

    public boolean isAllowAdjustment() {
        return allowAdjustment;
    }

    public void setAllowAdjustment(boolean allowAdjustment) {
        this.allowAdjustment = allowAdjustment;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
