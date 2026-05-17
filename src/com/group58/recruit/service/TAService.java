package com.group58.recruit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.group58.recruit.config.AppPaths;
import com.group58.recruit.util.DataFileOpen;
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

public final class TAService {
    private static final int MAX_APPLICATIONS = 4;
    private static final int MAX_ACCEPTED_PLACEMENTS = 3;
    private static final int MIN_PHONE_DIGITS = 11;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

    private final ModulePostingRepository moduleRepository = new ModulePostingRepository();
    private final RecruitmentApplicationRepository applicationRepository = new RecruitmentApplicationRepository();
    private final TAProfileRepository profileRepository = new TAProfileRepository();
    private final UserRepository userRepository = new UserRepository();

    public List<AcceptedNotification> collectUnreadAcceptedNotifications(String taUserId) {
        List<AcceptedNotification> notifications = new ArrayList<>();
        if (taUserId == null || taUserId.isBlank()) {
            return notifications;
        }
        Map<String, ModulePosting> moduleById = new HashMap<>();
        for (ModulePosting posting : moduleRepository.findAll()) {
            moduleById.put(posting.getModuleId(), posting);
        }
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepository.findAll());
        boolean changed = false;
        for (RecruitmentApplication app : all) {
            if (!taUserId.equals(app.getTaUserId())) {
                continue;
            }
            if (!countsAsAcceptedForTa(app.getStatus())) {
                continue;
            }
            String shownAt = app.getTaNotificationShownAt();
            if (shownAt != null && !shownAt.isBlank()) {
                continue;
            }
            ModulePosting posting = moduleById.get(app.getModuleId());
            String moduleCode = posting != null && posting.getModuleCode() != null ? posting.getModuleCode() : "";
            String moduleName = posting != null && posting.getModuleName() != null ? posting.getModuleName() : "";
            notifications.add(new AcceptedNotification(app.getApplicationId(), app.getModuleId(), moduleCode, moduleName));
            app.setTaNotificationShownAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            app.setUpdatedAt(app.getTaNotificationShownAt());
            changed = true;
        }
        if (changed) {
            try {
                applicationRepository.saveAll(all);
            } catch (IOException ignored) {
            }
        }
        return notifications;
    }

    public void reconcileAutoRejectWhenTaAcceptanceCapReached() {
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepository.findAll());
        Map<String, Integer> acceptedByTa = new HashMap<>();
        for (RecruitmentApplication app : all) {
            if (app.getTaUserId() == null || app.getTaUserId().isBlank()) continue;
            if (countsAsAcceptedForTa(app.getStatus())) acceptedByTa.merge(app.getTaUserId(), 1, Integer::sum);
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        boolean changed = false;
        for (RecruitmentApplication app : all) {
            if (app.getTaUserId() == null || app.getTaUserId().isBlank()) continue;
            int n = acceptedByTa.getOrDefault(app.getTaUserId(), 0);
            if (n < MAX_ACCEPTED_PLACEMENTS) continue;
            ApplicationStatus st = app.getStatus();
            if (st != ApplicationStatus.SUBMITTED && st != ApplicationStatus.WAITING_FOR_ASSIGNMENT) continue;
            app.setStatus(ApplicationStatus.REJECTED);
            app.setMoDecisionBy("SYSTEM");
            app.setDecisionTime(now);
            app.setUpdatedAt(now);
            changed = true;
        }
        if (changed) {
            try { applicationRepository.saveAll(all); } catch (IOException ignored) {}
        }
    }

    public List<String> getWorkloadOptions() {
        List<ModulePosting> postings = moduleRepository.findAll();
        Set<String> options = new LinkedHashSet<>();
        options.add("All workload");
        postings.sort(Comparator.comparingInt(this::workloadSortKey).thenComparing(ModulePosting::getWorkload));
        for (ModulePosting posting : postings) {
            if (posting.getWorkload() != null && !posting.getWorkload().isBlank()) options.add(posting.getWorkload());
        }
        return new ArrayList<>(options);
    }

    public DashboardData getDashboardData(String taUserId, String keyword, String workloadFilter) {
        reconcileAutoRejectWhenTaAcceptanceCapReached();
        List<RecruitmentApplication> applications = applicationRepository.findAll();
        int appliedCount = 0;
        int acceptedCount = 0;
        for (RecruitmentApplication app : applications) {
            if (!taUserId.equals(app.getTaUserId())) continue;
            appliedCount++;
            if (countsAsAcceptedForTa(app.getStatus())) acceptedCount++;
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<ModulePosting> postings = new ArrayList<>(moduleRepository.findAll());
        postings.sort(Comparator.comparing(ModulePosting::getModuleCode));
        List<ModulePosting> filteredPostings = new ArrayList<>();
        for (ModulePosting posting : postings) {
            if (matchesFilter(posting, normalizedKeyword, workloadFilter)) filteredPostings.add(posting);
        }
        return new DashboardData(appliedCount, acceptedCount, filteredPostings);
    }

    /**
     * Returns an error message when the TA cannot apply yet (profile, CV, module, caps); {@code null} if eligible.
     */
    public String applyEligibilityError(String taUserId, ModulePosting posting) {
        if (taUserId == null || taUserId.isBlank()) {
            return "No TA user selected.";
        }
        if (posting == null) {
            return "Module not found.";
        }
        String profileError = applicationPrerequisiteError(taUserId);
        if (profileError != null) {
            return profileError;
        }
        if (posting.getStatus() != ModuleStatus.OPEN || posting.getVacanciesFilled() >= posting.getVacanciesTotal()) {
            return "This position is no longer open for application.";
        }
        List<RecruitmentApplication> all = applicationRepository.findAll();
        int myAppliedCount = 0;
        boolean alreadyApplied = false;
        for (RecruitmentApplication app : all) {
            if (!taUserId.equals(app.getTaUserId())) {
                continue;
            }
            myAppliedCount++;
            if (posting.getModuleId().equals(app.getModuleId())) {
                alreadyApplied = true;
            }
        }
        if (alreadyApplied) {
            return "You have already applied to this module.";
        }
        if (myAppliedCount >= MAX_APPLICATIONS) {
            return "Maximum 4 applications allowed.";
        }
        return null;
    }

    /**
     * Profile fields required before applying (includes a stored CV file).
     */
    public String applicationPrerequisiteError(String taUserId) {
        if (taUserId == null || taUserId.isBlank()) {
            return "No TA user selected.";
        }
        TAProfile profile = findProfileByQmId(taUserId);
        if (profile == null) {
            return "Please complete and save your profile on the Profile page before applying.";
        }
        return validateTaProfileForApplication(profile);
    }

    public ApplyResult submitApplication(String taUserId, String moduleId) {
        ModulePosting posting = findModuleById(moduleId);
        if (posting == null) {
            return ApplyResult.failure("Module not found.");
        }
        String eligibility = applyEligibilityError(taUserId, posting);
        if (eligibility != null) {
            return ApplyResult.failure(eligibility);
        }
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepository.findAll());
        RecruitmentApplication newApp = new RecruitmentApplication();
        newApp.setApplicationId("app-" + UUID.randomUUID().toString().substring(0, 8));
        newApp.setTaUserId(taUserId);
        newApp.setModuleId(moduleId);
        newApp.setStatus(ApplicationStatus.SUBMITTED);
        String cvFilePath = getCvFilePath(taUserId);
        if (cvFilePath != null && !cvFilePath.isBlank()) newApp.setCvFilePath(cvFilePath);
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        newApp.setCreatedAt(now);
        newApp.setUpdatedAt(now);
        all.add(newApp);
        try {
            applicationRepository.saveAll(all);
            reconcileAutoRejectWhenTaAcceptanceCapReached();
            return ApplyResult.success("Application submitted successfully.");
        } catch (IOException e) {
            return ApplyResult.failure("Failed to save application: " + e.getMessage());
        }
    }

    public String getCvFilePath(String taUserId) {
        for (TAProfile profile : profileRepository.findAll()) {
            if (taUserId.equals(profile.getQmId())) return profile.getCvFilePath();
        }
        return null;
    }

    public boolean isTaWillingToAcceptAdjustment(String taUserId) {
        if (taUserId == null || taUserId.isBlank()) return true;
        for (TAProfile profile : profileRepository.findAll()) {
            if (taUserId.equals(profile.getQmId())) return profile.isAllowAdjustment();
        }
        return true;
    }

    public TAProfile loadOrCreateProfile(User ta) {
        if (ta == null || ta.getQmId() == null || ta.getQmId().isBlank()) return null;
        for (TAProfile profile : profileRepository.findAll()) {
            if (ta.getQmId().equals(profile.getQmId())) return profile;
        }
        return newDefaultProfile(ta);
    }

    private static String validateTaProfileFields(TAProfile profile) {
        String name = profile.getName() == null ? "" : profile.getName().trim();
        if (name.isEmpty()) return "Name cannot be empty.";
        String phone = profile.getPhone() == null ? "" : profile.getPhone().trim();
        if (phone.isEmpty()) return "Phone cannot be empty.";
        if (phone.length() < MIN_PHONE_DIGITS) return "Phone must be at least " + MIN_PHONE_DIGITS + " digits.";
        for (int i = 0; i < phone.length(); i++) if (!Character.isDigit(phone.charAt(i))) return "Phone must contain only digits (no spaces or other characters).";
        String email = profile.getEmail() == null ? "" : profile.getEmail().trim();
        if (email.isEmpty()) return "Email cannot be empty.";
        if (!EMAIL_PATTERN.matcher(email).matches()) return "Email format is invalid.";
        List<String> skills = profile.getSkills();
        if (skills == null || skills.isEmpty()) return "Please enter at least one skill.";
        for (String skill : skills) if (skill == null || skill.isBlank()) return "Skills cannot be empty.";
        return null;
    }

    private static String validateTaProfileForApplication(TAProfile profile) {
        String base = validateTaProfileFields(profile);
        if (base != null) {
            return base;
        }
        String cv = profile.getCvFilePath();
        if (cv == null || cv.isBlank()) {
            return "Please upload your CV before applying.";
        }
        Path resolved = DataFileOpen.resolveUnderData(cv);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return "Please upload your CV before applying.";
        }
        return null;
    }

    private TAProfile findProfileByQmId(String taUserId) {
        if (taUserId == null || taUserId.isBlank()) {
            return null;
        }
        for (TAProfile profile : profileRepository.findAll()) {
            if (taUserId.equals(profile.getQmId())) {
                return profile;
            }
        }
        return null;
    }

    public ApplyResult saveProfile(TAProfile profile) {
        if (profile == null || profile.getQmId() == null || profile.getQmId().isBlank()) return ApplyResult.failure("Invalid profile.");
        String profileError = validateTaProfileFields(profile);
        if (profileError != null) return ApplyResult.failure(profileError);
        profile.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        List<TAProfile> all = new ArrayList<>(profileRepository.findAll());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (profile.getQmId().equals(all.get(i).getQmId())) { all.set(i, profile); replaced = true; break; }
        }
        if (!replaced) {
            if (profile.getProfileId() == null || profile.getProfileId().isBlank()) profile.setProfileId("prof-" + profile.getQmId());
            all.add(profile);
        }
        try {
            profileRepository.saveAll(all);
            return ApplyResult.success("Profile saved.");
        } catch (IOException e) {
            return ApplyResult.failure("Failed to save profile: " + e.getMessage());
        }
    }

    public List<ApplicationHistoryRow> listMyApplications(String taUserId) {
        List<ApplicationHistoryRow> rows = new ArrayList<>();
        if (taUserId == null || taUserId.isBlank()) return rows;
        reconcileAutoRejectWhenTaAcceptanceCapReached();
        Map<String, ModulePosting> moduleById = new HashMap<>();
        for (ModulePosting posting : moduleRepository.findAll()) moduleById.put(posting.getModuleId(), posting);
        List<RecruitmentApplication> mine = new ArrayList<>();
        for (RecruitmentApplication app : applicationRepository.findAll()) if (taUserId.equals(app.getTaUserId())) mine.add(app);
        mine.sort(Comparator.comparing(RecruitmentApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (RecruitmentApplication app : mine) {
            ModulePosting mod = moduleById.get(app.getModuleId());
            String moduleCode = mod != null && mod.getModuleCode() != null ? mod.getModuleCode() : "";
            String moduleName = mod != null && mod.getModuleName() != null ? mod.getModuleName() : "";
            String workload = mod != null && mod.getWorkload() != null ? mod.getWorkload() : "-";
            ApplicationStatus status = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;
            rows.add(new ApplicationHistoryRow(app.getApplicationId(), app.getModuleId(), moduleCode, moduleName, workload, app.getCreatedAt(), status, displayLabelForStatus(status)));
        }
        return rows;
    }

    public static String displayLabelForStatus(ApplicationStatus status) {
        if (status == null) return "Submitted";
        return switch (status) {
            case SUBMITTED -> "Submitted";
            case ACCEPTED -> "Accepted";
            case REJECTED -> "Rejected";
            case WAITING_FOR_ASSIGNMENT -> "Waiting for Assignment";
            case REASSIGNED -> "Reassigned";
        };
    }

    public static boolean countsAsAcceptedForTa(ApplicationStatus status) {
        return status == ApplicationStatus.ACCEPTED || status == ApplicationStatus.REASSIGNED;
    }

    public ApplyResult updateCvFilePath(User taUser, Path sourceFilePath) {
        if (taUser == null || sourceFilePath == null || !Files.isRegularFile(sourceFilePath)) return ApplyResult.failure("Invalid CV file.");
        String originalName = sourceFilePath.getFileName().toString();
        String extension = fileExtension(originalName);
        if (!"pdf".equalsIgnoreCase(extension)) return ApplyResult.failure("Only PDF files are supported.");
        String storedName = "cv-" + System.currentTimeMillis() + (extension.isEmpty() ? "" : "." + extension);
        Path userCvDir = AppPaths.dataDirectory().resolve("cvs").resolve(taUser.getQmId());
        Path targetPath = userCvDir.resolve(storedName);
        try {
            Files.createDirectories(userCvDir);
            Files.copy(sourceFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return ApplyResult.failure("Failed to store CV file: " + e.getMessage());
        }
        String relativeCvPath = "cvs/" + taUser.getQmId() + "/" + storedName;
        List<TAProfile> profiles = new ArrayList<>(profileRepository.findAll());
        TAProfile target = null;
        for (TAProfile profile : profiles) {
            if (taUser.getQmId().equals(profile.getQmId())) { target = profile; break; }
        }
        if (target == null) { target = newDefaultProfile(taUser); profiles.add(target); }
        target.setCvFilePath(relativeCvPath);
        target.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        try {
            profileRepository.saveAll(profiles);
            return ApplyResult.success("CV uploaded successfully.");
        } catch (IOException e) {
            return ApplyResult.failure("Failed to save CV path: " + e.getMessage());
        }
    }

    private static TAProfile newDefaultProfile(User ta) {
        TAProfile profile = new TAProfile();
        profile.setProfileId("prof-" + ta.getQmId());
        profile.setQmId(ta.getQmId());
        profile.setName(ta.getName());
        profile.setEmail(ta.getEmail());
        profile.setPhone("");
        profile.setSkills(new ArrayList<>());
        profile.setAllowAdjustment(true);
        return profile;
    }

    public ModulePosting findModuleById(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return null;
        }
        for (ModulePosting posting : moduleRepository.findAll()) {
            if (moduleId.equals(posting.getModuleId())) {
                return posting;
            }
        }
        return null;
    }

    public String getMoNameForModuleId(String moduleId) {
        ModulePosting posting = findModuleById(moduleId);
        if (posting == null || posting.getMoUserId() == null || posting.getMoUserId().isBlank()) {
            return "";
        }
        for (User user : userRepository.findAll()) {
            if (posting.getMoUserId().equals(user.getQmId())) {
                String name = user.getName();
                return name == null ? "" : name.trim();
            }
        }
        return "";
    }

    private String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }

    private int workloadSortKey(ModulePosting posting) {
        if (posting.getWorkload() == null) return Integer.MAX_VALUE;
        String digits = posting.getWorkload().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return Integer.MAX_VALUE;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    private boolean matchesFilter(ModulePosting posting, String keyword, String workloadFilter) {
        boolean workloadMatch = workloadFilter == null || "All workload".equals(workloadFilter) || workloadFilter.equals(posting.getWorkload());
        if (!workloadMatch) {
            return false;
        }

        String code = posting.getModuleCode() == null ? "" : posting.getModuleCode().toLowerCase();
        String name = posting.getModuleName() == null ? "" : posting.getModuleName().toLowerCase();
        String combined = (code + " " + name).trim();
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = keyword.toLowerCase().trim().replaceAll("[\\s\\-_/]+", " ");
        if (normalizedKeyword.isEmpty()) {
            return true;
        }

        if (combined.contains(normalizedKeyword)) {
            return true;
        }

        String[] terms = normalizedKeyword.split("\\s+");
        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (!combined.contains(term)) {
                return false;
            }
        }
        return true;
    }

    public static final class AcceptedNotification {
        private final String applicationId;
        private final String moduleId;
        private final String moduleCode;
        private final String moduleName;

        private AcceptedNotification(String applicationId, String moduleId, String moduleCode, String moduleName) {
            this.applicationId = applicationId;
            this.moduleId = moduleId;
            this.moduleCode = moduleCode;
            this.moduleName = moduleName;
        }

        public String getApplicationId() { return applicationId; }
        public String getModuleId() { return moduleId; }
        public String getModuleCode() { return moduleCode; }
        public String getModuleName() { return moduleName; }
    }

    public static final class DashboardData {
        private final int appliedCount;
        private final int acceptedCount;
        private final List<ModulePosting> postings;
        private DashboardData(int appliedCount, int acceptedCount, List<ModulePosting> postings) {
            this.appliedCount = appliedCount;
            this.acceptedCount = acceptedCount;
            this.postings = postings;
        }
        public int getAppliedCount() { return appliedCount; }
        public int getAcceptedCount() { return acceptedCount; }
        public List<ModulePosting> getPostings() { return postings; }
    }

    public static final class ApplicationHistoryRow {
        private final String applicationId;
        private final String moduleId;
        private final String moduleCode;
        private final String moduleName;
        private final String workload;
        private final String appliedOn;
        private final ApplicationStatus status;
        private final String statusDisplayLabel;
        public ApplicationHistoryRow(String applicationId, String moduleId, String moduleCode, String moduleName, String workload, String appliedOn, ApplicationStatus status, String statusDisplayLabel) {
            this.applicationId = applicationId;
            this.moduleId = moduleId;
            this.moduleCode = moduleCode;
            this.moduleName = moduleName;
            this.workload = workload;
            this.appliedOn = appliedOn;
            this.status = status;
            this.statusDisplayLabel = statusDisplayLabel;
        }
        public String getApplicationId() { return applicationId; }
        public String getModuleId() { return moduleId; }
        public String getModuleCode() { return moduleCode; }
        public String getModuleName() { return moduleName; }
        public String getWorkload() { return workload; }
        public String getAppliedOn() { return appliedOn; }
        public ApplicationStatus getStatus() { return status; }
        public String getStatusDisplayLabel() { return statusDisplayLabel; }
    }

    public static final class ApplyResult {
        private final boolean success;
        private final String message;
        private ApplyResult(boolean success, String message) { this.success = success; this.message = message; }
        public static ApplyResult success(String message) { return new ApplyResult(true, message); }
        public static ApplyResult failure(String message) { return new ApplyResult(false, message); }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
