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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.group58.recruit.config.AppPaths;
import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.repository.TAProfileRepository;

/**
 * TA-side business logic for listing modules and submitting applications.
 */
public final class TAService {
    private static final int MAX_APPLICATIONS = 4;

    private final ModulePostingRepository moduleRepository = new ModulePostingRepository();
    private final RecruitmentApplicationRepository applicationRepository = new RecruitmentApplicationRepository();
    private final TAProfileRepository profileRepository = new TAProfileRepository();

    public List<String> getWorkloadOptions() {
        List<ModulePosting> postings = moduleRepository.findAll();
        Set<String> options = new LinkedHashSet<>();
        options.add("All workload");
        postings.sort(Comparator.comparingInt(this::workloadSortKey).thenComparing(ModulePosting::getWorkload));
        for (ModulePosting posting : postings) {
            if (posting.getWorkload() != null && !posting.getWorkload().isBlank()) {
                options.add(posting.getWorkload());
            }
        }
        return new ArrayList<>(options);
    }

    public DashboardData getDashboardData(String taUserId, String keyword, String workloadFilter) {
        List<RecruitmentApplication> applications = applicationRepository.findAll();
        int appliedCount = 0;
        int acceptedCount = 0;
        Set<String> appliedModuleIds = new HashSet<>();
        for (RecruitmentApplication app : applications) {
            if (!taUserId.equals(app.getTaUserId())) {
                continue;
            }
            appliedCount++;
            appliedModuleIds.add(app.getModuleId());
            if (app.getStatus() == ApplicationStatus.ACCEPTED) {
                acceptedCount++;
            }
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<ModulePosting> postings = new ArrayList<>(moduleRepository.findAll());
        postings.sort(Comparator.comparing(ModulePosting::getModuleCode));

        List<ModulePosting> filteredPostings = new ArrayList<>();
        for (ModulePosting posting : postings) {
            if (appliedModuleIds.contains(posting.getModuleId())) {
                continue;
            }
            if (matchesFilter(posting, normalizedKeyword, workloadFilter)) {
                filteredPostings.add(posting);
            }
        }
        return new DashboardData(appliedCount, acceptedCount, filteredPostings);
    }

    public ApplyResult submitApplication(String taUserId, String moduleId) {
        List<RecruitmentApplication> all = new ArrayList<>(applicationRepository.findAll());
        int myAppliedCount = 0;
        boolean duplicate = false;
        for (RecruitmentApplication app : all) {
            if (!taUserId.equals(app.getTaUserId())) {
                continue;
            }
            myAppliedCount++;
            if (moduleId.equals(app.getModuleId())) {
                duplicate = true;
            }
        }
        if (duplicate) {
            return ApplyResult.failure("You have already applied to this module.");
        }
        if (myAppliedCount >= MAX_APPLICATIONS) {
            return ApplyResult.failure("Maximum 4 applications allowed.");
        }

        ModulePosting posting = findModuleById(moduleId);
        if (posting == null) {
            return ApplyResult.failure("Module not found.");
        }
        if (posting.getStatus() != ModuleStatus.OPEN || posting.getVacanciesFilled() >= posting.getVacanciesTotal()) {
            return ApplyResult.failure("This position is no longer open for application.");
        }

        RecruitmentApplication newApp = new RecruitmentApplication();
        newApp.setApplicationId("app-" + UUID.randomUUID().toString().substring(0, 8));
        newApp.setTaUserId(taUserId);
        newApp.setModuleId(moduleId);
        newApp.setAppliedRoleName("Teaching Assistant");
        newApp.setStatus(ApplicationStatus.SUBMITTED);
        String cvFilePath = getCvFilePath(taUserId);
        if (cvFilePath != null && !cvFilePath.isBlank()) {
            newApp.setCvFilePath(cvFilePath);
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        newApp.setCreatedAt(now);
        newApp.setUpdatedAt(now);
        all.add(newApp);

        try {
            applicationRepository.saveAll(all);
            return ApplyResult.success("Application submitted successfully.");
        } catch (IOException e) {
            return ApplyResult.failure("Failed to save application: " + e.getMessage());
        }
    }

    private ModulePosting findModuleById(String moduleId) {
        List<ModulePosting> postings = moduleRepository.findAll();
        for (ModulePosting posting : postings) {
            if (moduleId.equals(posting.getModuleId())) {
                return posting;
            }
        }
        return null;
    }

    public String getCvFilePath(String taUserId) {
        List<TAProfile> profiles = profileRepository.findAll();
        for (TAProfile profile : profiles) {
            if (taUserId.equals(profile.getQmId())) {
                return profile.getCvFilePath();
            }
        }
        return null;
    }

    /**
     * Reassignment / adjustment willingness is stored only on {@link TAProfile}. Defaults to {@code true} when no profile exists yet.
     */
    public boolean isTaWillingToAcceptAdjustment(String taUserId) {
        if (taUserId == null || taUserId.isBlank()) {
            return true;
        }
        for (TAProfile profile : profileRepository.findAll()) {
            if (taUserId.equals(profile.getQmId())) {
                return profile.isAllowAdjustment();
            }
        }
        return true;
    }

    /**
     * Returns the persisted profile for this TA, or a fresh default (not stored until {@link #saveProfile}).
     */
    public TAProfile loadOrCreateProfile(User ta) {
        if (ta == null || ta.getQmId() == null || ta.getQmId().isBlank()) {
            return null;
        }
        for (TAProfile profile : profileRepository.findAll()) {
            if (ta.getQmId().equals(profile.getQmId())) {
                return profile;
            }
        }
        return newDefaultProfile(ta);
    }

    public ApplyResult saveProfile(TAProfile profile) {
        if (profile == null || profile.getQmId() == null || profile.getQmId().isBlank()) {
            return ApplyResult.failure("Invalid profile.");
        }
        profile.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        List<TAProfile> all = new ArrayList<>(profileRepository.findAll());
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (profile.getQmId().equals(all.get(i).getQmId())) {
                all.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (profile.getProfileId() == null || profile.getProfileId().isBlank()) {
                profile.setProfileId("prof-" + profile.getQmId());
            }
            all.add(profile);
        }
        try {
            profileRepository.saveAll(all);
            return ApplyResult.success("Profile saved.");
        } catch (IOException e) {
            return ApplyResult.failure("Failed to save profile: " + e.getMessage());
        }
    }

    /**
     * Applications for this TA with module labels and human-readable status.
     */
    public List<ApplicationHistoryRow> listMyApplications(String taUserId) {
        List<ApplicationHistoryRow> rows = new ArrayList<>();
        if (taUserId == null || taUserId.isBlank()) {
            return rows;
        }
        Map<String, ModulePosting> moduleById = new HashMap<>();
        for (ModulePosting posting : moduleRepository.findAll()) {
            moduleById.put(posting.getModuleId(), posting);
        }
        List<RecruitmentApplication> mine = new ArrayList<>();
        for (RecruitmentApplication app : applicationRepository.findAll()) {
            if (taUserId.equals(app.getTaUserId())) {
                mine.add(app);
            }
        }
        mine.sort(Comparator.comparing(RecruitmentApplication::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        for (RecruitmentApplication app : mine) {
            ModulePosting mod = moduleById.get(app.getModuleId());
            String moduleCode = mod != null && mod.getModuleCode() != null ? mod.getModuleCode() : "";
            String moduleName = mod != null && mod.getModuleName() != null ? mod.getModuleName() : "";
            ApplicationStatus status = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;
            String roleName = app.getAppliedRoleName() != null ? app.getAppliedRoleName() : "";
            rows.add(new ApplicationHistoryRow(
                    app.getApplicationId(),
                    app.getModuleId(),
                    moduleCode,
                    moduleName,
                    roleName,
                    status,
                    displayLabelForStatus(status)));
        }
        return rows;
    }

    public static String displayLabelForStatus(ApplicationStatus status) {
        if (status == null) {
            return "Submitted";
        }
        switch (status) {
            case SUBMITTED:
                return "Submitted";
            case ACCEPTED:
                return "Accepted";
            case REJECTED:
                return "Rejected";
            case WAITING_FOR_ASSIGNMENT:
                return "Waiting for Assignment";
            case REASSIGNED:
                return "Reassigned";
            default:
                return status.name();
        }
    }

    public ApplyResult updateCvFilePath(User taUser, Path sourceFilePath) {
        if (taUser == null || sourceFilePath == null || !Files.isRegularFile(sourceFilePath)) {
            return ApplyResult.failure("Invalid CV file.");
        }
        String originalName = sourceFilePath.getFileName().toString();
        String extension = fileExtension(originalName);
        if (!"pdf".equalsIgnoreCase(extension)) {
            return ApplyResult.failure("Only PDF files are supported.");
        }
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
            if (taUser.getQmId().equals(profile.getQmId())) {
                target = profile;
                break;
            }
        }
        if (target == null) {
            target = newDefaultProfile(taUser);
            profiles.add(target);
        }
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

    private String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private int workloadSortKey(ModulePosting posting) {
        if (posting.getWorkload() == null) {
            return Integer.MAX_VALUE;
        }
        String digits = posting.getWorkload().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean matchesFilter(ModulePosting posting, String keyword, String workloadFilter) {
        boolean keywordMatch = keyword.isEmpty()
                || posting.getModuleCode().toLowerCase().contains(keyword)
                || posting.getModuleName().toLowerCase().contains(keyword);
        boolean workloadMatch = workloadFilter == null
                || "All workload".equals(workloadFilter)
                || workloadFilter.equals(posting.getWorkload());
        return keywordMatch && workloadMatch;
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

        public int getAppliedCount() {
            return appliedCount;
        }

        public int getAcceptedCount() {
            return acceptedCount;
        }

        public List<ModulePosting> getPostings() {
            return postings;
        }
    }

    public static final class ApplicationHistoryRow {
        private final String applicationId;
        private final String moduleId;
        private final String moduleCode;
        private final String moduleName;
        private final String appliedRoleName;
        private final ApplicationStatus status;
        private final String statusDisplayLabel;

        public ApplicationHistoryRow(
                String applicationId,
                String moduleId,
                String moduleCode,
                String moduleName,
                String appliedRoleName,
                ApplicationStatus status,
                String statusDisplayLabel) {
            this.applicationId = applicationId;
            this.moduleId = moduleId;
            this.moduleCode = moduleCode;
            this.moduleName = moduleName;
            this.appliedRoleName = appliedRoleName;
            this.status = status;
            this.statusDisplayLabel = statusDisplayLabel;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getModuleId() {
            return moduleId;
        }

        public String getModuleCode() {
            return moduleCode;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getAppliedRoleName() {
            return appliedRoleName;
        }

        public ApplicationStatus getStatus() {
            return status;
        }

        public String getStatusDisplayLabel() {
            return statusDisplayLabel;
        }
    }

    public static final class ApplyResult {
        private final boolean success;
        private final String message;

        private ApplyResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ApplyResult success(String message) {
            return new ApplyResult(true, message);
        }

        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
