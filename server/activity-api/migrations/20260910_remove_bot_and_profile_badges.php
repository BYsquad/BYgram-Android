<?php
declare(strict_types=1);

function columnExists(PDO $pdo, string $table, string $column): bool {
    $query = $pdo->prepare('SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?');
    $query->execute([$table, $column]);
    return (bool) $query->fetchColumn();
}

$pdo->exec("CREATE TABLE IF NOT EXISTS users (telegram_id BIGINT UNSIGNED PRIMARY KEY, username VARCHAR(64) NULL, first_name VARCHAR(128) NULL, last_name VARCHAR(128) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE IF NOT EXISTS admins (telegram_id BIGINT UNSIGNED PRIMARY KEY, added_by BIGINT UNSIGNED NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
if ($pdo->query("SHOW TABLES LIKE 'bot_users'")->fetchColumn()) {
    $pdo->exec('INSERT IGNORE INTO users SELECT telegram_id,username,first_name,last_name,created_at,last_seen_at FROM bot_users');
}
if ($pdo->query("SHOW TABLES LIKE 'bot_admins'")->fetchColumn()) {
    $pdo->exec('INSERT IGNORE INTO admins SELECT telegram_id,added_by,created_at FROM bot_admins');
}
if (!columnExists($pdo, 'auth_challenges', 'token_hint')) {
    $pdo->exec("ALTER TABLE auth_challenges ADD token_hint VARCHAR(12) NULL AFTER token_hash");
    $pdo->exec('UPDATE auth_challenges SET token_hint=LEFT(token_hash,12) WHERE token_hint IS NULL');
    $pdo->exec('ALTER TABLE auth_challenges MODIFY token_hint VARCHAR(12) NOT NULL');
}
if (!columnExists($pdo, 'profile_badges', 'bygram_enabled')) {
    $pdo->exec('ALTER TABLE profile_badges ADD bygram_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER telegram_id');
    if (columnExists($pdo, 'profile_badges', 'enabled')) {
        $pdo->exec('UPDATE profile_badges SET bygram_enabled=enabled');
    }
}
if (!columnExists($pdo, 'profile_badges', 'extera_enabled')) {
    $pdo->exec('ALTER TABLE profile_badges ADD extera_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER bygram_enabled');
}
if (!columnExists($pdo, 'profile_badges', 'primary_badge')) {
    $pdo->exec("ALTER TABLE profile_badges ADD primary_badge ENUM('bygram','extera') NOT NULL DEFAULT 'bygram' AFTER extera_enabled");
}
