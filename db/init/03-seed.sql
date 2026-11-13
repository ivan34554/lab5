TRUNCATE users, podcasts, episodes, audio_files, publication_slots RESTART IDENTITY CASCADE;

INSERT INTO users (username, email, password_hash, role, status) VALUES
    ('admin',     'admin@example.com',     '$2b$10$6ghivGomsBib8TiNa7SNL.VR1CBXBocIVPYm7n486WpLYWg/PzrlO', 'ADMIN',     'ACTIVE'),
    ('moderator', 'moderator@example.com', '$2b$10$syRLSJKpKg1KRsFCDBCchOp8ePZLheiSadYtwDUchdu93a5F5NHM.', 'MODERATOR', 'ACTIVE'),
    ('ivan',      'ivan@example.com',      '$2b$10$OD/IXvOwC9LCr/m4lhUkWurwG0tQ2nVcpxNOJoF8S8wzYEOHF59X6', 'USER',      'ACTIVE'),
    ('anna',      'anna@example.com',      '$2b$10$OHmuupjyKdh7KN3sBZeVxes8jKnYfQq1AwZ7ZFuiSx4cgUb/jVj4e', 'USER',      'ACTIVE'),
    ('petr',      'petr@example.com',      '$2b$10$.7Sc9OqwISjfXfcYaLTwkuBbeT3bhu8BGt05Qu4YbgV.ItWWKOLCy', 'USER',      'BLOCKED');

INSERT INTO podcasts (title, author_id, status) VALUES
    ('Безопасный код',          3, 'ACTIVE'),
    ('Криптография на пальцах', 4, 'ACTIVE'),
    ('Архив радио',             5, 'ARCHIVED');

INSERT INTO episodes (podcast_id, title, duration_seconds, state) VALUES
    (1, 'Выпуск 1. Введение в РБПО',  1800, 'PUBLISHED'),
    (1, 'Выпуск 2. Внедрение SQL',    2400, 'SCHEDULED'),
    (1, 'Выпуск 3. Секреты и env',    1500, 'DRAFT'),
    (1, 'Выпуск 4. Роли в БД',        1200, 'DRAFT'),
    (2, 'Что такое хеш',              1320, 'DRAFT'),
    (2, 'Цифровая подпись',           1680, 'DRAFT'),
    (3, 'Первый эфир',                 900, 'PUBLISHED');

INSERT INTO audio_files (episode_id, path, format, size_bytes, is_main) VALUES
    (1, 'audio/podcast1/ep1.mp3',      'MP3',  28311552, TRUE),
    (2, 'audio/podcast1/ep2.mp3',      'MP3',  38400000, TRUE),
    (3, 'audio/podcast1/ep3.mp3',      'MP3',  24000000, FALSE),
    (3, 'audio/podcast1/ep3-hq.flac',  'FLAC', 98000000, FALSE),
    (5, 'audio/podcast2/hash.mp3',     'MP3',  21120000, TRUE),
    (7, 'audio/archive/first.ogg',     'OGG',  14400000, TRUE);

INSERT INTO publication_slots (podcast_id, episode_id, scheduled_at, status, published_at) VALUES
    (1, 1, now() - interval '10 days', 'PUBLISHED', now() - interval '10 days'),
    (1, 2, now() + interval '30 days', 'PLANNED',   NULL),
    (2, 6, now() + interval '5 days',  'CANCELLED', NULL),
    (3, 7, now() - interval '400 days','PUBLISHED', now() - interval '400 days');
