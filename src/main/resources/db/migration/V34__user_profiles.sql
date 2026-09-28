-- =============================================================================================
-- User profiles: the signed-in user's own contact details and photo.
--
-- The photo is kept optimized, never as uploaded: ProfilePhotoProcessor crops it square, scales it
-- to 512 px and a 96 px thumbnail, and re-encodes both as JPEG - which also drops whatever the
-- camera wrote into the file (location, device). A profile photo is a few tens of kilobytes, so it
-- lives in the database beside the user and needs no file store; photo_version on the user changes
-- with every new photo, so the browser may cache each one for a year.
-- =============================================================================================

ALTER TABLE sec_fabric_users
    ADD COLUMN email          VARCHAR(150),
    ADD COLUMN phone          VARCHAR(40),
    ADD COLUMN designation    VARCHAR(100),
    ADD COLUMN department     VARCHAR(100),
    ADD COLUMN bio            VARCHAR(500),
    -- Set when a photo is stored (epoch milliseconds), null without one: part of the photo's URL.
    ADD COLUMN photo_version  BIGINT;

ALTER TABLE sec_fabric_users
    ADD CONSTRAINT ck_fab_user_email CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$');

CREATE TABLE sec_user_photos (
    user_id         BIGINT       PRIMARY KEY,
    content_type    VARCHAR(30)  NOT NULL,
    image           BYTEA        NOT NULL,
    thumbnail       BYTEA        NOT NULL,
    width           INTEGER      NOT NULL,
    height          INTEGER      NOT NULL,
    original_bytes  INTEGER      NOT NULL,
    stored_bytes    INTEGER      NOT NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_photo_user FOREIGN KEY (user_id) REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_photo_type CHECK (content_type = 'image/jpeg'),
    CONSTRAINT ck_user_photo_size CHECK (width BETWEEN 1 AND 2048 AND height BETWEEN 1 AND 2048
        AND stored_bytes > 0 AND stored_bytes <= 1048576)
);
