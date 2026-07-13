package org.noise_planet.noisecapture.routesilenciosa;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable sound classification generated from an in-memory audio window.
 * No raw audio is stored in this object.
 */
public final class SoundClassification {
    private final int recordId;
    private final long startUtc;
    private final int durationMs;
    private final String rawLabel;
    private final String category;
    private final float confidence;
    private final String modelName;
    private final String modelVersion;
    private final boolean userValidated;

    public SoundClassification(int recordId, long startUtc, int durationMs, String rawLabel,
                               String category, float confidence, String modelName,
                               String modelVersion, boolean userValidated) {
        this.recordId = recordId;
        this.startUtc = startUtc;
        this.durationMs = durationMs;
        this.rawLabel = rawLabel == null ? "" : rawLabel;
        this.category = category == null ? "no_identificado" : category;
        this.confidence = Math.max(0f, Math.min(1f, confidence));
        this.modelName = modelName == null ? "unknown" : modelName;
        this.modelVersion = modelVersion == null ? "unknown" : modelVersion;
        this.userValidated = userValidated;
    }

    public int getRecordId() { return recordId; }
    public long getStartUtc() { return startUtc; }
    public int getDurationMs() { return durationMs; }
    public String getRawLabel() { return rawLabel; }
    public String getCategory() { return category; }
    public float getConfidence() { return confidence; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public boolean isUserValidated() { return userValidated; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("record_id", recordId);
        json.put("start_utc", startUtc);
        json.put("duration_ms", durationMs);
        json.put("raw_label", rawLabel);
        json.put("category", category);
        json.put("confidence", confidence);
        json.put("model_name", modelName);
        json.put("model_version", modelVersion);
        json.put("user_validated", userValidated);
        json.put("audio_stored", false);
        return json;
    }

    public static SoundClassification fromJson(JSONObject json) throws JSONException {
        return new SoundClassification(
                json.getInt("record_id"),
                json.getLong("start_utc"),
                json.optInt("duration_ms", 1000),
                json.optString("raw_label", ""),
                json.optString("category", "no_identificado"),
                (float) json.optDouble("confidence", 0d),
                json.optString("model_name", "unknown"),
                json.optString("model_version", "unknown"),
                json.optBoolean("user_validated", false));
    }
}
