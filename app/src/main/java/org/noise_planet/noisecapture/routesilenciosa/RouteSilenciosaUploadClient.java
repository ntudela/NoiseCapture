package org.noise_planet.noisecapture.routesilenciosa;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.noise_planet.noisecapture.MeasurementExport;
import org.noise_planet.noisecapture.MeasurementManager;
import org.noise_planet.noisecapture.Storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Uploads NoiseCapture measurements and sound classifications to the project's own API.
 * The official Noise-Planet upload remains unchanged and independent.
 */
public final class RouteSilenciosaUploadClient {
    public static final String PREF_ENABLED = "settings_route_silenciosa_enabled";
    public static final String PREF_BASE_URL = "settings_route_silenciosa_url";
    public static final String PREF_API_KEY = "settings_route_silenciosa_api_key";
    public static final String DEFAULT_PATH = "/v1/measurements";

    private final Context context;
    private final MeasurementManager measurementManager;
    private final SoundClassificationStore classificationStore;

    public RouteSilenciosaUploadClient(Context context) {
        this.context = context.getApplicationContext();
        this.measurementManager = new MeasurementManager(this.context);
        this.classificationStore = new SoundClassificationStore(this.context);
    }

    public boolean isEnabled() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(PREF_ENABLED, false)
                && !preferences.getString(PREF_BASE_URL, "").trim().isEmpty();
    }

    public void uploadRecord(int recordId) throws IOException {
        if (!isEnabled()) {
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String baseUrl = preferences.getString(PREF_BASE_URL, "").trim();
        String apiKey = preferences.getString(PREF_API_KEY, "").trim();
        URL url = new URL(normalizeUrl(baseUrl) + DEFAULT_PATH);

        byte[] payload;
        try {
            payload = buildPayload(recordId).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException ex) {
            throw new IOException("Unable to build Ruta Silenciosa payload", ex);
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Client", "NoiseCapture-RutaSilenciosa");
        if (!apiKey.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            StringBuilder error = new StringBuilder();
            if (connection.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line);
                    }
                }
            }
            throw new IOException("Ruta Silenciosa upload failed [" + status + "] " + error);
        }
        connection.disconnect();
    }

    JSONObject buildPayload(int recordId) throws JSONException, IOException {
        Storage.Record record = measurementManager.getRecord(recordId);
        if (record == null) {
            throw new IOException("Measurement record not found: " + recordId);
        }

        JSONObject root = new JSONObject();
        root.put("schema_version", "1.0");
        root.put("source", "NoiseCapture-fork");
        root.put("audio_stored", false);

        JSONObject measurement = new JSONObject();
        measurement.put("local_record_id", record.getId());
        measurement.put("record_utc", record.getUtc());
        measurement.put("duration_seconds", record.getTimeLength());
        measurement.put("laeq_mean", record.getLeqMean());
        measurement.put("calibration_gain", record.getCalibrationGain());
        measurement.put("calibration_method", record.getCalibrationMethod().name());
        measurement.put("pleasantness", record.getPleasantness());
        measurement.put("description", record.getDescription());
        measurement.put("microphone_identifier", record.getMicrophoneDeviceId());
        measurement.put("microphone_settings", record.getMicrophoneDeviceSettings());
        root.put("measurement", measurement);

        JSONArray tags = new JSONArray();
        for (String tag : measurementManager.getTags(recordId)) {
            tags.put(tag);
        }
        root.put("manual_tags", tags);

        List<MeasurementManager.LeqBatch> locations =
                measurementManager.getRecordLocations(recordId, true, 0, null, null);
        JSONObject featureCollection = new JSONObject();
        featureCollection.put("type", "FeatureCollection");
        featureCollection.put("features",
                MeasurementExport.recordsToGeoJSON(locations, false, true));
        root.put("track", featureCollection);

        JSONArray classifications = new JSONArray();
        for (SoundClassification classification : classificationStore.getForRecord(recordId)) {
            classifications.put(classification.toJson());
        }
        root.put("sound_classifications", classifications);
        return root;
    }

    private static String normalizeUrl(String baseUrl) {
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
