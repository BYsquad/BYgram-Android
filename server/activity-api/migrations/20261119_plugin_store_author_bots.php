<?php
declare(strict_types=1);

$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_author_bots (
    merchant VARCHAR(128) PRIMARY KEY,
    author_id BIGINT NOT NULL,
    token_encrypted VARCHAR(512) NOT NULL,
    webhook_secret CHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 0,
    INDEX author_bots (author_id,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
