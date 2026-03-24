package com.group58.recruit.model;

/**
 * Module / job posting (MO-owned).
 */
public final class ModulePosting {

    private String moduleId;
    private String moduleCode;
    private String moduleName;
    private String description;
    private String workload;
    private String requirements;
    private int vacanciesTotal;
    private int vacanciesFilled;
    private String moUserId;
    private ModuleStatus status;
    private String createdAt;
    private String updatedAt;

    public ModulePosting() {
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWorkload() {
        return workload;
    }

    public void setWorkload(String workload) {
        this.workload = workload;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public int getVacanciesTotal() {
        return vacanciesTotal;
    }

    public void setVacanciesTotal(int vacanciesTotal) {
        this.vacanciesTotal = vacanciesTotal;
    }

    public int getVacanciesFilled() {
        return vacanciesFilled;
    }

    public void setVacanciesFilled(int vacanciesFilled) {
        this.vacanciesFilled = vacanciesFilled;
    }

    public String getMoUserId() {
        return moUserId;
    }

    public void setMoUserId(String moUserId) {
        this.moUserId = moUserId;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public void setStatus(ModuleStatus status) {
        this.status = status;
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
