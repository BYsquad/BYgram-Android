<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-store.php';
require __DIR__ . '/plugin-store-providers.php';

$config = appConfig();
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') { http_response_code(403); exit; }
$merchant = $_GET['merchant'] ?? storeMerchant($config, 'stars');
if (!is_string($merchant) || strlen($merchant) > 128) { http_response_code(403); exit; }
if ($merchant === storeMerchant($config, 'stars')) {
    $secret = (string) ($config['plugin_store_stars_secret'] ?? '');
} else {
    $row = storeRow(db(), 'SELECT * FROM plugin_store_author_bots WHERE merchant=?', [$merchant]);
    $secret = $row['webhook_secret'] ?? '';
}
if ($secret === '' || !hash_equals($secret, (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? ''))) {
    http_response_code(403);
    exit;
}
try {
    if (isset($row) && $row) $config = storeAuthorBotConfig($config, $row);
    $raw = (string) file_get_contents('php://input', false, null, 0, 65537);
    if (strlen($raw) > 65536) { http_response_code(413); exit; }
    $update = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
    storeStarsUpdate(db(), $update, static fn($method, $data) => storeBot($config, $method, $data),
        ($config['terms_url'] ?? '') . "\n" . ($config['support_url'] ?? ''), $merchant);
    http_response_code(204);
} catch (Throwable $error) {
    error_log('BYgram merchant webhook: ' . get_class($error));
    http_response_code(503);
}
