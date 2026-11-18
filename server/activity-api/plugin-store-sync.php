<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-store.php';
require __DIR__ . '/plugin-store-providers.php';

$config = appConfig();
$pdo = db();
$provider = $argv[1] ?? '';
if (!in_array($provider, ['ton', 'stars'], true)) throw new InvalidArgumentException('Usage: plugin-store-sync.php ton|stars');
if (!storeRow($pdo, 'SELECT GET_LOCK(?,0) AS locked', ['bygram-plugin-store-' . $provider])['locked']) exit;
if ($provider === 'ton') {
    // shortcut: a seven-day reconciliation window, add a durable replay queue before high sales volume
    $streams = $pdo->prepare("SELECT DISTINCT merchant FROM plugin_store_orders WHERE method='ton' AND created_at>=?");
    $streams->execute([time()-7*86400]);
    $failed = false;
    foreach ($streams->fetchAll(PDO::FETCH_COLUMN) as $merchant) {
        $recipient = storeTonRecipient($merchant);
        if ($recipient === null) continue;
        try {
            if (!storeTonHeaders($config,$recipient[0])) usleep(1100000);
            storeSyncTonMerchant($pdo,$merchant,$config,5);
        } catch (Throwable $error) {
            $failed = true;
            fwrite(STDERR, "TON reconciliation failed for a receiving stream: " . get_class($error) . "\n");
        }
    }
    if ($failed) exit(1);
} else {
    // shortcut: replay the bot ledger during pilot, add a rolling checkpoint before high sales volume
    for ($offset = 0; ; $offset += 100) {
        $result = storeBot($config, 'getStarTransactions', ['offset'=>$offset,'limit'=>100]);
        foreach ($result['transactions'] as $payment) {
            $partner = $payment['source'] ?? $payment['receiver'] ?? [];
            $id = storeOrderPayload($partner['invoice_payload'] ?? null);
            if ($id === null || ($partner['type'] ?? '') !== 'user' || (int) ($payment['nanostar_amount'] ?? 0) !== 0) continue;
            storeSettle($pdo, $id, 'stars', (string) $payment['id'], (string) $partner['user']['id'],
                abs((int) $payment['amount']), (int) $payment['date'], storeMerchant($config, 'stars'), isset($payment['receiver']));
        }
        if (count($result['transactions']) < 100) break;
    }
}
