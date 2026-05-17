package com.group58.recruit.service;

import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.ModuleStatus;
import com.group58.recruit.model.ReassignActionType;
import com.group58.recruit.service.AdminService.ApplicantFilter;
import com.group58.recruit.service.AdminService.ApplicationCardRow;
import com.group58.recruit.service.AdminService.CourseCardRow;
import com.group58.recruit.service.AdminService.CourseFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prepares non-visual data for the Admin JavaFX dashboard.
 */
public final class AdminDashboardDataService {

    private final AdminService adminService;

    public AdminDashboardDataService(AdminService adminService) {
        if (adminService == null) {
            throw new IllegalArgumentException("adminService is required");
        }
        this.adminService = adminService;
    }

    public DashboardStats loadStats() {
        List<CourseCardRow> courses = adminService.listCourseRecruitment(CourseFilter.ALL);
        long openCount = courses.stream().filter(r -> {
            ModulePosting m = r.getModule();
            return m != null && m.getStatus() == ModuleStatus.OPEN && r.getRemaining() > 0;
        }).count();
        int applicationCount = adminService.listApplicantDashboard(ApplicantFilter.ALL).size();
        int pendingAdjustmentCount = adminService
                .listApplicantDashboard(ApplicantFilter.WAITING_FOR_ADJUSTMENT)
                .size();
        return new DashboardStats(courses.size(), openCount, applicationCount, pendingAdjustmentCount);
    }

    /**
     * Until the log repository exposes date-bucket queries, split the total count
     * across fixed buckets so the UI can render a stable trend placeholder.
     */
    public List<Long> approximateWeeklyReassignCounts(int bucketCount) {
        int buckets = Math.max(1, bucketCount);
        Map<ReassignActionType, Long> map = adminService.countReassignLogsByActionType();
        long total = map.getOrDefault(ReassignActionType.REASSIGN, 0L);
        List<Long> weeks = new ArrayList<>();
        for (int i = 0; i < buckets; i++) {
            weeks.add(0L);
        }
        for (long i = 0; i < total; i++) {
            int bucket = (int) (i % buckets);
            weeks.set(bucket, weeks.get(bucket) + 1);
        }
        return weeks;
    }

    public List<AttentionRow> listAttentionRows() {
        List<AttentionRow> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        List<ApplicationCardRow> allApps =
                adminService.listApplicantDashboard(ApplicantFilter.ALL);
        List<ApplicationCardRow> waitingList =
                adminService.listApplicantDashboard(ApplicantFilter.WAITING_FOR_ADJUSTMENT);
        int waitingTa = waitingList.size();

        Map<String, Long> submittedByModule = allApps.stream()
                .filter(r -> r.getStatus() == ApplicationStatus.SUBMITTED)
                .collect(Collectors.groupingBy(ApplicationCardRow::getModuleId, Collectors.counting()));

        List<CourseCardRow> allCourses = adminService.listCourseRecruitment(CourseFilter.ALL);
        Map<String, CourseCardRow> courseByModuleId = new HashMap<>();
        for (CourseCardRow cr : allCourses) {
            ModulePosting mp = cr.getModule();
            if (mp != null && mp.getModuleId() != null) {
                courseByModuleId.put(mp.getModuleId(), cr);
            }
        }

        if (waitingTa > 0) {
            int openSeats = 0;
            for (ModulePosting m : adminService.listReassignableCourses()) {
                openSeats += Math.max(0, m.getVacanciesTotal() - m.getVacanciesFilled());
            }
            String vacCol = openSeats + " open seat(s) (reassign targets)";

            Map<String, Integer> waitByModule = new HashMap<>();
            for (ApplicationCardRow w : waitingList) {
                String mid = w.getModuleId();
                if (mid != null && !mid.isBlank()) {
                    waitByModule.merge(mid, 1, Integer::sum);
                }
            }
            int mappedWaiting = waitByModule.values().stream().mapToInt(Integer::intValue).sum();
            int unmappedWaiting = waitingTa - mappedWaiting;

            if (unmappedWaiting > 0 && keys.add("UNMAPPED|WAITING_ADJUST")) {
                rows.add(new AttentionRow(null, "(Application(s) without module)",
                        "(No MO mapped)", vacCol, String.valueOf(unmappedWaiting),
                        unmappedWaiting + " TA(s) in \"Waiting for adjustment\" with no module id - check data",
                        "high", true));
            }

            List<String> moduleOrder = new ArrayList<>(waitByModule.keySet());
            moduleOrder.sort(Comparator.comparing(mid -> {
                CourseCardRow cr = courseByModuleId.get(mid);
                ModulePosting mp = cr != null ? cr.getModule() : null;
                return mp != null ? shortModuleLabel(mp) : mid;
            }, String.CASE_INSENSITIVE_ORDER));

            for (String mid : moduleOrder) {
                if (!keys.add(mid + "|WAITING_ADJUST")) {
                    continue;
                }
                int n = waitByModule.get(mid);
                CourseCardRow cr = courseByModuleId.get(mid);
                ModulePosting m = cr != null ? cr.getModule() : null;
                rows.add(new AttentionRow(mid,
                        m != null ? shortModuleLabel(m) : mid,
                        cr != null ? cr.getMoDisplayName() : "(No MO mapped)",
                        vacCol, String.valueOf(n),
                        n + " TA(s) in \"Waiting for adjustment\" for this module - assign on the Reassignment tab",
                        "medium"));
            }
        }

        List<CourseCardRow> openWithSub = new ArrayList<>();
        for (CourseCardRow cr : allCourses) {
            ModulePosting m = cr.getModule();
            if (m == null || m.getModuleId() == null) {
                continue;
            }
            int sub = submittedByModule.getOrDefault(m.getModuleId(), 0L).intValue();
            if (m.getStatus() == ModuleStatus.OPEN && sub > 0) {
                openWithSub.add(cr);
            }
        }
        openWithSub.sort(Comparator.comparingInt((CourseCardRow cr) ->
                submittedByModule.getOrDefault(cr.getModule().getModuleId(), 0L).intValue()).reversed());

        int cap = 0;
        for (CourseCardRow cr : openWithSub) {
            if (cap++ >= 10) {
                break;
            }
            ModulePosting m = cr.getModule();
            String mid = m.getModuleId();
            if (!keys.add(mid + "|MO_BACKLOG")) {
                continue;
            }
            int sub = submittedByModule.getOrDefault(mid, 0L).intValue();
            int t = Math.max(0, m.getVacanciesTotal());
            int f = Math.max(0, m.getVacanciesFilled());
            rows.add(new AttentionRow(mid, shortModuleLabel(m),
                    cr.getMoDisplayName(), f + "/" + t, String.valueOf(sub),
                    "MO review backlog: " + sub + " submitted CV(s) while module is OPEN ("
                            + cr.getRemaining() + " seat(s) left)",
                    sub >= 6 ? "medium" : "low"));
        }

        for (CourseCardRow cr : allCourses) {
            ModulePosting m = cr.getModule();
            if (m == null || m.getModuleId() == null) {
                continue;
            }
            String mid = m.getModuleId();
            int sub = submittedByModule.getOrDefault(mid, 0L).intValue();
            int total = Math.max(0, m.getVacanciesTotal());
            int filled = Math.max(0, m.getVacanciesFilled());
            String vacTxt = filled + "/" + total;

            if (total > 0 && filled >= total && sub > 0 && keys.add(mid + "|FULL_PENDING")) {
                rows.add(new AttentionRow(mid, shortModuleLabel(m),
                        cr.getMoDisplayName(), vacTxt, String.valueOf(sub),
                        "Capacity full but " + sub
                                + " application(s) still SUBMITTED - data needs MO action",
                        "high"));
            } else if (cr.getRemaining() <= 0 && sub > 2 && keys.add(mid + "|NOSEAT_MANY")) {
                rows.add(new AttentionRow(mid, shortModuleLabel(m),
                        cr.getMoDisplayName(), vacTxt, String.valueOf(sub),
                        "No seats left with multiple pending reviews - check MO decisions",
                        "medium"));
            } else if (m.getStatus() == ModuleStatus.OPEN && cr.getRemaining() <= 0
                    && sub == 0 && total > 0 && keys.add(mid + "|CLOSE_HINT")) {
                rows.add(new AttentionRow(mid, shortModuleLabel(m),
                        cr.getMoDisplayName(), vacTxt, "0",
                        "No vacancies left while status is OPEN - consider closing the posting",
                        "low"));
            }
        }

        return rows;
    }

    private static String shortModuleLabel(ModulePosting m) {
        if (m == null) {
            return "";
        }
        String code = m.getModuleCode() != null ? m.getModuleCode() : "";
        String name = m.getModuleName() != null ? m.getModuleName() : "";
        if (!code.isEmpty()) {
            return code + (name.isEmpty() ? "" : " - " + name);
        }
        return m.getModuleId() != null ? m.getModuleId() : "";
    }

    public static final class DashboardStats {
        private final int moduleCount;
        private final long openModuleCount;
        private final int applicationCount;
        private final int pendingAdjustmentCount;

        DashboardStats(int moduleCount, long openModuleCount, int applicationCount,
                       int pendingAdjustmentCount) {
            this.moduleCount = moduleCount;
            this.openModuleCount = openModuleCount;
            this.applicationCount = applicationCount;
            this.pendingAdjustmentCount = pendingAdjustmentCount;
        }

        public int getModuleCount() {
            return moduleCount;
        }

        public long getOpenModuleCount() {
            return openModuleCount;
        }

        public int getApplicationCount() {
            return applicationCount;
        }

        public int getPendingAdjustmentCount() {
            return pendingAdjustmentCount;
        }
    }

    public static final class AttentionRow {
        private final String moduleId;
        private final String module;
        private final String mo;
        private final String vacancies;
        private final String waitlist;
        private final String issue;
        private final String severity;
        private final boolean reassignmentQueueSummary;

        public AttentionRow(String moduleId, String module, String mo,
                     String vacancies, String waitlist, String issue, String severity) {
            this(moduleId, module, mo, vacancies, waitlist, issue, severity, false);
        }

        public AttentionRow(String moduleId, String module, String mo,
                     String vacancies, String waitlist, String issue,
                     String severity, boolean reassignmentQueueSummary) {
            this.moduleId = moduleId;
            this.module = module;
            this.mo = mo;
            this.vacancies = vacancies;
            this.waitlist = waitlist;
            this.issue = issue;
            this.severity = severity;
            this.reassignmentQueueSummary = reassignmentQueueSummary;
        }

        public String getModuleId() {
            return moduleId;
        }

        public String getModule() {
            return module;
        }

        public String getMo() {
            return mo;
        }

        public String getVacancies() {
            return vacancies;
        }

        public String getWaitlist() {
            return waitlist;
        }

        public String getIssue() {
            return issue;
        }

        public String getSeverity() {
            return severity;
        }

        public boolean isReassignmentQueueSummary() {
            return reassignmentQueueSummary;
        }
    }
}
