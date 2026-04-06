package com.group58.recruit.service;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.ReassignActionType;
import com.group58.recruit.model.ReassignLog;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.model.User;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.ReassignLogRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;
import com.group58.recruit.repository.TAProfileRepository;
import com.group58.recruit.repository.UserRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-side business logic: course recruitment dashboard + TA reassign/reject.
 */
public final class AdminService {

    public enum CourseFilter {
        ALL,
        FINISHED,
        UNFINISHED
    }

    public enum ApplicantFilter {
        ALL,
        WAITING_FOR_ADJUSTMENT
    }

    private final ModulePostingRepository moduleRepo = new ModulePostingRepository();
    private final RecruitmentApplicationRepository applicationRepo = new RecruitmentApplicationRepository();
    private final UserRepository userRepo = new UserRepository();
    private final TAProfileRepository profileRepo = new TAProfileRepository();
    private final ReassignLogRepository logRepo = new ReassignLogRepository();

    public static final class CourseCardRow {
        private final ModulePosting module;
        private final String moDisplayName;
        private final int remaining;
        private final String recruitmentStatusText;

        public CourseCardRow(ModulePosting module, String moDisplayName, int remaining, String recruitmentStatusText) {
            this.module = module;
            this.moDisplayName = moDisplayName;
            this.remaining = remaining;
            this.recruitmentStatusText = recruitmentStatusText;
        }

        public ModulePosting getModule() {
            return module;
        }

        public String getMoDisplayName() {
            return moDisplayName;
        }

        public int getRemaining() {
            return remaining;
        }

        public String getRecruitmentStatusText() {
            return recruitmentStatusText;
        }
    }

    public static final class ApplicationCardRow {
        private final String applicationId;
        private final String taUserId;
        private final String taDisplayName;
        private final String moduleId;
        private final String moduleCode;
        private final String moduleName;
        private final ApplicationStatus status;
        private final boolean allowAdjustment;
        private final String cvFilePath;

        public ApplicationCardRow(
                String applicationId,
                String taUserId,
                String taDisplayName,
                String moduleId,
                String moduleCode,
                String moduleName,
                ApplicationStatus status,
                boolean allowAdjustment,
                String cvFilePath
        ) {
            this.applicationId = applicationId;
            this.taUserId = taUserId;
            this.taDisplayName = taDisplayName;
            this.moduleId = moduleId;
            this.moduleCode = moduleCode;
            this.moduleName = moduleName;
            this.status = status;
            this.allowAdjustment = allowAdjustment;
            this.cvFilePath = cvFilePath;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getTaUserId() {
            return taUserId;
        }

        public String getTaDisplayName() {
            return taDisplayName;
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

        public ApplicationStatus getStatus() {
            return status;
        }

        public boolean isAllowAdjustment() {
            return allowAdjustment;
        }

        public String getCvFilePath() {
            return cvFilePath;
        }
    }

    public List<CourseCardRow> listCourseRecruitment(CourseFilter filter) {
        List<ModulePosting> modules = moduleRepo.findAll();
        Map<String, User> usersByQmId = toUserByQmId(userRepo.findAll());

        List<CourseCardRow> rows = new ArrayList<>();
        for (ModulePosting m : modules) {
            boolean finished = isModuleFinished(m);
            if (filter == CourseFilter.FINISHED && !finished) {
                continue;
            }
            if (filter == CourseFilter.UNFINISHED && finished) {
                continue;
            }

            String moDisplay = moDisplayName(m, usersByQmId);
            int remaining = Math.max(0, safeVacanciesTotal(m) - safeVacanciesFilled(m));
            String recruitmentStatusText = buildRecruitmentStatusText(remaining);
            rows.add(new CourseCardRow(m, moDisplay, remaining, recruitmentStatusText));
        }

        rows.sort(Comparator
                .comparing((CourseCardRow r) -> safeModuleCode(r.getModule()))
                .thenComparing((CourseCardRow r) -> {
                    ModulePosting m = r.getModule();
                    return m != null && m.getModuleName() != null ? m.getModuleName() : "";
                }));
        return rows;
    }

    public List<ApplicationCardRow> listApplicantDashboard(ApplicantFilter filter) {
        new TAService().reconcileAutoRejectWhenTaAcceptanceCapReached();
        List<RecruitmentApplication> applications = applicationRepo.findAll();
        Map<String, ModulePosting> moduleById = toModuleById(moduleRepo.findAll());
        Map<String, TAProfile> profileByQmId = toProfileByQmId(profileRepo.findAll());
        Map<String, User> userByQmId = toUserByQmId(userRepo.findAll());

        List<ApplicationCardRow> rows = new ArrayList<>();
        for (RecruitmentApplication app : applications) {
            ApplicationStatus status = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;
            boolean allowAdj = allowAdjustment(profileByQmId, app.getTaUserId());
            // Waiting applicants are those whose status is set to WAITING_FOR_ASSIGNMENT.
            // REJECTED is reserved for final rejected applications.
            boolean waiting = status == ApplicationStatus.WAITING_FOR_ASSIGNMENT && allowAdj;

            if (filter == ApplicantFilter.ALL) {
                // ALL: show all TA applications regardless of status.
            } else if (filter == ApplicantFilter.WAITING_FOR_ADJUSTMENT) {
                if (!waiting) {
                    continue;
                }
            }

            ModulePosting mod = moduleById.get(app.getModuleId());
            String moduleCode = mod != null && mod.getModuleCode() != null ? mod.getModuleCode() : app.getModuleId();
            String moduleName = mod != null && mod.getModuleName() != null ? mod.getModuleName() : "";

            String taDisplay = taDisplayName(app.getTaUserId(), profileByQmId, userByQmId);
            String cvPath = resolveCvPath(app, profileByQmId);

            rows.add(new ApplicationCardRow(
                    app.getApplicationId(),
                    app.getTaUserId(),
                    taDisplay,
                    app.getModuleId(),
                    moduleCode,
                    moduleName,
                    status,
                    allowAdj,
                    cvPath
            ));
        }

        rows.sort(Comparator.comparingInt(this::appStatusPriority));

        return rows;
    }

    public List<ModulePosting> listReassignableCourses() {
        List<ModulePosting> modules = moduleRepo.findAll();
        List<ModulePosting> result = new ArrayList<>();
        for (ModulePosting m : modules) {
            int total = safeVacanciesTotal(m);
            int filled = safeVacanciesFilled(m);
            if (total > 0 && filled < total) {
                // For finished modules, we still avoid reassignment to keep workflow consistent.
                if (m.getStatus() == ModuleStatus.FINISHED) {
                    continue;
                }
                result.add(m);
            }
        }
        result.sort(Comparator.comparing(ModulePosting::getModuleCode));
        return result;
    }

    public ActionResult reassignApplication(String applicationId, String toModuleId, String adminUserId) {
        if (applicationId == null || applicationId.isBlank() || toModuleId == null || toModuleId.isBlank()) {
            return ActionResult.failure("Invalid application or module id.");
        }
        if (adminUserId == null || adminUserId.isBlank()) {
            return ActionResult.failure("Invalid admin session.");
        }

        List<ModulePosting> modules = new ArrayList<>(moduleRepo.findAll());
        List<RecruitmentApplication> applications = new ArrayList<>(applicationRepo.findAll());
        List<ReassignLog> logs = new ArrayList<>(logRepo.findAll());

        RecruitmentApplication app = findApplicationById(applications, applicationId);
        if (app == null) {
            return ActionResult.failure("Application not found.");
        }

        Map<String, TAProfile> profileByQmId = toProfileByQmId(profileRepo.findAll());
        boolean allowAdj = allowAdjustment(profileByQmId, app.getTaUserId());
        if (!allowAdj) {
            return ActionResult.failure("This TA does not accept reassignment (allowAdjustment=false).");
        }

        ApplicationStatus curStatus = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;
        if (curStatus != ApplicationStatus.WAITING_FOR_ASSIGNMENT) {
            return ActionResult.failure("Application is not eligible for reassign (current: " + curStatus + ").");
        }

        ModulePosting fromModule = findModuleById(modules, app.getModuleId());
        ModulePosting toModule = findModuleById(modules, toModuleId);
        if (toModule == null) {
            return ActionResult.failure("Target module not found.");
        }

        int total = safeVacanciesTotal(toModule);
        int filled = safeVacanciesFilled(toModule);
        if (total <= 0 || filled >= total) {
            return ActionResult.failure("Target module has no available vacancies.");
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String fromModuleId = fromModule != null ? fromModule.getModuleId() : app.getModuleId();

        // Update application: move to new module and mark final admin reassignment.
        app.setModuleId(toModuleId);
        app.setStatus(ApplicationStatus.REASSIGNED);
        app.setMoDecisionBy(adminUserId);
        app.setDecisionTime(now);
        app.setUpdatedAt(now);

        // Update module capacity.
        toModule.setVacanciesFilled(filled + 1);
        toModule.setUpdatedAt(now);
        if (toModule.getVacanciesFilled() >= toModule.getVacanciesTotal()) {
            toModule.setStatus(ModuleStatus.FINISHED);
        }

        // Audit log.
        ReassignLog log = new ReassignLog();
        log.setLogId("log-" + UUID.randomUUID().toString().substring(0, 8));
        log.setApplicationId(app.getApplicationId());
        log.setFromModuleId(fromModuleId);
        log.setToModuleId(toModuleId);
        log.setActionType(ReassignActionType.REASSIGN);
        log.setAdminUserId(adminUserId);
        log.setReason("Admin reassign to " + toModuleId);
        log.setCreatedAt(now);
        logs.add(log);

        try {
            applicationRepo.saveAll(applications);
            moduleRepo.saveAll(modules);
            logRepo.saveAll(logs);
            new TAService().reconcileAutoRejectWhenTaAcceptanceCapReached();
            return ActionResult.success("Reassigned successfully.");
        } catch (IOException e) {
            return ActionResult.failure("Save failed: " + e.getMessage());
        }
    }

    public ActionResult finalRejectApplication(String applicationId, String adminUserId) {
        if (applicationId == null || applicationId.isBlank()) {
            return ActionResult.failure("Invalid application id.");
        }
        if (adminUserId == null || adminUserId.isBlank()) {
            return ActionResult.failure("Invalid admin session.");
        }

        List<RecruitmentApplication> applications = new ArrayList<>(applicationRepo.findAll());
        List<ReassignLog> logs = new ArrayList<>(logRepo.findAll());

        RecruitmentApplication app = findApplicationById(applications, applicationId);
        if (app == null) {
            return ActionResult.failure("Application not found.");
        }

        ApplicationStatus curStatus = app.getStatus() != null ? app.getStatus() : ApplicationStatus.SUBMITTED;
        if (curStatus != ApplicationStatus.WAITING_FOR_ASSIGNMENT) {
            return ActionResult.failure("Application is not eligible for final reject (current: " + curStatus + ").");
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        app.setStatus(ApplicationStatus.REJECTED);
        app.setMoDecisionBy(adminUserId);
        app.setDecisionTime(now);
        app.setUpdatedAt(now);

        ReassignLog log = new ReassignLog();
        log.setLogId("log-" + UUID.randomUUID().toString().substring(0, 8));
        log.setApplicationId(app.getApplicationId());
        log.setFromModuleId(app.getModuleId());
        log.setToModuleId(null);
        log.setActionType(ReassignActionType.FINAL_REJECT);
        log.setAdminUserId(adminUserId);
        log.setReason("Final reject by admin");
        log.setCreatedAt(now);
        logs.add(log);

        try {
            applicationRepo.saveAll(applications);
            logRepo.saveAll(logs);
            return ActionResult.success("Application rejected successfully.");
        } catch (IOException e) {
            return ActionResult.failure("Save failed: " + e.getMessage());
        }
    }

    private int appStatusPriority(ApplicationCardRow row) {
        if (row == null) return Integer.MAX_VALUE;
        ApplicationStatus s = row.getStatus();
        if (s == ApplicationStatus.ACCEPTED) return 0;
        if (s == ApplicationStatus.WAITING_FOR_ASSIGNMENT) return 1;
        if (s == ApplicationStatus.REASSIGNED) return 2;
        if (s == ApplicationStatus.REJECTED) return 3;
        return 10;
    }

    private boolean isModuleFinished(ModulePosting m) {
        if (m == null) return true;
        if (m.getStatus() == ModuleStatus.FINISHED) return true;
        int total = safeVacanciesTotal(m);
        int filled = safeVacanciesFilled(m);
        return total > 0 && filled >= total;
    }

    private String buildRecruitmentStatusText(int remaining) {
        if (remaining <= 0) {
            return "Recruitment completed";
        }
        if (remaining == 1) {
            return "One spot remaining";
        }
        if (remaining == 2) {
            return "Two spots remaining";
        }
        return remaining + " spots remaining";
    }

    private int safeVacanciesTotal(ModulePosting m) {
        if (m == null) return 0;
        return Math.max(0, m.getVacanciesTotal());
    }

    private int safeVacanciesFilled(ModulePosting m) {
        if (m == null) return 0;
        return Math.max(0, m.getVacanciesFilled());
    }

    private String safeModuleCode(ModulePosting m) {
        if (m == null || m.getModuleCode() == null) return "";
        return m.getModuleCode();
    }

    private Map<String, TAProfile> toProfileByQmId(List<TAProfile> profiles) {
        Map<String, TAProfile> map = new HashMap<>();
        if (profiles == null) return map;
        for (TAProfile p : profiles) {
            if (p != null && p.getQmId() != null) {
                map.put(p.getQmId(), p);
            }
        }
        return map;
    }

    private Map<String, ModulePosting> toModuleById(List<ModulePosting> modules) {
        Map<String, ModulePosting> map = new HashMap<>();
        if (modules == null) return map;
        for (ModulePosting m : modules) {
            if (m != null && m.getModuleId() != null) {
                map.put(m.getModuleId(), m);
            }
        }
        return map;
    }

    private Map<String, User> toUserByQmId(List<User> users) {
        Map<String, User> map = new HashMap<>();
        if (users == null) return map;
        for (User u : users) {
            if (u != null && u.getQmId() != null) {
                map.put(u.getQmId(), u);
            }
        }
        return map;
    }

    private String moDisplayName(ModulePosting m, Map<String, User> usersByQmId) {
        if (m == null) return "-";
        String moId = m.getMoUserId();
        User u = usersByQmId.get(moId);
        if (u == null) {
            return moId != null ? moId : "-";
        }
        if (u.getName() != null && !u.getName().isBlank()) {
            return u.getName();
        }
        return u.getQmId();
    }

    private String taDisplayName(String taUserId, Map<String, TAProfile> profileByQmId, Map<String, User> userByQmId) {
        TAProfile p = profileByQmId.get(taUserId);
        if (p != null && p.getName() != null && !p.getName().isBlank()) {
            return p.getName();
        }
        User u = userByQmId.get(taUserId);
        if (u != null && u.getName() != null && !u.getName().isBlank()) {
            return u.getName();
        }
        return taUserId;
    }

    private boolean allowAdjustment(Map<String, TAProfile> profileByQmId, String taUserId) {
        TAProfile p = profileByQmId.get(taUserId);
        return p == null || p.isAllowAdjustment();
    }

    private String resolveCvPath(RecruitmentApplication app, Map<String, TAProfile> profileByQmId) {
        if (app != null && app.getCvFilePath() != null && !app.getCvFilePath().isBlank()) {
            return app.getCvFilePath();
        }
        TAProfile p = profileByQmId.get(app.getTaUserId());
        return p != null ? p.getCvFilePath() : null;
    }

    private RecruitmentApplication findApplicationById(List<RecruitmentApplication> applications, String applicationId) {
        if (applications == null) return null;
        for (RecruitmentApplication a : applications) {
            if (a != null && applicationId.equals(a.getApplicationId())) {
                return a;
            }
        }
        return null;
    }

    private ModulePosting findModuleById(List<ModulePosting> modules, String moduleId) {
        if (modules == null) return null;
        for (ModulePosting m : modules) {
            if (m != null && moduleId.equals(m.getModuleId())) {
                return m;
            }
        }
        return null;
    }

    public boolean hasUnreviewedApplications() {
        List<RecruitmentApplication> apps = applicationRepo.findAll();
        return apps.stream().anyMatch(app -> app.getStatus() == ApplicationStatus.SUBMITTED);
    }

    /**
     * One line per MO who still has at least one {@link ApplicationStatus#SUBMITTED} application
     * on a module they own. Used by Admin UI to explain why reassignment is blocked.
     */
    public List<String> listMoPendingSubmittedSummaryLines() {
        List<ModulePosting> modules = moduleRepo.findAll();
        Map<String, ModulePosting> modById = new HashMap<>();
        for (ModulePosting m : modules) {
            if (m != null && m.getModuleId() != null) {
                modById.put(m.getModuleId(), m);
            }
        }
        Map<String, User> userByQmId = new HashMap<>();
        for (User u : userRepo.findAll()) {
            if (u != null && u.getQmId() != null) {
                userByQmId.put(u.getQmId(), u);
            }
        }
        Map<String, Map<String, Integer>> byMo = new HashMap<>();
        for (RecruitmentApplication app : applicationRepo.findAll()) {
            if (app == null || app.getStatus() != ApplicationStatus.SUBMITTED) {
                continue;
            }
            String mid = app.getModuleId();
            ModulePosting m = mid == null ? null : modById.get(mid);
            if (m == null) {
                continue;
            }
            String moId = m.getMoUserId();
            if (moId == null || moId.isBlank()) {
                continue;
            }
            byMo.computeIfAbsent(moId, k -> new HashMap<>()).merge(mid, 1, Integer::sum);
        }
        List<String> moIds = new ArrayList<>(byMo.keySet());
        moIds.sort(Comparator.comparing(id -> moDisplayNameForAdmin(id, userByQmId), String.CASE_INSENSITIVE_ORDER));
        List<String> lines = new ArrayList<>();
        for (String moId : moIds) {
            Map<String, Integer> perMod = byMo.get(moId);
            List<String> modIds = new ArrayList<>(perMod.keySet());
            modIds.sort(Comparator.comparing(
                    mid -> {
                        ModulePosting mm = modById.get(mid);
                        return mm != null && mm.getModuleCode() != null ? mm.getModuleCode() : mid;
                    },
                    String.CASE_INSENSITIVE_ORDER));
            StringBuilder sb = new StringBuilder();
            for (String modId : modIds) {
                int cnt = perMod.get(modId);
                ModulePosting mm = modById.get(modId);
                String code = mm != null && mm.getModuleCode() != null ? mm.getModuleCode() : modId;
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(code).append(" (").append(cnt).append(")");
            }
            lines.add(moDisplayNameForAdmin(moId, userByQmId) + " (" + moId + "): " + sb);
        }
        return lines;
    }

    private static String moDisplayNameForAdmin(String moUserId, Map<String, User> userByQmId) {
        User u = userByQmId.get(moUserId);
        if (u == null) {
            return moUserId;
        }
        String n = u.getName();
        return (n == null || n.isBlank()) ? moUserId : n.trim();
    }

    public static final class ActionResult {
        private final boolean success;
        private final String message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}

