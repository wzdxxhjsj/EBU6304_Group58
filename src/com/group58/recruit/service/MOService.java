package com.group58.recruit.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.repository.TAProfileRepository;
import com.group58.recruit.repository.UserRepository;

/**
 * MO-side business logic: viewing own modules, reviewing applicants, accept/reject.
 */
public final class MOService {

    private final ModulePostingRepository moduleRepo = new ModulePostingRepository();
    private final RecruitmentApplicationRepository applicationRepo = new RecruitmentApplicationRepository();
    private final TAProfileRepository profileRepo = new TAProfileRepository();
    private final UserRepository userRepo = new UserRepository();

    // ─────────────────────── Module queries ───────────────────────

    /** All modules owned by this MO, sorted by moduleCode. */
    public List<ModulePosting> getMyModules(String moUserId) {
        List<ModulePosting> result = new ArrayList<>();
        for (ModulePosting m : moduleRepo.findAll()) {
            if (moUserId.equals(m.getMoUserId())) {
                result.add(m);
            }
        }
        result.sort((a, b) -> {
            // OPEN before CLOSED
            int statusCmp = (b.getStatus() == ModuleStatus.OPEN ? 1 : 0) - (a.getStatus() == ModuleStatus.OPEN ? 1 : 0);
            if (statusCmp != 0) return statusCmp;
            return a.getModuleCode().compareToIgnoreCase(b.getModuleCode());
        });
        return result;
    }

    /** One module by id (null if not found). */
    public ModulePosting findModuleById(String moduleId) {
        for (ModulePosting m : moduleRepo.findAll()) {
            if (moduleId.equals(m.getModuleId())) return m;
        }
        return null;
    }

    // ─────────────────────── Applicant queries ────────────────────

    /**
     * All applications for a given module, enriched with TA display info.
     * Sorted: SUBMITTED first, then by createdAt descending.
     */
    public List<ApplicantRow> getApplicantsForModule(String moduleId) {
        Map<String, TAProfile> profileByQmId = new HashMap<>();
        for (TAProfile p : profileRepo.findAll()) {
            profileByQmId.put(p.getQmId(), p);
        }
        Map<String, User> userByQmId = new HashMap<>();
        for (User u : userRepo.findAll()) {
            userByQmId.put(u.getQmId(), u);
        }

        List<ApplicantRow> rows = new ArrayList<>();
        for (RecruitmentApplication app : applicationRepo.findAll()) {
            if (!moduleId.equals(app.getModuleId())) continue;
            String taId = app.getTaUserId();
            TAProfile profile = profileByQmId.get(taId);
            User user = userByQmId.get(taId);

            String taName = profile != null && profile.getName() != null && !profile.getName().isBlank()
                    ? profile.getName()
                    : (user != null ? user.getName() : taId);
            String taEmail = profile != null && profile.getEmail() != null ? profile.getEmail()
                    : (user != null ? user.getEmail() : "");
            String taPhone = profile != null && profile.getPhone() != null ? profile.getPhone() : "";
            List<String> skills = profile != null && profile.getSkills() != null ? profile.getSkills() : new ArrayList<>();
            boolean allowAdj = profile == null || profile.isAllowAdjustment();
            String cvPath = app.getCvFilePath() != null ? app.getCvFilePath()
                    : (profile != null ? profile.getCvFilePath() : null);
            ApplicationStatus status = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;

            rows.add(new ApplicantRow(
                    app.getApplicationId(), taId, taName, taEmail, taPhone,
                    skills, allowAdj, cvPath,
                    app.getAppliedRoleName() != null ? app.getAppliedRoleName() : "",
                    status, app.getCreatedAt()));
        }

        rows.sort((a, b) -> {
            // SUBMITTED first
            boolean aSubmit = a.getStatus() == ApplicationStatus.SUBMITTED;
            boolean bSubmit = b.getStatus() == ApplicationStatus.SUBMITTED;
            if (aSubmit != bSubmit) return aSubmit ? -1 : 1;
            // then newest first
            String ca = a.getCreatedAt() != null ? a.getCreatedAt() : "";
            String cb = b.getCreatedAt() != null ? b.getCreatedAt() : "";
            return cb.compareTo(ca);
        });
        return rows;
    }

    // ─────────────────────── Accept / Reject ──────────────────────

    public MOActionResult acceptApplication(String applicationId, String moUserId) {
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepo.findAll());
        RecruitmentApplication target = findApp(all, applicationId);
        if (target == null) return MOActionResult.failure("Application not found.");
        if (target.getStatus() != ApplicationStatus.SUBMITTED)
            return MOActionResult.failure("Application is not in SUBMITTED state (current: " + target.getStatus() + ").");

        // check module capacity
        ModulePosting module = findModuleById(target.getModuleId());
        if (module == null) return MOActionResult.failure("Module not found.");
        if (module.getStatus() != ModuleStatus.OPEN)
            return MOActionResult.failure("Module is not open.");

        long alreadyAccepted = countAccepted(all, target.getModuleId());
        if (alreadyAccepted >= module.getVacanciesTotal())
            return MOActionResult.failure("Module is already full (" + module.getVacanciesTotal() + "/" + module.getVacanciesTotal() + ").");

        // update application
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        target.setStatus(ApplicationStatus.ACCEPTED);
        target.setMoDecisionBy(moUserId);
        target.setDecisionTime(now);
        target.setUpdatedAt(now);

        // update module vacanciesFilled
        module.setVacanciesFilled((int) alreadyAccepted + 1);
        module.setUpdatedAt(now);
        // if full, auto-close
        if (module.getVacanciesFilled() >= module.getVacanciesTotal()) {
            module.setStatus(ModuleStatus.CLOSED);
        }

        try {
            applicationRepo.saveAll(all);
            saveModule(module);
            return MOActionResult.success("Application accepted.");
        } catch (IOException e) {
            return MOActionResult.failure("Save failed: " + e.getMessage());
        }
    }

    public MOActionResult rejectApplication(String applicationId, String moUserId) {
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepo.findAll());
        RecruitmentApplication target = findApp(all, applicationId);
        if (target == null) return MOActionResult.failure("Application not found.");
        if (target.getStatus() != ApplicationStatus.SUBMITTED)
            return MOActionResult.failure("Application is not in SUBMITTED state (current: " + target.getStatus() + ").");

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        target.setStatus(ApplicationStatus.REJECTED);
        target.setMoDecisionBy(moUserId);
        target.setDecisionTime(now);
        target.setUpdatedAt(now);

        try {
            applicationRepo.saveAll(all);
            return MOActionResult.success("Application rejected.");
        } catch (IOException e) {
            return MOActionResult.failure("Save failed: " + e.getMessage());
        }
    }

    // ─────────────────────── Summary ──────────────────────────────

    /** Count of ACCEPTED apps for a module (from the live list). */
    private long countAccepted(List<RecruitmentApplication> all, String moduleId) {
        return all.stream()
                .filter(a -> moduleId.equals(a.getModuleId()) && a.getStatus() == ApplicationStatus.ACCEPTED)
                .count();
    }

    /** Live count of SUBMITTED apps for a module. */
    public int countPendingForModule(String moduleId) {
        int count = 0;
        for (RecruitmentApplication a : applicationRepo.findAll()) {
            if (moduleId.equals(a.getModuleId()) && a.getStatus() == ApplicationStatus.SUBMITTED) count++;
        }
        return count;
    }

    // ─────────────────────── Helpers ──────────────────────────────

    private RecruitmentApplication findApp(List<RecruitmentApplication> all, String appId) {
        for (RecruitmentApplication a : all) {
            if (appId.equals(a.getApplicationId())) return a;
        }
        return null;
    }

    private void saveModule(ModulePosting updated) throws IOException {
        List<ModulePosting> all = new ArrayList<>(moduleRepo.findAll());
        for (int i = 0; i < all.size(); i++) {
            if (updated.getModuleId().equals(all.get(i).getModuleId())) {
                all.set(i, updated);
                break;
            }
        }
        moduleRepo.saveAll(all);
    }

    // ─────────────────────── Result types ─────────────────────────

    public static final class MOActionResult {
        private final boolean success;
        private final String message;

        private MOActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static MOActionResult success(String msg) { return new MOActionResult(true, msg); }

        public static MOActionResult failure(String msg) { return new MOActionResult(false, msg); }

        public boolean isSuccess() { return success; }

        public String getMessage() { return message; }
    }

    public static final class ApplicantRow {
        private final String applicationId;
        private final String taUserId;
        private final String taName;
        private final String taEmail;
        private final String taPhone;
        private final List<String> skills;
        private final boolean allowAdjustment;
        private final String cvFilePath;
        private final String appliedRoleName;
        private final ApplicationStatus status;
        private final String createdAt;

        public ApplicantRow(String applicationId, String taUserId, String taName,
                            String taEmail, String taPhone, List<String> skills,
                            boolean allowAdjustment, String cvFilePath,
                            String appliedRoleName, ApplicationStatus status, String createdAt) {
            this.applicationId = applicationId;
            this.taUserId = taUserId;
            this.taName = taName;
            this.taEmail = taEmail;
            this.taPhone = taPhone;
            this.skills = skills;
            this.allowAdjustment = allowAdjustment;
            this.cvFilePath = cvFilePath;
            this.appliedRoleName = appliedRoleName;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String getApplicationId() { return applicationId; }

        public String getTaUserId() { return taUserId; }

        public String getTaName() { return taName; }

        public String getTaEmail() { return taEmail; }

        public String getTaPhone() { return taPhone; }

        public List<String> getSkills() { return skills; }

        public boolean isAllowAdjustment() { return allowAdjustment; }

        public String getCvFilePath() { return cvFilePath; }

        public String getAppliedRoleName() { return appliedRoleName; }

        public ApplicationStatus getStatus() { return status; }

        public String getCreatedAt() { return createdAt; }
    }
}
