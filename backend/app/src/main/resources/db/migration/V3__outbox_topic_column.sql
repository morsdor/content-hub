-- Separate Kafka topic from event type name.
-- event_type = logical name (e.g. "VideoUploaded"), topic = Kafka routing key (e.g. "video.uploaded").
-- Backfills existing rows by copying event_type, which was previously used as the topic.
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS topic TEXT;
UPDATE outbox SET topic = event_type WHERE topic IS NULL;
ALTER TABLE outbox ALTER COLUMN topic SET NOT NULL;
