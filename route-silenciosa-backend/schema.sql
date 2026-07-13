CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    device_measurement_id TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
    laeq_mean REAL,
    calibration_gain REAL,
    calibration_method TEXT,
    pleasantness SMALLINT,
    description TEXT,
    microphone_identifier TEXT,
    microphone_settings JSONB,
    audio_stored BOOLEAN NOT NULL DEFAULT FALSE,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS measurement_points (
    id BIGSERIAL PRIMARY KEY,
    measurement_id UUID NOT NULL REFERENCES measurements(id) ON DELETE CASCADE,
    measured_at TIMESTAMPTZ NOT NULL,
    geom GEOGRAPHY(POINT, 4326),
    accuracy_m REAL,
    speed_mps REAL,
    bearing_deg REAL,
    laeq REAL,
    spectrum JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS measurement_points_geom_idx
    ON measurement_points USING GIST (geom);
CREATE INDEX IF NOT EXISTS measurement_points_time_idx
    ON measurement_points (measured_at);

CREATE TABLE IF NOT EXISTS sound_classifications (
    id BIGSERIAL PRIMARY KEY,
    measurement_id UUID NOT NULL REFERENCES measurements(id) ON DELETE CASCADE,
    start_utc BIGINT NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms > 0),
    raw_label TEXT,
    category TEXT NOT NULL,
    confidence REAL NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    model_name TEXT NOT NULL,
    model_version TEXT NOT NULL,
    user_validated BOOLEAN NOT NULL DEFAULT FALSE,
    audio_stored BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS sound_classifications_measurement_idx
    ON sound_classifications (measurement_id, start_utc);
CREATE INDEX IF NOT EXISTS sound_classifications_category_idx
    ON sound_classifications (category);

CREATE TABLE IF NOT EXISTS measurement_tags (
    measurement_id UUID NOT NULL REFERENCES measurements(id) ON DELETE CASCADE,
    tag TEXT NOT NULL,
    PRIMARY KEY (measurement_id, tag)
);
