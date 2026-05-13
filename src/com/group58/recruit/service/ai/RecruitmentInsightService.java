package com.group58.recruit.service.ai;

import com.group58.recruit.config.AiConfig;
import com.group58.recruit.model.ApplicationStatus;
import com.group58.recruit.model.ModulePosting;
import com.group58.recruit.model.RecruitmentApplication;
import com.group58.recruit.model.TAProfile;
import com.group58.recruit.repository.ModulePostingRepository;
import com.group58.recruit.repository.RecruitmentApplicationRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Calls the remote chat model once per {@link #analyze(ModulePosting, TAProfile)} request.
 */
public final class RecruitmentInsightService {

    private final AiConfig aiConfig;
    private final RemoteRecruitmentInsightClient remoteClient;
    private final RecruitmentApplicationRepository applicationRepo = new RecruitmentApplicationRepository();
    private final ModulePostingRepository moduleRepo = new ModulePostingRepository();

    public RecruitmentInsightService() {
        this(AiConfig.load());
    }

    public RecruitmentInsightService(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.remoteClient = new RemoteRecruitmentInsightClient(aiConfig);
    }

    public RecruitmentInsightResult analyze(ModulePosting module, TAProfile profile) {
        if (!aiConfig.remoteEnabled()) {
            return new RecruitmentInsightResult(
                    0,
                    List.of(),
                    List.of(),
                    "Configure AI_API_URL and AI_API_KEY in config.properties at the project root (or set the same as environment variables).",
                    List.of(),
                    "",
                    RecruitmentInsightResult.Source.ERROR_NO_CONFIG,
                    "未配置 API"
            );
        }

        String workloadFacts = buildPromptWorkloadFacts(module, profile);
        String userBlock = buildUserPrompt(module, profile, workloadFacts);
        try {
            Optional<RemoteRecruitmentInsightClient.RemoteInsightPayload> opt = remoteClient.complete(userBlock);
            if (opt.isEmpty()) {
                return remoteError("Empty response from API.");
            }
            RemoteRecruitmentInsightClient.RemoteInsightPayload p = opt.get();
            int score = p.clampedMatchScore();
            if (score < 0) {
                return remoteError("Model did not return a valid matchScore.");
            }
            return new RecruitmentInsightResult(
                    score,
                    p.getMatchedSkills(),
                    p.getMissingHints(),
                    p.getRationale(),
                    p.getSuggestedSkillsToAdd(),
                    p.getWorkloadNote(),
                    RecruitmentInsightResult.Source.REMOTE,
                    "在线 AI"
            );
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return remoteError(msg);
        }
    }

    private static RecruitmentInsightResult remoteError(String message) {
        return new RecruitmentInsightResult(
                0,
                List.of(),
                List.of(),
                message,
                List.of(),
                "",
                RecruitmentInsightResult.Source.ERROR_REMOTE,
                "API 调用失败"
        );
    }

    /** Factual workload lines for the model (parsed hours from JSON data, not keyword matching). */
    private String buildPromptWorkloadFacts(ModulePosting module, TAProfile profile) {
        String taId = profile != null ? profile.getQmId() : "";
        String moduleId = module != null ? module.getModuleId() : "";
        StringBuilder sb = new StringBuilder();
        OptionalDouble thisH = module != null ? WorkloadTextParser.parseWeeklyHours(module.getWorkload()) : OptionalDouble.empty();
        double other = sumAcceptedWeeklyHoursForTaExcluding(taId, moduleId);
        if (thisH.isPresent()) {
            sb.append(String.format(Locale.ROOT, "This posting workload text parses to about %.1f hours/week.", thisH.getAsDouble()));
        } else {
            sb.append("Could not parse weekly hours from this posting's workload text.");
        }
        sb.append(' ');
        sb.append(String.format(Locale.ROOT, "This TA has about %.1f hours/week from other ACCEPTED module postings (parsed from their workload text).", other));
        return sb.toString();
    }

    private String buildUserPrompt(ModulePosting module, TAProfile profile, String workloadFacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Assess this TA for this single module posting. Return only the JSON object described in the system message.\n\n");
        if (module != null) {
            sb.append("moduleCode: ").append(nullToEmpty(module.getModuleCode())).append('\n');
            sb.append("moduleName: ").append(nullToEmpty(module.getModuleName())).append('\n');
            sb.append("workloadText: ").append(nullToEmpty(module.getWorkload())).append('\n');
            sb.append("requirements: ").append(nullToEmpty(module.getRequirements())).append('\n');
            sb.append("description: ").append(nullToEmpty(module.getDescription())).append('\n');
        }
        if (profile != null) {
            sb.append("taQmId: ").append(nullToEmpty(profile.getQmId())).append('\n');
            sb.append("taName: ").append(nullToEmpty(profile.getName())).append('\n');
            String skills = profile.getSkills() == null ? "" : profile.getSkills().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(", "));
            sb.append("taSkills: ").append(skills).append('\n');
        }
        sb.append("workloadFacts: ").append(workloadFacts).append('\n');
        sb.append('\n');
        sb.append("Checklist: (1) Map taSkills to requirements and description. (2) Set matchScore using the rubric in the system message. "
                + "(3) matchedSkills must be grounded in taSkills. (4) missingHints = largest unmet requirements. "
                + "(5) suggestedSkillsToAdd = upskilling gaps for this module, not a repeat of matchedSkills. "
                + "(6) workloadNote must reflect workloadFacts.\n");
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public double sumAcceptedWeeklyHoursForTaExcluding(String taQmId, String excludeModuleId) {
        if (taQmId == null || taQmId.isBlank()) {
            return 0;
        }
        List<ModulePosting> modules = moduleRepo.findAll();
        Map<String, ModulePosting> byId = modules.stream()
                .filter(m -> m.getModuleId() != null)
                .collect(Collectors.toMap(ModulePosting::getModuleId, m -> m, (a, b) -> a));
        List<RecruitmentApplication> apps = applicationRepo.findAll();
        double sum = 0;
        for (RecruitmentApplication app : apps) {
            if (app == null || app.getStatus() != ApplicationStatus.ACCEPTED) {
                continue;
            }
            if (!taQmId.equals(app.getTaUserId())) {
                continue;
            }
            if (excludeModuleId != null && excludeModuleId.equals(app.getModuleId())) {
                continue;
            }
            ModulePosting m = byId.get(app.getModuleId());
            if (m == null) {
                continue;
            }
            OptionalDouble h = WorkloadTextParser.parseWeeklyHours(m.getWorkload());
            if (h.isPresent()) {
                sum += h.getAsDouble();
            }
        }
        return sum;
    }
}
