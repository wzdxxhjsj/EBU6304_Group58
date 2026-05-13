package com.group58.recruit.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads optional AI HTTP settings from environment variables (highest priority) or {@code config.properties}
 * in the JVM working directory (typically project root).
 */
public final class AiConfig {

    private static final String FILE_NAME = "config.properties";

    private final Optional<String> apiUrl;
    private final Optional<String> apiKey;
    private final String model;

    private AiConfig(Optional<String> apiUrl, Optional<String> apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public static AiConfig load() {
        Properties props = new Properties();
        Path path = Paths.get(System.getProperty("user.dir"), FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException ignored) {
                // keep empty props
            }
        }

        String url = firstNonBlank(System.getenv("AI_API_URL"), props.getProperty("AI_API_URL"));
        String key = firstNonBlank(System.getenv("AI_API_KEY"), props.getProperty("AI_API_KEY"));
        String model = firstNonBlank(System.getenv("AI_MODEL"), props.getProperty("AI_MODEL"));
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }
        return new AiConfig(Optional.ofNullable(url).filter(s -> !s.isBlank()),
                Optional.ofNullable(key).filter(s -> !s.isBlank()),
                model.trim());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    /** True when both URL and key are present so a remote completion may be attempted. */
    public boolean remoteEnabled() {
        return apiUrl.isPresent() && apiKey.isPresent();
    }

    public Optional<String> apiUrl() {
        return apiUrl;
    }

    public Optional<String> apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public String describeForLog() {
        return "AiConfig[remote=" + remoteEnabled() + ", model=" + model + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiConfig)) {
            return false;
        }
        AiConfig aiConfig = (AiConfig) o;
        return Objects.equals(apiUrl, aiConfig.apiUrl) && Objects.equals(apiKey, aiConfig.apiKey)
                && Objects.equals(model, aiConfig.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiUrl, apiKey, model);
    }

    @Override
    public String toString() {
        return describeForLog().toLowerCase(Locale.ROOT);
    }
}
