<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/account-sessions.php';

set_exception_handler(static function (Throwable $error): never {
    error_log('BYgram API failure type=' . get_class($error) . ' action=' . ($GLOBALS['bygram_action'] ?? 'unknown'));
    jsonResponse(['ok' => false], 503);
});

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') jsonResponse(['ok' => false], 405);
if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 6000000) jsonResponse(['ok' => false], 413);
$raw = (string) file_get_contents('php://input');
if (strlen($raw) > 6000000 || !is_array($request = json_decode($raw, true))) jsonResponse(['ok' => false], 400);
$action = (string) ($request['action'] ?? '');
if ($action !== 'profile_background_put' && strlen($raw) > 16384) jsonResponse(['ok' => false], 413);
$GLOBALS['bygram_action'] = $action;
$ip = (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
$pdo = db();

function rateLimit(PDO $pdo, string $key, int $limit): void {
    $q = $pdo->prepare("INSERT INTO api_rate_limits (rate_key,window_started,request_count) VALUES (?,UTC_TIMESTAMP(),1)
        ON DUPLICATE KEY UPDATE request_count=IF(window_started<UTC_TIMESTAMP()-INTERVAL 1 MINUTE,1,request_count+1),window_started=IF(window_started<UTC_TIMESTAMP()-INTERVAL 1 MINUTE,UTC_TIMESTAMP(),window_started)");
    $q->execute([$key]);
    $q = $pdo->prepare('SELECT request_count FROM api_rate_limits WHERE rate_key=?'); $q->execute([$key]);
    if ((int) $q->fetchColumn() > $limit) { error_log('BYgram API rate_limit key=' . hash('sha256', $key)); jsonResponse(['ok' => false], 429); }
}
function token(): string { return rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='); }
function accountLoginUrl(string $mode, string $activation): string {
    $adminUrl = (string) (appConfig()['admin_url'] ?? 'https://byxarek.site/TEST-PROJECT/BYgram/api/admin.php');
    return dirname($adminUrl) . '/account-login.php?' . http_build_query(['mode' => $mode, 'token' => $activation]);
}
function issueTokens(PDO $pdo, int $userId, bool $confirmed = false): array {
    $access = token(); $refresh = token();
    $pdo->prepare("INSERT INTO activity_sessions (telegram_id,account_confirmed,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES (?,?,?,?,UTC_TIMESTAMP()+INTERVAL 1 HOUR,UTC_TIMESTAMP()+INTERVAL 30 DAY)")
        ->execute([$userId, (int) $confirmed, hash('sha256', $access), hash('sha256', $refresh)]);
    return ['access_token' => $access, 'refresh_token' => $refresh, 'access_expires' => time() + 3600];
}
function bearer(): string {
    $header = (string) ($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    return str_starts_with($header, 'Bearer ') ? substr($header, 7) : '';
}
function publicKeyPem(string $encoded): string {
    $der = base64_decode($encoded, true);
    if ($der === false || strlen($der) < 64 || strlen($der) > 1024) throw new InvalidArgumentException('invalid public key');
    return "-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode($der), 64, "\n") . "-----END PUBLIC KEY-----\n";
}
function signaturePayload(string $raw, int $timestamp, string $nonce): string {
    $path = (string) (parse_url((string) ($_SERVER['REQUEST_URI'] ?? '/'), PHP_URL_PATH) ?: '/');
    return "POST\n$path\n$timestamp\n$nonce\n" . hash('sha256', $raw);
}
function verifyRequestSignature(string $publicKey, string $raw, int $timestamp, string $nonce, bool $required = false): bool {
    if (!$required && !requestSignatureChecksEnabled()) return true;
    $signature = base64_decode((string) ($_SERVER['HTTP_X_BYGRAM_SIGNATURE'] ?? ''), true);
    return $signature !== false && $timestamp > 0 && abs(time() - $timestamp) <= 60
        && preg_match('/^[a-f0-9-]{36}$/D', $nonce) === 1
        && openssl_verify(signaturePayload($raw, $timestamp, $nonce), $signature, publicKeyPem($publicKey), OPENSSL_ALGO_SHA256) === 1;
}
function authorizeAccountRequest(PDO $pdo, int $userId, string $raw, bool $requireConfirmed = false): void {
    if (validAccountSession(bearer(), $userId)) return;
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0);
    $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    $authorized = !$requireConfirmed && !requestSignatureChecksEnabled() && !accountConfirmationEnabled();
    if (!$authorized) {
        foreach (typingPublicKeys($pdo, $userId, $requireConfirmed) as $publicKey) {
            if (verifyRequestSignature((string) $publicKey, $raw, $timestamp, $nonce, $requireConfirmed)) { $authorized = true; break; }
        }
    }
    if (!$authorized) jsonResponse(['ok' => false, 'needs_registration' => true], 401);
    if ($requireConfirmed || requestSignatureChecksEnabled()) {
        try { $pdo->prepare('INSERT INTO typing_nonces (telegram_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$userId, $nonce]); }
        catch (PDOException) { jsonResponse(['ok' => false], 409); }
    }
}
function authenticatedSession(PDO $pdo, string $raw): array {
    $access = bearer();
    $q = $pdo->prepare('SELECT s.id,s.telegram_id,s.device_id,d.public_key FROM activity_sessions s LEFT JOIN licensed_devices d ON d.id=s.device_id WHERE s.access_hash=? AND s.access_expires_at>UTC_TIMESTAMP() AND (?=0 OR d.revoked_at IS NULL) AND (s.revoked_at IS NULL OR (?=0 AND d.revoked_at IS NOT NULL)) AND (?=0 OR d.account_confirmed=1 OR (s.device_id IS NULL AND s.account_confirmed=1)) AND (?=0 OR s.device_id IS NOT NULL OR s.account_confirmed=1)');
    $q->execute([hash('sha256', $access), (int) deviceRevocationChecksEnabled(), (int) deviceRevocationChecksEnabled(), (int) accountConfirmationEnabled(), (int) requestSignatureChecksEnabled()]); $session = $access === '' ? false : $q->fetch();
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0); $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    if (!$session || ($session['device_id'] !== null && !verifyRequestSignature((string) $session['public_key'], $raw, $timestamp, $nonce))) jsonResponse(['ok' => false], 401);
    try { if ($session['device_id'] !== null && requestSignatureChecksEnabled()) $pdo->prepare('INSERT INTO api_nonces (session_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$session['id'], $nonce]); }
    catch (PDOException) { error_log('BYgram API replay session=' . $session['id']); jsonResponse(['ok' => false], 409); }
    return $session;
}
function viewerIds(mixed $value): array {
    $ids = array_values(array_unique(array_filter(array_map('intval', is_array($value) ? $value : []), static fn(int $id): bool => $id > 0)));
    if (count($ids) > 200) jsonResponse(['ok' => false], 400);
    return $ids;
}
function viewerPermissions(mixed $value): array {
    $result = [];
    foreach (is_array($value) ? $value : [] as $viewer) {
        if (is_array($viewer)) {
            $id = (int) ($viewer['user_id'] ?? 0);
            $permissions = (int) ($viewer['permissions'] ?? 0) & 7;
        } else {
            $id = (int) $viewer;
            $permissions = 4;
        }
        if ($id > 0 && $permissions !== 0) $result[$id] = $permissions;
    }
    if (count($result) > 200) jsonResponse(['ok' => false], 400);
    return $result;
}
function replaceViewers(PDO $pdo, int $ownerId, array $viewers): void {
    $pdo->prepare('DELETE FROM activity_viewers WHERE owner_id=?')->execute([$ownerId]);
    $insert = $pdo->prepare('INSERT INTO activity_viewers (owner_id,viewer_id,permissions) VALUES (?,?,?)');
    foreach ($viewers as $viewerId => $permissions) $insert->execute([$ownerId, $viewerId, $permissions]);
}

rateLimit($pdo, 'ip:' . $ip, in_array($action, ['device_start', 'device_prove', 'device_status', 'device_replace'], true) ? 30 : 120);
if ($action === 'device_seen') {
    require __DIR__ . '/client-devices.php';
    try {
        reportClientDevice($pdo, $request);
        jsonResponse(['ok' => true]);
    } catch (InvalidArgumentException) {
        jsonResponse(['ok' => false], 400);
    }
}
$pdo->exec('DELETE FROM device_activations WHERE expires_at<UTC_TIMESTAMP()-INTERVAL 1 DAY');
$pdo->exec('DELETE FROM api_nonces WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM typing_nonces WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM chat_activity WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM api_rate_limits WHERE window_started<UTC_TIMESTAMP()-INTERVAL 1 DAY');

if ($action === 'activity_start' || $action === 'device_start') {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    $confirmed = validAccountSession(bearer(), $userId);
    if (!$confirmed && (accountConfirmationEnabled() || requestSignatureChecksEnabled())) {
        jsonResponse(['ok' => false, 'needs_registration' => true], 401);
    }
    rateLimit($pdo, 'activity_start:' . $ip, 30);
    $pdo->prepare('INSERT IGNORE INTO users (telegram_id) VALUES (?)')->execute([$userId]);
    if ($action === 'device_start') {
        // Older clients expect a challenge. Hold only the account and a short-lived exchange token.
        $activation = token();
        $challenge = token();
        $pdo->prepare('INSERT INTO device_activations (token_hash,expected_user_id,challenge,expires_at)
            VALUES (?,?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)')->execute([hash('sha256', $activation), $userId, $challenge]);
        jsonResponse(['ok' => true, 'state' => 'pending_proof', 'activation' => $activation, 'challenge' => $challenge]);
    }
    jsonResponse(['ok' => true, 'state' => 'active'] + issueTokens($pdo, $userId, $confirmed));
}
if ($action === 'device_prove' || $action === 'device_status') {
    if (accountConfirmationEnabled() || requestSignatureChecksEnabled()) jsonResponse(['ok' => false], 401);
    $activation = $request['activation'] ?? '';
    if (!is_string($activation) || !preg_match('/^[A-Za-z0-9_-]{43}$/D', $activation)) jsonResponse(['ok' => false], 400);
    $key = hash('sha256', $activation);
    $pdo->beginTransaction();
    $lookup = $pdo->prepare('SELECT expected_user_id FROM device_activations WHERE token_hash=? AND expires_at>UTC_TIMESTAMP()
        AND completed_at IS NULL FOR UPDATE');
    $lookup->execute([$key]);
    $userId = (int) $lookup->fetchColumn();
    if (!$userId) {
        $pdo->rollBack();
        jsonResponse(['ok' => false], 404);
    }
    $session = issueTokens($pdo, $userId);
    $pdo->prepare('DELETE FROM device_activations WHERE token_hash=?')->execute([$key]);
    $pdo->commit();
    jsonResponse(['ok' => true, 'state' => 'active'] + $session);
}
if ($action === 'device_replace') jsonResponse(['ok' => false], 404);

if (in_array($action, ['account_login_start', 'account_login_exchange'], true)) {
    rateLimit($pdo, $action . ':' . $ip, 30);
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    if ($action === 'account_login_start') {
        $browserToken = token();
        $exchangeToken = token();
        accountLoginAttempt($pdo, $userId, $browserToken, $exchangeToken);
        jsonResponse(['ok' => true, 'auth_url' => accountLoginUrl('account', $browserToken), 'exchange_token' => $exchangeToken]);
    }
    $exchangeToken = $request['exchange_token'] ?? '';
    if (!is_string($exchangeToken) || !preg_match('/^[A-Za-z0-9_-]{43}$/D', $exchangeToken)) jsonResponse(['ok' => false], 400);
    jsonResponse(exchangeAccountLogin($pdo, $userId, $exchangeToken));
}
if ($action === 'refresh') {
    $refresh = (string) ($request['refresh_token'] ?? '');
    $q = $pdo->prepare('SELECT s.id,s.telegram_id,s.device_id,d.public_key FROM activity_sessions s LEFT JOIN licensed_devices d ON d.id=s.device_id WHERE s.refresh_hash=? AND s.refresh_expires_at>UTC_TIMESTAMP() AND (?=0 OR d.revoked_at IS NULL) AND (s.revoked_at IS NULL OR (?=0 AND d.revoked_at IS NOT NULL)) AND (?=0 OR d.account_confirmed=1 OR (s.device_id IS NULL AND s.account_confirmed=1)) AND (?=0 OR s.device_id IS NOT NULL OR s.account_confirmed=1)');
    $q->execute([hash('sha256', $refresh), (int) deviceRevocationChecksEnabled(), (int) deviceRevocationChecksEnabled(), (int) accountConfirmationEnabled(), (int) requestSignatureChecksEnabled()]); $session = $refresh === '' ? false : $q->fetch();
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0); $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    if (!$session || ($session['device_id'] !== null && !verifyRequestSignature((string) $session['public_key'], $raw, $timestamp, $nonce))) jsonResponse(['ok' => false], 401);
    try { if ($session['device_id'] !== null && requestSignatureChecksEnabled()) $pdo->prepare('INSERT INTO api_nonces (session_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$session['id'], $nonce]); }
    catch (PDOException) { jsonResponse(['ok' => false], 409); }
    $access = token();
    $pdo->prepare('UPDATE activity_sessions SET access_hash=?,access_expires_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE id=?')->execute([hash('sha256', $access), $session['id']]);
    jsonResponse(['ok' => true, 'access_token' => $access, 'access_expires' => time() + 3600]);
}
if ($action === 'badges') {
    rateLimit($pdo, 'badges:' . $ip, 120);
    $ids = viewerIds($request['user_ids'] ?? []);
    $chatIds = viewerIds($request['chat_ids'] ?? []);
    $configured = loadBadges(); $badges = [];
    foreach ($ids as $id) if (badgeFor($configured, 'user', $id)) $badges[] = ['peer_type' => 'user', 'peer_id' => $id];
    foreach ($chatIds as $id) if (badgeFor($configured, 'chat', $id)) $badges[] = ['peer_type' => 'chat', 'peer_id' => $id];
    jsonResponse(['ok' => true, 'badges' => $badges]);
}
if ($action === 'profile_background_get') {
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$ownerId || $ownerId < 1) jsonResponse(['ok' => false], 400);
    $query = $pdo->prepare('SELECT revision,mime FROM profile_backgrounds WHERE telegram_id=?');
    $query->execute([$ownerId]);
    $image = $query->fetch();
    jsonResponse(['ok' => true, 'revision' => $image ? $image['revision'] : '', 'mime' => $image ? $image['mime'] : '']);
}
if ($action === 'profile_background_put') {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1 || !is_string($request['image'] ?? null)) jsonResponse(['ok' => false], 400);
    rateLimit($pdo, 'profile_background:' . $userId, 6);
    authorizeAccountRequest($pdo, (int) $userId, $raw);
    if ($request['image'] === '') {
        $pdo->prepare('DELETE FROM profile_backgrounds WHERE telegram_id=?')->execute([$userId]);
        jsonResponse(['ok' => true]);
    }
    require __DIR__ . '/profile-background-validation.php';
    try { [$data, $mime, $revision] = profileBackgroundImage($request['image']); }
    catch (InvalidArgumentException) { jsonResponse(['ok' => false, 'invalid_image' => true], 422); }
    $pdo->prepare('INSERT INTO profile_backgrounds (telegram_id,image_data,mime,revision) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE image_data=VALUES(image_data),mime=VALUES(mime),revision=VALUES(revision)')->execute([$userId, $data, $mime, $revision]);
    jsonResponse(['ok' => true]);
}
if ($action === 'typing_get') {
    rateLimit($pdo, 'typing_get:' . $ip, 120);
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$ownerId || $ownerId < 1) jsonResponse(['ok' => false], 400);
    $query = $pdo->prepare('SELECT statuses FROM typing_statuses WHERE telegram_id=?');
    $query->execute([$ownerId]);
    $statuses = json_decode((string) ($query->fetchColumn() ?: '{}'), true);
    jsonResponse(['ok' => true, 'statuses' => (object) (is_array($statuses) ? $statuses : [])]);
}
if ($action === 'typing_put') {
    rateLimit($pdo, 'typing_put:' . $ip, 60);
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    $values = $request['statuses'] ?? null;
    if (!$userId || $userId < 1 || !is_array($values) || count($values) > 12) jsonResponse(['ok' => false], 400);
    $statuses = [];
    foreach ($values as $key => $value) {
        if (!preg_match('/^(?:[0-9]|10)$/D', (string) $key) || !is_string($value) || mb_strlen($value) > 64) jsonResponse(['ok' => false], 400);
        $value = trim(strip_tags($value));
        if ($value !== '') $statuses[(string) $key] = $value;
    }
    authorizeAccountRequest($pdo, (int) $userId, $raw);
    $pdo->prepare('INSERT INTO typing_statuses (telegram_id,statuses) VALUES (?,?) ON DUPLICATE KEY UPDATE statuses=VALUES(statuses)')
        ->execute([$userId,json_encode((object)$statuses,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES)]);
    jsonResponse(['ok' => true]);
}
if (str_starts_with($action, 'profile_views_')) {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    rateLimit($pdo, 'profile_views:' . $userId, 120);
    authorizeAccountRequest($pdo, (int) $userId, $raw);
    $pdo->prepare('DELETE FROM profile_views WHERE visited_at<UTC_TIMESTAMP()-INTERVAL 7 DAY')->execute();
    if ($action === 'profile_views_set') {
        if (!is_bool($request['enabled'] ?? null)) jsonResponse(['ok' => false], 400);
        $enabled = $request['enabled'] ? 1 : 0;
        $pdo->beginTransaction();
        $old = $pdo->prepare('SELECT enabled FROM profile_view_settings WHERE telegram_id=? FOR UPDATE');
        $old->execute([$userId]); $wasEnabled = (bool) $old->fetchColumn();
        $pdo->prepare('INSERT INTO profile_view_settings (telegram_id,enabled) VALUES (?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)')->execute([$userId, $enabled]);
        if (!$enabled || !$wasEnabled) $pdo->prepare('DELETE FROM profile_views WHERE owner_id=? OR viewer_id=?')->execute([$userId, $userId]);
        $pdo->commit();
        jsonResponse(['ok' => true, 'enabled' => (bool) $enabled]);
    }
    $q = $pdo->prepare('SELECT enabled FROM profile_view_settings WHERE telegram_id=?');
    $q->execute([$userId]);
    $enabled = (bool) $q->fetchColumn();
    if ($action === 'profile_views_record') {
        $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
        if (!$ownerId || $ownerId < 1 || $ownerId === $userId) jsonResponse(['ok' => false], 400);
        if (!$enabled) jsonResponse(['ok' => true, 'recorded' => false]);
        $q->execute([$ownerId]);
        if (!(bool) $q->fetchColumn()) jsonResponse(['ok' => true, 'recorded' => false]);
        $name = mb_substr(trim(strip_tags((string) ($request['viewer_name'] ?? ''))), 0, 128);
        $username = (string) ($request['viewer_username'] ?? '');
        if ($name === '' || ($username !== '' && !preg_match('/^[A-Za-z0-9_]{5,64}$/D', $username))) jsonResponse(['ok' => false], 400);
        $identity = $pdo->prepare('SELECT username,first_name,last_name FROM users WHERE telegram_id=?');
        $identity->execute([$userId]); $known = $identity->fetch();
        if ($known) {
            $name = mb_substr(trim((string) $known['first_name'] . ' ' . (string) $known['last_name']), 0, 128) ?: $name;
            $username = (string) ($known['username'] ?? '');
        }
        $pdo->prepare('INSERT INTO profile_views (owner_id,viewer_id,viewer_name,viewer_username,visited_at) VALUES (?,?,?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE viewer_name=VALUES(viewer_name),viewer_username=VALUES(viewer_username),notified_at=IF(visited_at<UTC_TIMESTAMP()-INTERVAL 7 DAY,NULL,notified_at),seen_at=NULL,visited_at=UTC_TIMESTAMP()')->execute([$ownerId, $userId, $name, $username]);
        jsonResponse(['ok' => true, 'recorded' => true]);
    }
    if ($action === 'profile_views_seen') {
        $viewerId = filter_var($request['viewer_id'] ?? null, FILTER_VALIDATE_INT);
        if (!$viewerId || $viewerId < 1) jsonResponse(['ok' => false], 400);
        $pdo->prepare('UPDATE profile_views SET seen_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id=? AND seen_at IS NULL')->execute([$userId, $viewerId]);
        jsonResponse(['ok' => true]);
    }
    if ($action === 'profile_views_list' || $action === 'profile_views_poll' || $action === 'profile_views_summary') {
        if (!$enabled) jsonResponse(['ok' => true, 'enabled' => false, 'views' => []]);
        $extra = $action === 'profile_views_poll' ? ' AND v.notified_at IS NULL' : ($action === 'profile_views_summary' ? ' AND v.seen_at IS NULL' : '');
        $limit = $action === 'profile_views_summary' ? 1 : 100;
        $order = $action === 'profile_views_list' ? '(v.seen_at IS NULL) DESC,' : '';
        $query = $pdo->prepare("SELECT v.viewer_id,v.viewer_name,v.viewer_username,TIMESTAMPDIFF(SECOND,'1970-01-01 00:00:00',v.visited_at) AS visited_at,(v.seen_at IS NULL) AS is_new FROM profile_views v JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY$extra ORDER BY $order v.visited_at DESC LIMIT $limit");
        $query->execute([$userId]); $views = $query->fetchAll();
        if ($action === 'profile_views_list' && $views) {
            $ids = array_column($views, 'viewer_id');
            $marks = implode(',', array_fill(0, count($ids), '?'));
            $pdo->prepare("UPDATE profile_views SET seen_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id IN ($marks) AND seen_at IS NULL")
                ->execute(array_merge([$userId], $ids));
        }
        $expires = time() + 3600;
        foreach ($views as &$view) {
            $viewerId = (int) $view['viewer_id'];
            $signature = hash_hmac('sha256', "$userId:$viewerId:$expires", (string) appConfig()['bot_token']);
            $view['avatar_url'] = "https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?owner=$userId&viewer=$viewerId&expires=$expires&sig=$signature";
        }
        unset($view);
        if ($action === 'profile_views_poll' && $views) {
            $ids = array_column($views, 'viewer_id');
            $marks = implode(',', array_fill(0, count($ids), '?'));
            $pdo->prepare("UPDATE profile_views SET notified_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id IN ($marks)")->execute(array_merge([$userId], $ids));
        }
        if ($action === 'profile_views_summary') {
            $count = $pdo->prepare('SELECT COUNT(*) FROM profile_views v JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.seen_at IS NULL AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY');
            $count->execute([$userId]);
            jsonResponse(['ok' => true, 'enabled' => true, 'new_count' => (int) $count->fetchColumn(), 'views' => $views]);
        }
        jsonResponse(['ok' => true, 'enabled' => true, 'views' => $views]);
    }
    jsonResponse(['ok' => false], 400);
}

if (in_array($action, ['flame_get', 'flame_sync', 'flame_restore'], true)) {
    require __DIR__ . '/chat-flames.php';
    $idOptions = ['options' => ['min_range' => 1, 'max_range' => 0xffffffffff]];
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT, $idOptions);
    $peerId = filter_var($request['peer_id'] ?? null, FILTER_VALIDATE_INT, $idOptions);
    if (!$userId || !$peerId || $peerId === $userId) jsonResponse(['ok' => false], 400);
    authorizeAccountRequest($pdo, $userId, $raw, true);
    rateLimit($pdo, 'flame:' . $userId, 60);
    try {
        jsonResponse(chatFlameRequest($pdo, $userId, $peerId, $action, $request));
    } catch (InvalidArgumentException) {
        jsonResponse(['ok' => false], 400);
    }
}

$session = authenticatedSession($pdo, $raw); $userId = (int) $session['telegram_id'];
rateLimit($pdo, 'session:' . $session['id'], 120);
if ($action === 'plugin_submit' || $action === 'plugin_submissions') {
    require __DIR__ . '/plugin-catalog.php';
    try {
        if ($action === 'plugin_submit') submitPlugin($pdo, $userId, (string) ($request['repository'] ?? ''));
        jsonResponse(['ok' => true, 'submissions' => pluginSubmissions($pdo, $userId)]);
    } catch (InvalidArgumentException $error) { jsonResponse(['ok' => false, 'error' => $error->getMessage()], 422); }
}
// Older clients use this response as a session readiness check.
if ($action === 'license') jsonResponse(['ok' => true, 'licensed' => true, 'valid_until' => 0]);

if ($action === 'update') {
    $type = (int) ($request['activity_type'] ?? 4);
    $name = trim(strip_tags((string) ($request['peer_name'] ?? '')));
    $peerId = (int) ($request['peer_id'] ?? 0);
    $peerKind = (int) ($request['peer_kind'] ?? 0);
    if (!in_array($type, [1, 2, 4], true) || !in_array($peerKind, [0, 1, 2, 3, 4], true) || ($type !== 1 && ($peerId === 0 || $name === ''))) jsonResponse(['ok' => false], 400);
    $pdo->beginTransaction();
    $pdo->prepare('INSERT INTO chat_activity (owner_id,activity_type,peer_id,peer_name,peer_kind,expires_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP()+INTERVAL 60 SECOND) ON DUPLICATE KEY UPDATE activity_type=VALUES(activity_type),peer_id=VALUES(peer_id),peer_name=VALUES(peer_name),peer_kind=VALUES(peer_kind),expires_at=VALUES(expires_at)')
        ->execute([$userId, $type, $peerId === 0 ? null : $peerId, mb_substr($name, 0, 100), $peerKind]);
    if (array_key_exists('viewers', $request)) replaceViewers($pdo, $userId, viewerPermissions($request['viewers']));
    $pdo->commit(); jsonResponse(['ok' => true]);
}
if ($action === 'viewers') { $pdo->beginTransaction(); replaceViewers($pdo, $userId, viewerPermissions($request['viewers'] ?? [])); $pdo->commit(); jsonResponse(['ok' => true]); }
if ($action === 'clear') { $pdo->prepare('DELETE FROM chat_activity WHERE owner_id=?')->execute([$userId]); jsonResponse(['ok' => true]); }
if ($action === 'get') {
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    $q = $pdo->prepare('SELECT a.peer_name FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id=? AND a.activity_type=4 AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & 4)<>0');
    $q->execute([$userId, $ownerId ?: 0]); $name = $q->fetchColumn();
    jsonResponse($name === false ? ['ok' => true] : ['ok' => true, 'peer_name' => $name]);
}
if ($action === 'activity_get') {
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    $q = $pdo->prepare('SELECT a.activity_type,a.peer_id,a.peer_name,a.peer_kind FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id=? AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & a.activity_type)<>0');
    $q->execute([$userId, $ownerId ?: 0]); $activity = $q->fetch();
    jsonResponse($activity === false ? ['ok' => true] : ['ok' => true] + $activity);
}
if ($action === 'activities') {
    $ownerIds = viewerIds($request['owner_ids'] ?? []);
    if ($ownerIds === []) jsonResponse(['ok' => true, 'activities' => []]);
    $marks = implode(',', array_fill(0, count($ownerIds), '?'));
    $q = $pdo->prepare("SELECT a.owner_id,a.activity_type,a.peer_id,a.peer_name,a.peer_kind FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id IN ($marks) AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & a.activity_type)<>0");
    $q->execute(array_merge([$userId], $ownerIds));
    jsonResponse(['ok' => true, 'activities' => $q->fetchAll()]);
}
error_log('BYgram API invalid_action session=' . $session['id']);
jsonResponse(['ok' => false], 400);
