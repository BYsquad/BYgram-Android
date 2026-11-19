<?php
declare(strict_types=1);
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_submissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    repository VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    release_tag VARCHAR(80) NOT NULL DEFAULT '',
    candidate MEDIUMTEXT NULL,
    review_note VARCHAR(1000) NOT NULL DEFAULT '',
    reviewed_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    pending_repository VARCHAR(200) GENERATED ALWAYS AS (IF(status IN ('pending','review'),repository,NULL)) STORED,
    UNIQUE KEY plugin_pending (pending_repository),
    INDEX plugin_owner (telegram_id,created_at), INDEX plugin_repository (repository,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
