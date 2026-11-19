<?php
declare(strict_types=1);

$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_author_wallets (
    author_id BIGINT NOT NULL,
    network VARCHAR(4) NOT NULL,
    address CHAR(66) NOT NULL,
    verified_at BIGINT NOT NULL,
    PRIMARY KEY (author_id,network)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
