<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS typing_activations (token_hash CHAR(64) PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, public_key TEXT NOT NULL, confirmed_at DATETIME NULL, expires_at DATETIME NOT NULL) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS typing_devices (telegram_id BIGINT UNSIGNED NOT NULL, key_fingerprint CHAR(64) NOT NULL, public_key TEXT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (telegram_id,key_fingerprint)) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS typing_statuses (telegram_id BIGINT UNSIGNED PRIMARY KEY, statuses JSON NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB');
$pdo->exec('CREATE TABLE IF NOT EXISTS typing_nonces (telegram_id BIGINT UNSIGNED NOT NULL, nonce CHAR(36) NOT NULL, expires_at DATETIME NOT NULL, PRIMARY KEY (telegram_id,nonce), INDEX typing_nonces_expiry (expires_at)) ENGINE=InnoDB');
