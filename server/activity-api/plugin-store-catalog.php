<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-store.php';

$config = appConfig();
header('Cache-Control: no-store');
if (($config['plugin_store_enabled'] ?? false) !== true) jsonResponse(['format'=>2,'plugins'=>[],'revoked'=>[]]);
try {
    $pdo = db();
    $revoked = $pdo->query("SELECT sha256 FROM plugin_store_releases WHERE status='revoked' ORDER BY sha256")->fetchAll(PDO::FETCH_COLUMN);
    jsonResponse(['format'=>2,'plugins'=>storeCatalog($pdo, storeMethods($config), storeConfiguredTonNetwork($config)), 'revoked'=>$revoked,
        'terms'=>PLUGIN_STORE_TERMS, 'notice'=>PLUGIN_STORE_NOTICE,
        'stars_test'=>(bool) ($config['plugin_store_stars_test'] ?? true), 'ton_test'=>(bool) ($config['plugin_store_ton_test'] ?? true)]);
} catch (Throwable $error) {
    error_log('BYgram paid catalog: ' . get_class($error));
    jsonResponse(['ok'=>false], 503);
}
