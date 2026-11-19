<?php
declare(strict_types=1);

function storeAuthorStarsEnabled(array $config): bool {
    return !empty($config['bot_token']) && str_starts_with($config['plugin_store_url'] ?? '', 'https://')
        && (($config['plugin_store_stars_test'] ?? true) || ($config['plugin_store_live'] ?? false) === true);
}

function storeStarsEnvironment(array $config): string {
    return ($config['plugin_store_stars_test'] ?? true) ? 'test' : 'live';
}

function storeStarsTokenKey(array $config): string {
    $secret = $config['plugin_store_stars_key'] ?? $config['bot_token'] ?? '';
    if (!is_string($secret) || $secret === '') throw new RuntimeException('Author bot storage unavailable');
    return hash_hmac('sha256', 'BYgram author Stars bot token v1', $secret, true);
}

function storeEncryptStarsToken(array $config, string $merchant, string $token): string {
    $iv = random_bytes(12);
    $data = openssl_encrypt($token, 'aes-256-gcm', storeStarsTokenKey($config), OPENSSL_RAW_DATA, $iv, $tag, $merchant);
    if ($data === false) throw new RuntimeException('Author bot storage unavailable');
    return base64_encode($iv . $tag . $data);
}

function storeAuthorBotConfig(array $config, array $row): array {
    $data = base64_decode($row['token_encrypted'], true);
    $token = $data !== false && strlen($data) > 28
        ? openssl_decrypt(substr($data, 28), 'aes-256-gcm', storeStarsTokenKey($config), OPENSSL_RAW_DATA,
            substr($data, 0, 12), substr($data, 12, 16), $row['merchant'])
        : false;
    if ($token === false) throw new RuntimeException('Author bot credentials unavailable');
    if (!preg_match('/^stars:(test|live):[1-9][0-9]+$/D', $row['merchant'], $match)) throw new RuntimeException('Invalid Stars merchant');
    return ['plugin_store_stars_token' => $token, 'plugin_store_stars_secret' => $row['webhook_secret'],
        'plugin_store_stars_test' => $match[1] === 'test'] + $config;
}

function storeStarsMerchantConfig(PDO $pdo, array $config, string $merchant): array {
    if ($merchant === storeMerchant($config, 'stars')) return $config;
    $row = storeRow($pdo, 'SELECT * FROM plugin_store_author_bots WHERE merchant=?', [$merchant]);
    if (!$row) throw new InvalidArgumentException('Платёжный бот заказа недоступен');
    return storeAuthorBotConfig($config, $row);
}

function storeBindAuthorBot(PDO $pdo, array $config, int $author, string $token): string {
    if (!storeAuthorStarsEnabled($config) || !preg_match('/^([1-9][0-9]{1,15}):[A-Za-z0-9_-]{20,100}$/D', $token, $match))
        throw new InvalidArgumentException('Введите токен отдельного бота из BotFather');
    $botId = $match[1];
    foreach (['bot_token', 'plugin_store_stars_token'] as $reserved) {
        if ($botId === explode(':', $config[$reserved] ?? '', 2)[0])
            throw new InvalidArgumentException('Этот бот уже используется BYgram');
    }
    $merchant = 'stars:' . storeStarsEnvironment($config) . ':' . $botId;
    $botConfig = ['plugin_store_stars_token' => $token] + $config;
    $bot = storeBot($botConfig, 'getMe', []);
    if ((string) ($bot['id'] ?? '') !== $botId || ($bot['is_bot'] ?? null) !== true
        || !preg_match('/^[A-Za-z0-9_]{3,64}$/D', $username = (string) ($bot['username'] ?? ''))) {
        throw new InvalidArgumentException('Telegram не подтвердил бота');
    }
    $webhook = rtrim($config['plugin_store_url'], '/') . '/plugin-store-stars.php?' . http_build_query(['merchant' => $merchant]);
    return storeTransaction($pdo, static function () use ($pdo, $config, $author, $token, $botConfig, $username, $merchant, $webhook) {
        if (!storeRow($pdo, 'SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE', [$author]))
            throw new InvalidArgumentException('Аккаунт автора не найден');
        $existing = storeRow($pdo, 'SELECT * FROM plugin_store_author_bots WHERE merchant=? FOR UPDATE', [$merchant]);
        if ($existing && (int) $existing['author_id'] !== $author)
            throw new InvalidArgumentException('Бот уже подключён к другому аккаунту');
        $current = storeBot($botConfig, 'getWebhookInfo', [])['url'] ?? '';
        if ($current !== '' && $current !== $webhook)
            throw new InvalidArgumentException('У бота уже настроен другой webhook. Используйте отдельного торгового бота.');
        $secret = $existing['webhook_secret'] ?? bin2hex(random_bytes(32));
        $encrypted = storeEncryptStarsToken($config, $merchant, $token);
        storeWrite($pdo, 'UPDATE plugin_store_author_bots SET active=0 WHERE author_id=? AND merchant LIKE ?', [$author, 'stars:' . storeStarsEnvironment($config) . ':%']);
        if ($existing) {
            storeWrite($pdo, 'UPDATE plugin_store_author_bots SET username=?,token_encrypted=?,active=1 WHERE merchant=?', [$username,$encrypted,$merchant]);
        } else {
            storeWrite($pdo, 'INSERT INTO plugin_store_author_bots (merchant,author_id,username,token_encrypted,webhook_secret,active) VALUES (?,?,?,?,?,1)',
                [$merchant,$author,$username,$encrypted,$secret]);
        }
        if (storeBot($botConfig, 'setWebhook', ['url'=>$webhook,'secret_token'=>$secret,'allowed_updates'=>['pre_checkout_query','message']]) !== true)
            throw new RuntimeException('Telegram did not register author webhook');
        return $username;
    });
}
