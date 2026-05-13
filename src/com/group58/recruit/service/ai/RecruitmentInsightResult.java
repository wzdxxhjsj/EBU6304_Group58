package com.group58.recruit.service.ai;

import java.util.List;
import java.util.Objects;

/** Insight from the remote model API (Admin AI panel). */
public final class RecruitmentInsightResult {

    public enum Source {
        /** Remote model returned JSON successfully. */
        REMOTE,
        /** AI_API_URL / AI_API_KEY not configured. */
        ERROR_NO_CONFIG,
        /** HTTP error, empty response, or invalid JSON from the API. */
        ERROR_REMOTE
    }

    private final int matchScore;
    private final List<String> matchedSkills;
    private final List<String> missingHints;
    private final String rationale;
    private final List<String> suggestedSkillsToAdd;
    private final String workloadNote;
    private final Source source;
    private final String sourceCaption;

    public RecruitmentInsightResult(
            int matchScore,
            List<String> matchedSkills,
            List<String> missingHints,
            String rationale,
            List<String> suggestedSkillsToAdd,
            String workloadNote,
            Source source,
            String sourceCaption
    ) {
        this.matchScore = matchScore;
        this.matchedSkills = List.copyOf(matchedSkills);
        this.missingHints = List.copyOf(missingHints);
        this.rationale = rationale != null ? rationale : "";
        this.suggestedSkillsToAdd = List.copyOf(suggestedSkillsToAdd);
        this.workloadNote = workloadNote != null ? workloadNote : "";
        this.source = source;
        this.sourceCaption = sourceCaption != null ? sourceCaption : "";
    }

    public boolean isSuccess() {
        return source == Source.REMOTE;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public List<String> getMatchedSkills() {
        return matchedSkills;
    }

    public List<String> getMissingHints() {
        return missingHints;
    }

    public String getRationale() {
        return rationale;
    }

    public List<String> getSuggestedSkillsToAdd() {
        return suggestedSkillsToAdd;
    }

    public String getWorkloadNote() {
        return workloadNote;
    }

    public Source getSource() {
        return source;
    }

    public String getSourceCaption() {
        return sourceCaption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecruitmentInsightResult)) {
            return false;
        }
        RecruitmentInsightResult that = (RecruitmentInsightResult) o;
        return matchScore == that.matchScore
                && Objects.equals(matchedSkills, that.matchedSkills)
                && Objects.equals(missingHints, that.missingHints)
                && Objects.equals(rationale, that.rationale)
                && Objects.equals(suggestedSkillsToAdd, that.suggestedSkillsToAdd)
                && Objects.equals(workloadNote, that.workloadNote)
                && source == that.source
                && Objects.equals(sourceCaption, that.sourceCaption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchScore, matchedSkills, missingHints, rationale, suggestedSkillsToAdd, workloadNote,
                source, sourceCaption);
    }
}
