/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  NoiseCapture is free software under the GNU General Public License version 3 or later.
 */
package org.noise_planet.noisecapture;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import org.noise_planet.noisecapture.routesilenciosa.RouteSilenciosaUploadClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Communicate with WPS server in order to upload measurements.
 *
 * The optional Ruta Silenciosa upload is intentionally isolated from the official
 * Noise-Planet flow. A failure in the project API never invalidates a successful
 * upload to Noise-Planet.
 */
public class MeasurementUploadWPS {
    private static final String TAG = "MeasurementUploadWPS";
    Activity activity;
    public static final String BASE_URL = "https://onomap-gs.noise-planet.org";

    public MeasurementUploadWPS(Activity activity) {
        this.activity = activity;
    }

    public void uploadRecord(int recordId) throws IOException {
        MeasurementExport measurementExport = new MeasurementExport(activity);
        MeasurementManager measurementManager = new MeasurementManager(activity);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        String serverUrl = sharedPref.getString("settings_onomap_url", BASE_URL);
        URL url = new URL(serverUrl + "/geoserver/wps");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setReadTimeout(15000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        try {
            InputStream inputStream = activity.getResources().openRawResource(R.raw.wps_begin);
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } finally {
                inputStream.close();
            }

            Base64OutputStream base64OutputStream = new Base64OutputStream(
                    os, Base64.NO_CLOSE | Base64.NO_WRAP);
            try {
                measurementExport.exportRecord(recordId, base64OutputStream, false);
            } finally {
                base64OutputStream.close();
            }

            inputStream = activity.getResources().openRawResource(R.raw.wps_end);
            try {
                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } finally {
                inputStream.close();
            }
        } finally {
            os.close();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpsURLConnection.HTTP_OK) {
            String line;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder uuid = new StringBuilder();
            while ((line = br.readLine()) != null) {
                uuid.append(line);
            }
            Pattern pattern = Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            Matcher matcher = pattern.matcher(uuid.toString());
            if (matcher.matches()) {
                measurementManager.updateRecordUUID(recordId, uuid.toString());
                uploadToRouteSilenciosaBestEffort(recordId);
            } else {
                throw new IOException("Illegal track UUID :" + uuid);
            }
        } else {
            throw new IOException("Failed to transfer measurement "
                    + conn.getResponseMessage() + " [code:" + responseCode + "]");
        }
    }

    private void uploadToRouteSilenciosaBestEffort(int recordId) {
        try {
            RouteSilenciosaUploadClient client = new RouteSilenciosaUploadClient(activity);
            if (client.isEnabled()) {
                client.uploadRecord(recordId);
            }
        } catch (IOException | RuntimeException ex) {
            // Keep the official upload successful even if the project API is unavailable.
            Log.e(TAG, "Ruta Silenciosa upload failed", ex);
        }
    }
}
