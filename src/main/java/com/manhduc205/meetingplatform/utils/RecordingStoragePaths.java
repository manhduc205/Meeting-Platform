package com.manhduc205.meetingplatform.utils;

import java.util.regex.Pattern;

/**
 * Canonical object-key layout inside the configured MinIO bucket.
 * The future Python worker must use these paths rather than placing files at
 * the bucket root.
 */
public final class RecordingStoragePaths {
    private static final Pattern SAFE_PREFIX = Pattern.compile(
            "^recordings/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private RecordingStoragePaths() {
    }

    public static String newRecordingPrefix(String identifier) {
        return "recordings/" + identifier;
    }

    public static String videoSource(String prefix) {
        return prefix + "/video/source.mp4";
    }

    public static String thumbnail(String prefix) {
        return prefix + "/thumbnail/cover.jpg";
    }

    public static String rawTranscript(String prefix, int version) {
        return prefix + "/transcript/raw/v" + version + "/transcript.json";
    }

    public static String caption(String prefix, String language, int version) {
        return prefix + "/transcript/captions/" + language + "/v" + version + "/transcript.vtt";
    }

    public static String summary(String prefix, String language, int version) {
        return prefix + "/ai/summary/" + language + "/v" + version + "/summary.json";
    }

    public static boolean isSafeRecordingPrefix(String prefix) {
        return prefix != null && SAFE_PREFIX.matcher(prefix.trim()).matches();
    }
}
