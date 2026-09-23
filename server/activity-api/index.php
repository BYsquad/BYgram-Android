<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

set_exception_handler(static function (Throwable $error): never {
    error_log('BYgram API failure type=' . get_class($error) . ' action=' . ($GLOBALS['bygram_action'] ?? 'unknown'));
    jsonResponse(['ok' => false], 503);
});

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') jsonResponse(['ok' => false], 405);
if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 16384) jsonResponse(['ok' => false], 413);
$raw = (string) file_get_contents('php://input');
if (strlen($raw) > 16384 || !is_array($request = json_decode($raw, true))) jsonResponse(['ok' => false], 400);
$action = (string) ($request['action'] ?? '');
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
function botUrl(string $activation): string {
    $username = appSetting('bot_username');
    if ($username === '') throw new RuntimeException('bot username is not configured');
    $url = 'https://t.me/' . rawurlencode($username);
    return $activation === '' ? $url : $url . '?start=bind_' . rawurlencode($activation);
}
function managerUrl(): string {
    $username = ltrim(trim(appSetting('manager_username', (string) (appConfig()['manager_username'] ?? ''))), '@');
    if (!preg_match('/^[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]$/D', $username)) throw new RuntimeException('manager username is not configured');
    return 'https://t.me/' . rawurlencode($username);
}
function issueTokens(PDO $pdo, int $userId, string $deviceId): array {
    $access = token(); $refresh = token();
    $pdo->prepare("INSERT INTO activity_sessions (telegram_id,device_id,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES (?,?,?,?,UTC_TIMESTAMP()+INTERVAL 1 HOUR,UTC_TIMESTAMP()+INTERVAL 30 DAY)")
        ->execute([$userId, $deviceId, hash('sha256', $access), hash('sha256', $refresh)]);
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
function verifyRequestSignature(string $publicKey, string $raw, int $timestamp, string $nonce): bool {
    $signature = base64_decode((string) ($_SERVER['HTTP_X_BYGRAM_SIGNATURE'] ?? ''), true);
    return $signature !== false && $timestamp > 0 && abs(time() - $timestamp) <= 60
        && preg_match('/^[a-f0-9-]{36}$/D', $nonce) === 1
        && openssl_verify(signaturePayload($raw, $timestamp, $nonce), $signature, publicKeyPem($publicKey), OPENSSL_ALGO_SHA256) === 1;
}
function authenticatedSession(PDO $pdo, string $raw): array {
    $access = bearer();
    $q = $pdo->prepare('SELECT s.id,s.telegram_id,s.device_id,d.public_key FROM activity_sessions s JOIN licensed_devices d ON d.id=s.device_id AND d.revoked_at IS NULL WHERE s.access_hash=? AND s.access_expires_at>UTC_TIMESTAMP() AND s.revoked_at IS NULL');
    $q->execute([hash('sha256', $access)]); $session = $access === '' ? false : $q->fetch();
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0); $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    if (!$session || !verifyRequestSignature((string) $session['public_key'], $raw, $timestamp, $nonce)) jsonResponse(['ok' => false], 401);
    try { $pdo->prepare('INSERT INTO api_nonces (session_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$session['id'], $nonce]); }
    catch (PDOException) { error_log('BYgram API replay session=' . $session['id']); jsonResponse(['ok' => false], 409); }
    $pdo->prepare('UPDATE licensed_devices SET last_seen_at=UTC_TIMESTAMP() WHERE id=?')->execute([$session['device_id']]);
    return $session;
}
function activation(PDO $pdo, string $value): array|false {
    if (!preg_match('/^[A-Za-z0-9_-]{43}$/D', $value)) return false;
    $q = $pdo->prepare('SELECT * FROM device_activations WHERE token_hash=? AND expires_at>UTC_TIMESTAMP()');
    $q->execute([hash('sha256', $value)]);
    return $q->fetch();
}
function deviceList(PDO $pdo, int $userId): array {
    $q = $pdo->prepare('SELECT id,device_name,created_at,last_seen_at FROM licensed_devices WHERE telegram_id=? AND revoked_at IS NULL ORDER BY created_at');
    $q->execute([$userId]);
    return $q->fetchAll();
}
function activateDevice(PDO $pdo, array $attempt, ?string $replaceId = null): array {
    $userId = (int) $attempt['expected_user_id'];
    $pdo->beginTransaction();
    $license = $pdo->prepare("SELECT telegram_id FROM licenses WHERE telegram_id=? AND status='active' AND (valid_until IS NULL OR valid_until>UTC_TIMESTAMP()) FOR UPDATE");
    $license->execute([$userId]);
    if (!$license->fetch()) { $pdo->rollBack(); return ['ok' => true, 'state' => 'denied']; }
    $existing = $pdo->prepare('SELECT id,telegram_id,revoked_at FROM licensed_devices WHERE key_fingerprint=? FOR UPDATE');
    $existing->execute([$attempt['key_fingerprint']]); $sameKey = $existing->fetch();
    if ($sameKey && (int) $sameKey['telegram_id'] !== $userId) { $pdo->rollBack(); return ['ok' => false]; }
    if ($sameKey && $sameKey['revoked_at'] === null) {
        $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE device_id=? AND revoked_at IS NULL')->execute([$sameKey['id']]);
        $tokens = issueTokens($pdo, $userId, (string) $sameKey['id']);
        $pdo->prepare('UPDATE device_activations SET completed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$attempt['id']]);
        $pdo->commit();
        return ['ok' => true, 'state' => 'active'] + $tokens;
    }
    $devices = deviceList($pdo, $userId);
    if (count($devices) >= 2 && $replaceId === null) { $pdo->commit(); return ['ok' => true, 'state' => 'device_limit', 'devices' => $devices]; }
    if ($replaceId !== null) {
        $target = $pdo->prepare('SELECT id FROM licensed_devices WHERE id=? AND telegram_id=? AND revoked_at IS NULL FOR UPDATE');
        $target->execute([$replaceId, $userId]);
        if (!$target->fetch()) { $pdo->rollBack(); return ['ok' => false]; }
        $pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE id=?')->execute([$replaceId]);
        $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE device_id=? AND revoked_at IS NULL')->execute([$replaceId]);
    }
    $deviceId = $sameKey ? (string) $sameKey['id'] : bin2hex(random_bytes(16));
    if ($sameKey) {
        $pdo->prepare('UPDATE licensed_devices SET public_key=?,device_name=?,created_at=UTC_TIMESTAMP(),last_seen_at=UTC_TIMESTAMP(),revoked_at=NULL WHERE id=?')
            ->execute([$attempt['public_key'], $attempt['device_name'], $deviceId]);
    } else {
        $pdo->prepare('INSERT INTO licensed_devices (id,telegram_id,public_key,key_fingerprint,device_name) VALUES (?,?,?,?,?)')
            ->execute([$deviceId, $userId, $attempt['public_key'], $attempt['key_fingerprint'], $attempt['device_name']]);
    }
    $tokens = issueTokens($pdo, $userId, $deviceId);
    $pdo->prepare('UPDATE device_activations SET completed_at=UTC_TIMESTAMP() WHERE id=?')->execute([$attempt['id']]);
    $pdo->commit();
    return ['ok' => true, 'state' => 'active'] + $tokens;
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
$pdo->exec('DELETE FROM device_activations WHERE expires_at<UTC_TIMESTAMP()-INTERVAL 1 DAY');
$pdo->exec('DELETE FROM api_nonces WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM typing_nonces WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM chat_activity WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM api_rate_limits WHERE window_started<UTC_TIMESTAMP()-INTERVAL 1 DAY');

if ($action === 'device_start') {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    if (!licensed((int) $userId)) jsonResponse(['ok' => true, 'state' => 'denied', 'bot_url' => managerUrl()]);
    $activation = token(); $challenge = token();
    $pdo->prepare('INSERT IGNORE INTO users (telegram_id) VALUES (?)')->execute([$userId]);
    $pdo->prepare('INSERT INTO device_activations (token_hash,expected_user_id,challenge,expires_at) VALUES (?,?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)')
        ->execute([hash('sha256', $activation), $userId, $challenge]);
    jsonResponse(['ok' => true, 'state' => 'pending_proof', 'activation' => $activation, 'challenge' => $challenge, 'bot_url' => botUrl($activation)]);
}
if ($action === 'device_prove') {
    $value = (string) ($request['activation'] ?? ''); $attempt = activation($pdo, $value);
    $publicKey = (string) ($request['public_key'] ?? ''); $deviceName = trim((string) ($request['device_name'] ?? ''));
    $signature = base64_decode((string) ($request['signature'] ?? ''), true);
    if (!$attempt || $signature === false || $deviceName === '' || mb_strlen($deviceName) > 80) jsonResponse(['ok' => false], 400);
    $canonical = "BYGRAM_DEVICE_V1\n$value\n{$attempt['challenge']}\n{$attempt['expected_user_id']}";
    if (openssl_verify($canonical, $signature, publicKeyPem($publicKey), OPENSSL_ALGO_SHA256) !== 1) jsonResponse(['ok' => false], 401);
    $fingerprint = hash('sha256', base64_decode($publicKey, true));
    $pdo->prepare('UPDATE device_activations SET public_key=?,key_fingerprint=?,device_name=?,proof_at=UTC_TIMESTAMP() WHERE id=?')
        ->execute([$publicKey, $fingerprint, mb_substr($deviceName, 0, 80), $attempt['id']]);
    // The Telegram account was already verified when this exact device key was registered.
    // A fresh proof of possession is enough to restore its session without another bot visit.
    $known = $pdo->prepare('SELECT id FROM licensed_devices WHERE key_fingerprint=? AND telegram_id=? AND revoked_at IS NULL');
    $known->execute([$fingerprint, $attempt['expected_user_id']]);
    if ($known->fetchColumn() !== false) {
        $attempt['public_key'] = $publicKey;
        $attempt['key_fingerprint'] = $fingerprint;
        $attempt['device_name'] = mb_substr($deviceName, 0, 80);
        jsonResponse(activateDevice($pdo, $attempt));
    }
    jsonResponse(['ok' => true, 'state' => empty($attempt['telegram_confirmed_at']) ? 'pending_telegram' : 'pending', 'bot_url' => botUrl($value)]);
}
if ($action === 'device_status' || $action === 'device_replace') {
    $value = (string) ($request['activation'] ?? ''); $attempt = activation($pdo, $value);
    if (!$attempt) jsonResponse(['ok' => false], 404);
    if (empty($attempt['proof_at'])) jsonResponse(['ok' => true, 'state' => 'pending_proof', 'bot_url' => botUrl($value)]);
    $replaceId = $action === 'device_replace' ? (string) ($request['device_id'] ?? '') : null;
    if ($replaceId !== null && !preg_match('/^[a-f0-9]{32}$/D', $replaceId)) jsonResponse(['ok' => false], 400);
    $signedValue = $action === 'device_replace' ? "BYGRAM_REPLACE_V1\n$value\n$replaceId" : "BYGRAM_STATUS_V1\n$value";
    $signature = base64_decode((string) ($request['signature'] ?? ''), true);
    if ($signature === false || openssl_verify($signedValue, $signature, publicKeyPem((string) $attempt['public_key']), OPENSSL_ALGO_SHA256) !== 1) jsonResponse(['ok' => false], 401);
    if (!licensed((int) $attempt['expected_user_id'])) jsonResponse(['ok' => true, 'state' => 'denied']);
    if ($action === 'device_status' && !empty($attempt['key_fingerprint'])) {
        $known = $pdo->prepare('SELECT id FROM licensed_devices WHERE key_fingerprint=? AND telegram_id=? AND revoked_at IS NULL');
        $known->execute([$attempt['key_fingerprint'], $attempt['expected_user_id']]);
        if ($known->fetchColumn() !== false) jsonResponse(activateDevice($pdo, $attempt));
    }
    if (empty($attempt['telegram_confirmed_at'])) jsonResponse(['ok' => true, 'state' => 'pending_telegram', 'bot_url' => botUrl($value)]);
    jsonResponse(activateDevice($pdo, $attempt, $replaceId));
}
if ($action === 'refresh') {
    $refresh = (string) ($request['refresh_token'] ?? '');
    $q = $pdo->prepare('SELECT s.id,s.telegram_id,s.device_id,d.public_key FROM activity_sessions s JOIN licensed_devices d ON d.id=s.device_id AND d.revoked_at IS NULL WHERE s.refresh_hash=? AND s.refresh_expires_at>UTC_TIMESTAMP() AND s.revoked_at IS NULL');
    $q->execute([hash('sha256', $refresh)]); $session = $refresh === '' ? false : $q->fetch();
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0); $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    if (!$session || !verifyRequestSignature((string) $session['public_key'], $raw, $timestamp, $nonce) || !licensed((int) $session['telegram_id'])) jsonResponse(['ok' => false], 401);
    try { $pdo->prepare('INSERT INTO api_nonces (session_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$session['id'], $nonce]); }
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
if ($action === 'typing_get') {
    rateLimit($pdo, 'typing_get:' . $ip, 120);
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$ownerId || $ownerId < 1) jsonResponse(['ok' => false], 400);
    $query = $pdo->prepare('SELECT statuses FROM typing_statuses WHERE telegram_id=?');
    $query->execute([$ownerId]);
    $statuses = json_decode((string) ($query->fetchColumn() ?: '{}'), true);
    jsonResponse(['ok' => true, 'statuses' => is_array($statuses) ? $statuses : []]);
}
if ($action === 'typing_register') {
    rateLimit($pdo, 'typing_register:' . $ip, 12);
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    $publicKey = (string) ($request['public_key'] ?? '');
    $proof = base64_decode((string) ($request['proof'] ?? ''), true);
    if (!$userId || $userId < 1 || $proof === false || openssl_verify("BYGRAM_TYPING_V1\n$userId", $proof, publicKeyPem($publicKey), OPENSSL_ALGO_SHA256) !== 1) jsonResponse(['ok' => false], 400);
    $activation = token();
    $pdo->prepare('INSERT INTO typing_activations (token_hash,telegram_id,public_key,expires_at) VALUES (?,?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)')
        ->execute([hash('sha256', $activation), $userId, $publicKey]);
    jsonResponse(['ok' => true, 'bot_url' => 'https://t.me/' . rawurlencode(appSetting('bot_username')) . '?start=typing_' . rawurlencode($activation)]);
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
    $query = $pdo->prepare('SELECT public_key FROM typing_devices WHERE telegram_id=?');
    $query->execute([$userId]);
    $timestamp = (int) ($_SERVER['HTTP_X_BYGRAM_TIMESTAMP'] ?? 0); $nonce = (string) ($_SERVER['HTTP_X_BYGRAM_NONCE'] ?? '');
    $authorized = false;
    foreach ($query->fetchAll(PDO::FETCH_COLUMN) as $publicKey) {
        if (verifyRequestSignature((string) $publicKey, $raw, $timestamp, $nonce)) { $authorized = true; break; }
    }
    if (!$authorized) jsonResponse(['ok' => false, 'needs_registration' => true], 401);
    try { $pdo->prepare('INSERT INTO typing_nonces (telegram_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 2 MINUTE)')->execute([$userId,$nonce]); }
    catch (PDOException) { jsonResponse(['ok' => false], 409); }
    $pdo->prepare('INSERT INTO typing_statuses (telegram_id,statuses) VALUES (?,?) ON DUPLICATE KEY UPDATE statuses=VALUES(statuses)')
        ->execute([$userId,json_encode($statuses,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES)]);
    jsonResponse(['ok' => true]);
}

$session = authenticatedSession($pdo, $raw); $userId = (int) $session['telegram_id'];
rateLimit($pdo, 'session:' . $session['id'], 120);
$license = $pdo->prepare("SELECT valid_until FROM licenses WHERE telegram_id=? AND status='active' AND (valid_until IS NULL OR valid_until>UTC_TIMESTAMP())");
$license->execute([$userId]); $licenseRow = $license->fetch(); $licensed = $licenseRow !== false;
if ($action === 'license') jsonResponse(['ok' => true, 'licensed' => $licensed, 'valid_until' => $licenseRow['valid_until'] ?? 0, 'manager_url' => managerUrl()]);
if (!$licensed) jsonResponse(['ok' => false, 'licensed' => false], 403);

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
