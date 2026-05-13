package com.group58.recruit.service.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group58.recruit.config.AiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OpenAI-compatible chat completions: model returns one JSON object in {@code message.content}.
 */
public final class RemoteRecruitmentInsightClient {

    private static final String SYSTEM = ""
            + "You are an assistant for BUPT International School TA recruitment. "
            + "You receive exactly one module posting (fields below in the user message) and one TA (taQmId, taName, taSkills). "
            + "workloadFacts are numeric summaries parsed from workload text elsewhere—treat them as factual constraints, not as skill evidence. "
            + "Use ONLY the supplied text; do not invent CV details, grades, past employment, or school policies. "
            + "\n\n"
            + "Reply with ONE JSON object only (no markdown fences). Keys and types: "
            + "matchScore (integer 0-100), "
            + "rationale (string, at most 3 sentences, plain English), "
            + "matchedSkills (array of strings: items taken from taSkills that clearly help this module; empty if none), "
            + "missingHints (array of strings: concrete gaps vs requirements/description, max 12 short phrases), "
            + "suggestedSkillsToAdd (array of strings: skills the TA could develop to serve THIS module better, max 8; "
            + "must NOT duplicate strengths already reflected in matchedSkills), "
            + "workloadNote (one sentence; must incorporate workloadFacts when they mention hours). "
            + "\n\n"
            + "matchScore rubric—use the full range deliberately: "
            + "85-100 strong match (most key requirements covered by taSkills). "
            + "70-84 good match (clear overlap; gaps are minor). "
            + "60-69 acceptable partial match (several relevant skills; some important gaps remain). "
            + "45-59 weak match (limited overlap or serious gaps). "
            + "0-44 poor match OR taSkills empty / gibberish / unrelated to the posting so a defensible fit cannot be claimed. "
            + "When taSkills lists at least two coherent technical or teaching-related skills that genuinely relate to "
            + "requirements or description, do not place matchScore below 60 unless there is a hard conflict "
            + "(e.g. required language or core stack entirely absent). "
            + "When overlap is partial but real, prefer 60-79 rather than very low scores. "
            + "\n\n"
            + "Consistency: rationale must agree with matchScore; missingHints should align with score (fewer critical gaps when score is high). "
            + "If matchScore is below 60, suggestedSkillsToAdd should emphasize bridging gaps, not generic restatements of module titles.";

    private final AiConfig config;
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    public RemoteRecruitmentInsightClient(AiConfig config) {
        this.config = config;
    }

    public Optional<RemoteInsightPayload> complete(String userBlock) throws IOException, InterruptedException {
        if (!config.remoteEnabled()) {
            return Optional.empty();
        }
        String url = config.apiUrl().orElseThrow();
        String key = config.apiKey().orElseThrow();

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.2);
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", SYSTEM);
        messages.add(sys);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", userBlock);
        messages.add(usr);
        body.add("messages", messages);

        String json = gson.toJson(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + truncate(response.body(), 400));
        }
        String content = extractAssistantContent(response.body());
        if (content == null || content.isBlank()) {
            throw new IOException("Empty assistant content");
        }
        content = stripJsonFence(content);
        JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
        return Optional.of(RemoteInsightPayload.fromJson(obj));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String extractAssistantContent(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            return null;
        }
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject message = first.getAsJsonObject("message");
        if (message == null) {
            return null;
        }
        JsonElement c = message.get("content");
        return c != null && c.isJsonPrimitive() ? c.getAsString() : null;
    }

    private static String stripJsonFence(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1).trim();
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    public static final class RemoteInsightPayload {
        private final int matchScore;
        private final String rationale;
        private final List<String> matchedSkills;
        private final List<String> missingHints;
        private final List<String> suggestedSkillsToAdd;
        private final String workloadNote;

        public RemoteInsightPayload(
                int matchScore,
                String rationale,
                List<String> matchedSkills,
                List<String> missingHints,
                List<String> suggestedSkillsToAdd,
                String workloadNote
        ) {
            this.matchScore = matchScore;
            this.rationale = rationale != null ? rationale : "";
            this.matchedSkills = List.copyOf(matchedSkills);
            this.missingHints = List.copyOf(missingHints);
            this.suggestedSkillsToAdd = List.copyOf(suggestedSkillsToAdd);
            this.workloadNote = workloadNote != null ? workloadNote : "";
        }

        static RemoteInsightPayload fromJson(JsonObject obj) {
            int score = readInt(obj, "matchScore", -1);
            String rationale = readString(obj, "rationale");
            String workloadNote = readString(obj, "workloadNote");
            List<String> matched = readStringArray(obj, "matchedSkills", 24);
            List<String> missing = readStringArray(obj, "missingHints", 16);
            List<String> suggested = readStringArray(obj, "suggestedSkillsToAdd", 12);
            return new RemoteInsightPayload(score, rationale, matched, missing, suggested, workloadNote);
        }

        private static List<String> readStringArray(JsonObject o, String key, int max) {
            List<String> out = new ArrayList<>();
            JsonElement arr = o.get(key);
            if (arr == null || !arr.isJsonArray()) {
                return out;
            }
            for (JsonElement e : arr.getAsJsonArray()) {
                if (e != null && e.isJsonPrimitive()) {
                    String v = e.getAsString().trim();
                    if (!v.isEmpty()) {
                        out.add(v);
                    }
                    if (out.size() >= max) {
                        break;
                    }
                }
            }
            return out;
        }

        private static int readInt(JsonObject o, String key, int defaultVal) {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonPrimitive()) {
                return defaultVal;
            }
            try {
                return e.getAsInt();
            } catch (NumberFormatException ex) {
                try {
                    return (int) Math.round(Double.parseDouble(e.getAsString()));
                } catch (NumberFormatException ex2) {
                    return defaultVal;
                }
            }
        }

        private static String readString(JsonObject o, String key) {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) {
                return "";
            }
            return e.getAsString().trim();
        }

        public int getMatchScore() {
            return matchScore;
        }

        public String getRationale() {
            return rationale;
        }

        public List<String> getMatchedSkills() {
            return matchedSkills;
        }

        public List<String> getMissingHints() {
            return missingHints;
        }

        public List<String> getSuggestedSkillsToAdd() {
            return suggestedSkillsToAdd;
        }

        public String getWorkloadNote() {
            return workloadNote;
        }

        public int clampedMatchScore() {
            if (matchScore < 0) {
                return -1;
            }
            return Math.max(0, Math.min(100, matchScore));
        }
    }
}
