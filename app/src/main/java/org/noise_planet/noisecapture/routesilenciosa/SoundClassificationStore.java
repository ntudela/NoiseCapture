package org.noise_planet.noisecapture.routesilenciosa;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSONL sidecar store for AI classifications.
 * It deliberately avoids changing NoiseCapture's existing SQLite schema.
 */
public final class SoundClassificationStore {
    private static final String DIRECTORY = "route_silenciosa";
    private static final String FILE_NAME = "sound_classifications.jsonl";

    private final File file;

    public SoundClassificationStore(Context context) {
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create classification storage directory");
        }
        this.file = new File(directory, FILE_NAME);
    }

    public synchronized void append(SoundClassification classification) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            try {
                writer.write(classification.toJson().toString());
                writer.newLine();
            } catch (JSONException ex) {
                throw new IOException("Unable to serialize sound classification", ex);
            }
        }
    }

    public synchronized List<SoundClassification> getForRecord(int recordId) throws IOException {
        List<SoundClassification> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    SoundClassification classification =
                            SoundClassification.fromJson(new JSONObject(line));
                    if (classification.getRecordId() == recordId) {
                        result.add(classification);
                    }
                } catch (JSONException ignored) {
                    // Skip malformed lines without blocking the measurement workflow.
                }
            }
        }
        return result;
    }

    public synchronized void deleteForRecord(int recordId) throws IOException {
        if (!file.exists()) {
            return;
        }
        File temporary = new File(file.getParentFile(), FILE_NAME + ".tmp");
        try (BufferedReader reader = new BufferedReader(new FileReader(file));
             BufferedWriter writer = new BufferedWriter(new FileWriter(temporary))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);
                    if (json.optInt("record_id", -1) != recordId) {
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (JSONException ignored) {
                    // Drop malformed entries during compaction.
                }
            }
        }
        if (!file.delete() || !temporary.renameTo(file)) {
            throw new IOException("Unable to compact classification store");
        }
    }
}
