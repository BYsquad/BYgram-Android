<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-catalog.php';

header('Cache-Control: no-store');
$method = $_SERVER['REQUEST_METHOD'] ?? '';
if ($method === 'GET' && !isset($_GET['view']) && !isset($_GET['download'])) {
    header('Location: admin.php?page=plugins', true, 302);
    exit;
}
try {
    $admin = requireAdminSession();
    if ($method === 'GET' && isset($_GET['download'])) {
        $id = filter_var($_GET['download'], FILTER_VALIDATE_INT);
        $query = db()->prepare("SELECT candidate FROM plugin_submissions WHERE id=? AND status='review'");
        $query->execute([$id ?: 0]);
        $candidate = json_decode((string) $query->fetchColumn(), true);
        $file = $candidate['file'] ?? '';
        if (!preg_match('/^[a-f0-9]{64}\.byplugin$/D', $file)) { http_response_code(404); exit; }
        $path = pluginCandidateDirectory() . '/' . $file;
        if (!is_file($path)) { http_response_code(404); exit; }
        header('Content-Type: application/octet-stream');
        header('Content-Disposition: attachment; filename="' . $file . '"');
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        exit;
    }
    if ($method === 'GET' && ($_GET['view'] ?? '') === 'list') {
        $rows = db()->query('SELECT id,telegram_id,repository,status,release_tag,candidate,review_note
            FROM plugin_submissions ORDER BY id DESC LIMIT 100')->fetchAll();
        foreach ($rows as &$row) {
            $row['candidate'] = json_decode((string) $row['candidate'], true);
        }
        unset($row);
        jsonResponse(['ok' => true, 'data' => $rows]);
    }
    if ($method !== 'POST') jsonResponse(['ok' => false], 405);
    requireAdminCsrf();
    $input = json_decode((string) file_get_contents('php://input'), true) ?: [];
    $id = filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$id || $id < 1) throw new InvalidArgumentException('Неверная заявка');
    $action = $input['action'] ?? '';
    if ($action === 'prepare') {
        preparePluginReview(db(), $id);
    } elseif ($action === 'approve' && ($input['reviewed'] ?? null) === true) {
        approvePlugin(db(), $id, $admin, (string) ($input['hash'] ?? ''));
    } elseif ($action === 'reject' || $action === 'revoke') {
        $note = trim((string) ($input['note'] ?? ''));
        if ($note === '' || mb_strlen($note) > 1000) throw new InvalidArgumentException('Укажите причину, до 1000 символов');
        if ($action === 'revoke') {
            revokePlugin(db(), $id, $admin, (string) ($input['hash'] ?? ''), $note);
        } else {
            $query = db()->prepare("UPDATE plugin_submissions SET status='rejected',review_note=?,reviewed_by=?,reviewed_at=UTC_TIMESTAMP()
                WHERE id=? AND status IN ('pending','review')");
            $query->execute([$note, $admin, $id]);
            if ($query->rowCount() !== 1) throw new InvalidArgumentException('Статус заявки уже изменился');
        }
    } else {
        throw new InvalidArgumentException('Подтвердите проверку кода и пакета');
    }
    jsonResponse(['ok' => true]);
} catch (AdminAuthenticationException $error) {
    jsonResponse(['ok' => false, 'error' => $error->getMessage()], 401);
} catch (Throwable $error) {
    jsonResponse(['ok' => false, 'error' => $error->getMessage()], 422);
}
