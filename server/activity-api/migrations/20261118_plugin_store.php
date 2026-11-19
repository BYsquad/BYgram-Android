<?php
declare(strict_types=1);

$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_products (
    plugin_id VARCHAR(120) PRIMARY KEY,
    author_id BIGINT NOT NULL,
    author_key CHAR(64) NOT NULL,
    current_hash CHAR(64) NULL,
    listed BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_releases (
    sha256 CHAR(64) PRIMARY KEY,
    plugin_id VARCHAR(120) NOT NULL,
    version VARCHAR(80) NOT NULL,
    candidate MEDIUMTEXT NOT NULL,
    archive MEDIUMBLOB NOT NULL,
    stars INT UNSIGNED NOT NULL,
    nano BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'review',
    reviewed_by BIGINT NULL,
    review_note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    UNIQUE KEY release_version (plugin_id, version),
    KEY review_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_orders (
    id CHAR(32) PRIMARY KEY,
    buyer BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    plugin_id VARCHAR(120) NOT NULL,
    release_hash CHAR(64) NOT NULL,
    method VARCHAR(8) NOT NULL,
    merchant VARCHAR(128) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    author_bps SMALLINT UNSIGNED NOT NULL,
    terms_version VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    checkout_id VARCHAR(128) NULL,
    checkout_url VARCHAR(512) NULL,
    client_key CHAR(32) NOT NULL,
    UNIQUE KEY buyer_retry (buyer, client_key),
    KEY buyer_product (buyer, plugin_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_payments (
    method VARCHAR(8) NOT NULL,
    merchant VARCHAR(128) NOT NULL,
    charge VARCHAR(128) NOT NULL,
    order_id CHAR(32) NULL,
    amount BIGINT UNSIGNED NOT NULL,
    author_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
    payer VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    refund_reference VARCHAR(128) NULL,
    paid_at BIGINT NOT NULL,
    PRIMARY KEY (method, merchant, charge),
    KEY order_payment (order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_entitlements (
    buyer BIGINT NOT NULL,
    plugin_id VARCHAR(120) NOT NULL,
    order_id CHAR(32) NOT NULL,
    PRIMARY KEY (buyer, plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_cursor (
    stream VARCHAR(128) PRIMARY KEY,
    logical_time VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
$pdo->exec("CREATE TABLE IF NOT EXISTS plugin_store_payouts (
    author BIGINT NOT NULL,
    method VARCHAR(8) NOT NULL,
    merchant VARCHAR(128) NOT NULL,
    reference VARCHAR(128) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    paid_at BIGINT NOT NULL,
    PRIMARY KEY (method, merchant, reference),
    KEY author_payout (author, method, merchant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
