package com.group58.recruit.service.ai;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort parse of strings like {@code 8 hours/week} into a weekly hour estimate. */
public final class WorkloadTextParser {

    private static final Pattern HOURS_PER_WEEK = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|h)\\s*/\\s*week",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern H_PER_W = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*h\\s*/\\s*w",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PER_WEEK = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?|h)\\s+per\\s+week",
            Pattern.CASE_INSENSITIVE);

    private WorkloadTextParser() {
    }

    public static OptionalDouble parseWeeklyHours(String workload) {
        if (workload == null || workload.isBlank()) {
            return OptionalDouble.empty();
        }
        OptionalDouble v = tryPattern(HOURS_PER_WEEK, workload);
        if (v.isPresent()) {
            return v;
        }
        v = tryPattern(H_PER_W, workload);
        if (v.isPresent()) {
            return v;
        }
        return tryPattern(PER_WEEK, workload);
    }

    private static OptionalDouble tryPattern(Pattern p, String workload) {
        Matcher m = p.matcher(workload);
        if (m.find()) {
            return OptionalDouble.of(Double.parseDouble(m.group(1)));
        }
        return OptionalDouble.empty();
    }
}
