<?php
declare(strict_types=1);

function storeHttp(string $url, ?array $data = null, array $headers = []): array {
    $curl = curl_init($url);
    $raw = '';
    curl_setopt_array($curl, [
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS, CURLOPT_CONNECTTIMEOUT => 2, CURLOPT_TIMEOUT => 5,
        CURLOPT_USERAGENT => 'BYgram-Plugin-Store/1.0',
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_WRITEFUNCTION => static function ($handle, string $chunk) use (&$raw): int {
            if (strlen($raw) + strlen($chunk) > 4 * 1024 * 1024) return 0;
            $raw .= $chunk;
            return strlen($chunk);
        },
    ]);
    if ($data !== null) {
        curl_setopt($curl, CURLOPT_POSTFIELDS, json_encode($data, JSON_THROW_ON_ERROR));
        curl_setopt($curl, CURLOPT_HTTPHEADER, [...$headers, 'Content-Type: application/json']);
    }
    if (!curl_exec($curl) || curl_getinfo($curl, CURLINFO_RESPONSE_CODE) !== 200) throw new RuntimeException('Payment provider unavailable');
    $value = json_decode($raw, true, 64, JSON_THROW_ON_ERROR);
    if (!is_array($value)) throw new RuntimeException('Invalid payment provider response');
    return $value;
}
function storeBot(array $config, string $method, array $data): mixed {
    $token = $config['plugin_store_stars_token'] ?? '';
    if (!preg_match('/^[0-9]+:[A-Za-z0-9_-]+$/D', $token)) throw new RuntimeException('Invalid merchant token');
    $test = ($config['plugin_store_stars_test'] ?? true) ? '/test' : '';
    if ($test === '' && ($config['plugin_store_live'] ?? false) !== true) throw new RuntimeException('Live payments disabled');
    $response = storeHttp("https://api.telegram.org/bot$token$test/$method", $data);
    if (($response['ok'] ?? false) !== true) throw new RuntimeException('Merchant bot request failed');
    return $response['result'] ?? null;
}
function storeCheckoutUrl(PDO $pdo, array $order, array $config): string {
    if ($order['status'] !== 'pending' || (int) $order['expires_at'] <= time()) throw new InvalidArgumentException('Заказ завершён или просрочен');
    if ($order['method'] === 'ton') {
        $recipient = storeTonRecipient($order['merchant']);
        if ($recipient === null || $recipient[0] !== storeConfiguredTonNetwork($config))
            throw new InvalidArgumentException('Сеть оплаты изменилась. Обновите заказ.');
        return rtrim($config['plugin_store_url'], '/') . '/plugin-store-web.php?order=' . $order['id'];
    }
    if ($order['merchant'] !== storeMerchant($config, 'stars')) throw new InvalidArgumentException('Платёжные реквизиты изменились');
    if ($order['checkout_url'] !== null) return $order['checkout_url'];
    $release = storeRow($pdo, 'SELECT candidate FROM plugin_store_releases WHERE sha256=?', [$order['release_hash']]);
    $manifest = json_decode($release['candidate'], true, 32, JSON_THROW_ON_ERROR)['manifest'];
    $title = mb_substr($manifest['name'], 0, 32);
    $url = storeBot($config, 'createInvoiceLink', [
        'title' => $title, 'description' => mb_substr(trim($manifest['description']) ?: $title, 0, 255),
        'payload' => 'bygram:' . $order['id'], 'currency' => 'XTR', 'provider_token' => '',
        'prices' => [['label' => $title, 'amount' => (int) $order['amount']]],
    ]);
    if (!is_string($url) || !preg_match('~^https://t\.me/(?:\$|invoice/)[A-Za-z0-9_-]+$~D', $url))
        throw new RuntimeException('Invalid invoice link');
    storeWrite($pdo, 'UPDATE plugin_store_orders SET checkout_url=? WHERE id=? AND checkout_url IS NULL', [$url, $order['id']]);
    return storeRow($pdo, 'SELECT checkout_url FROM plugin_store_orders WHERE id=?', [$order['id']])['checkout_url'];
}
function storeOrderPayload(mixed $value): ?string {
    return is_string($value) && preg_match('/^bygram:([0-9a-f]{32})$/D', $value, $match) ? $match[1] : null;
}
function storeStarsUpdate(PDO $pdo, array $update, callable $bot, string $support, string $merchant): string {
    if (isset($update['pre_checkout_query'])) {
        $query = $update['pre_checkout_query'];
        $checkout = $query['id'] ?? null;
        if (!is_string($checkout) || $checkout === '' || strlen($checkout) > 128) throw new InvalidArgumentException('Invalid checkout ID');
        $id = storeOrderPayload($query['invoice_payload'] ?? null);
        $accepted = $id !== null && storeCanCheckout($pdo, $id, $checkout, (int) ($query['from']['id'] ?? 0),
            (string) ($query['currency'] ?? ''), (int) ($query['total_amount'] ?? 0), $merchant);
        $answer = ['pre_checkout_query_id' => $checkout, 'ok' => $accepted];
        if (!$accepted) $answer['error_message'] = 'Заказ недействителен. Откройте официальный каталог BYgram.';
        $bot('answerPreCheckoutQuery', $answer);
        return $accepted ? 'accepted' : 'rejected';
    }
    $message = $update['message'] ?? [];
    $payment = $message['successful_payment'] ?? $message['refunded_payment'] ?? null;
    if (is_array($payment)) {
        $id = storeOrderPayload($payment['invoice_payload'] ?? null);
        if ($id === null || ($payment['currency'] ?? '') !== 'XTR') return 'ignored';
        $refund = isset($message['refunded_payment']);
        $payer = $refund && ($message['chat']['type'] ?? '') === 'private' ? $message['chat']['id'] : ($message['from']['id'] ?? 0);
        return storeSettle($pdo, $id, 'stars', (string) ($payment['telegram_payment_charge_id'] ?? ''),
            (string) $payer, (int) ($payment['total_amount'] ?? 0), (int) ($message['date'] ?? time()), $merchant, $refund);
    }
    if (($message['chat']['type'] ?? '') === 'private' && preg_match('~^/(paysupport|support|terms)(?:@\w+)?(?:\s|$)~', $message['text'] ?? '')) {
        $bot('sendMessage', ['chat_id' => $message['chat']['id'], 'text' => PLUGIN_STORE_NOTICE . "\n" . $support]);
    }
    return 'ignored';
}
function storeTonCell(string $id): string {
    if (!preg_match('/^[0-9a-f]{32}$/D', $id)) throw new InvalidArgumentException('Invalid TON order id');
    $data = "\0\0\0\0bygram:" . $id;
    return "\0" . chr(strlen($data) * 2) . $data;
}
function storeTonPayload(string $id): string {
    $cell = storeTonCell($id);
    return base64_encode(hex2bin('b5ee9c720101010100') . chr(strlen($cell)) . "\0" . $cell);
}
function storeTonHost(string $network): string {
    return $network === '-3' ? 'https://testnet.toncenter.com' : 'https://toncenter.com';
}
function storeTonHeaders(array $config, string $network): array {
    $key = $network === storeConfiguredTonNetwork($config) ? ($config['plugin_store_ton_api_key'] ?? '') : '';
    return $key === '' ? [] : ['X-API-Key: ' . $key];
}
function storeSyncTonMerchant(PDO $pdo, string $merchant, array $config, int $pages, ?callable $fetch = null): void {
    $recipient = storeTonRecipient($merchant);
    if ($recipient === null) return;
    [$network,$destination] = $recipient;
    $lock = 'bygram-ton-' . substr(hash('sha256',$merchant),0,48);
    if (!storeRow($pdo,'SELECT GET_LOCK(?,0) AS locked',[$lock])['locked']) return;
    try {
        $current = storeRow($pdo, "SELECT MIN(created_at) AS since FROM plugin_store_orders
            WHERE method='ton' AND merchant=? AND created_at>=?", [$merchant,time()-7*86400]);
        if (!$current || $current['since'] === null) return;
        // Shared by checkout polling and the optional periodic reconciliation job.
        if (storeRow($pdo,'SELECT 1 FROM api_rate_limits WHERE rate_key=?
            AND window_started>UTC_TIMESTAMP()-INTERVAL 10 SECOND',[$lock])) return;
        storeWrite($pdo,'INSERT INTO api_rate_limits (rate_key,window_started,request_count) VALUES (?,UTC_TIMESTAMP(),1)
            ON DUPLICATE KEY UPDATE window_started=VALUES(window_started)',[$lock]);
        $stream = $network . ':' . $destination;
        $cursor = (int) (storeRow($pdo,'SELECT logical_time FROM plugin_store_cursor WHERE stream=?',[$stream])['logical_time'] ?? 0);
        $headers = storeTonHeaders($config,$network);
        $legacy = storeMerchant($config,'ton') === $merchant;
        $host = storeTonHost($network);
        for ($page=0; $page<$pages; $page++) {
            $query = http_build_query(['account'=>$destination,'sort'=>'asc','start_lt'=>$cursor+1,
                'start_utime'=>(int) $current['since'],'limit'=>50]);
            if ($page > 0 && !$headers && $fetch === null) usleep(1100000);
            $url = "$host/api/v3/transactions?$query";
            $body = $fetch === null ? storeHttp($url,null,$headers) : $fetch($url,$headers);
            $transactions = $body['transactions'] ?? throw new RuntimeException('No TON transactions field');
            foreach ($transactions as $tx) {
                $lt = (int) ($tx['lt'] ?? 0);
                if ($lt <= $cursor || (int) ($tx['mc_block_seqno'] ?? 0) < 1) return;
                storeTonTransaction($pdo,$tx,$destination,$network,$legacy);
                storeWrite($pdo,'INSERT INTO plugin_store_cursor (stream,logical_time) VALUES (?,?)
                    ON DUPLICATE KEY UPDATE logical_time=VALUES(logical_time)',[$stream,(string) $lt]);
                $cursor = $lt;
            }
            if (count($transactions)<50) break;
        }
    } finally {
        storeRow($pdo,'SELECT RELEASE_LOCK(?) AS released',[$lock]);
    }
}

function storePollTonOrder(PDO $pdo, array $order, array $config): void {
    if ($order['method'] !== 'ton' || $order['status'] !== 'pending' || (int) $order['expires_at'] < time()-3600) return;
    try {
        storeSyncTonMerchant($pdo,$order['merchant'],$config,1);
    } catch (Throwable $error) {
        error_log('BYgram TON order check: ' . get_class($error));
    }
}
function storeTonFriendly(string $raw, string $network): string {
    if (!preg_match('/^0:[0-9a-f]{64}$/D', $raw)) throw new InvalidArgumentException('Invalid TON address');
    $bytes = ($network === '-3' ? "\xd1" : "\x51") . "\0" . hex2bin(substr($raw,2));
    $crc = 0;
    foreach (str_split($bytes) as $byte) {
        $crc ^= ord($byte) << 8;
        for ($i = 0; $i < 8; $i++) $crc = (($crc << 1) ^ (($crc & 0x8000) ? 0x1021 : 0)) & 0xffff;
    }
    return strtr(base64_encode($bytes . pack('n', $crc)), '+/', '-_');
}
function storeTonTransaction(PDO $pdo, array $tx, string $destination, string $network, bool $legacyReceiver = false): string {
    $message = $tx['in_msg'] ?? [];
    if (strtolower((string) ($tx['account'] ?? '')) !== $destination || (int) ($tx['mc_block_seqno'] ?? 0) < 1
        || ($tx['description']['aborted'] ?? null) !== false || ($tx['emulated'] ?? false) !== false
        || ($message['bounced'] ?? null) !== false || strtolower((string) ($message['destination'] ?? '')) !== $destination
        || !preg_match('/^-?\d+:[0-9a-f]{64}$/Di', $message['source'] ?? '')) return 'ignored';
    $hash = base64_decode(strtr((string) ($message['hash'] ?? ''), '-_', '+/'), true);
    $amount = filter_var($message['value'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    if ($hash === false || strlen($hash) !== 32 || $amount === false) return 'ignored';
    $payer = strtolower($message['source']);
    $charge = $network . ':' . bin2hex($hash);
    $merchant = 'ton:' . $network . ':' . $destination;
    $paidAt = (int) ($tx['now'] ?? 0);
    $content = $message['message_content'] ?? [];
    $id = storeOrderPayload($content['decoded']['comment'] ?? $content['decoded']['text'] ?? null);
    if ($id === null && is_string($content['body'] ?? null)) {
        $body = base64_decode(strtr($content['body'], '-_', '+/'), true);
        if ($body !== false && preg_match('/bygram:([0-9a-f]{32})/D', $body, $match)) $id = $match[1];
    }
    $bodyHash = base64_decode(strtr((string) ($content['hash'] ?? ''), '-_', '+/'), true);
    if ($id !== null && $bodyHash !== false
        && hash_equals(hash('sha256', storeTonCell($id), true), $bodyHash)) {
        $result = storeSettle($pdo, $id, 'ton', $charge, $payer, $amount, $paidAt, $merchant);
        if ($result !== 'unknown') return $result;
    }
    if (!$legacyReceiver) return 'ignored';
    storeWrite($pdo, "INSERT INTO plugin_store_payments (method,merchant,charge,payer,amount,status,paid_at)
        VALUES ('ton',?,?,?,?,'unmatched',?) ON DUPLICATE KEY UPDATE charge=VALUES(charge)", [$merchant,$charge,$payer,$amount,$paidAt]);
    return 'unmatched';
}
