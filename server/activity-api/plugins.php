<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-catalog.php';

header('Cache-Control: no-store');
$method = $_SERVER['REQUEST_METHOD'] ?? '';
if ($method === 'GET' && !isset($_GET['view']) && !isset($_GET['download']) && !isset($_GET['paid_download'])) {
    header('Location: admin.php?page=plugins', true, 302);
    exit;
}
try {
    $admin = requireAdminSession();
    $paid = (appConfig()['plugin_store_enabled'] ?? false) === true;
    if ($paid) require __DIR__ . '/plugin-store.php';
    if ($method === 'GET' && $paid && isset($_GET['paid_download'])) {
        $hash = $_GET['paid_download'];
        $row = is_string($hash) && preg_match('/^[a-f0-9]{64}$/D', $hash)
            ? storeRow(db(), "SELECT archive FROM plugin_store_releases WHERE sha256=? AND status='review'", [$hash]) : false;
        if (!$row) { http_response_code(404); exit; }
        header('Content-Type: application/octet-stream');
        header('X-Content-Type-Options: nosniff');
        header('Content-Disposition: attachment; filename="' . $hash . '.byplugin"');
        echo $row['archive'];
        exit;
    }
    if ($method === 'GET' && $paid && ($_GET['view'] ?? '') === 'payment-report') {
        if (!isOwner($admin)) { http_response_code(403); exit; }
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="bygram-plugin-payments.csv"');
        $out = fopen('php://output', 'wb');
        fputcsv($out, ['order','plugin','author','method','merchant','charge','amount','author_amount','status','timestamp'], ',', '"', '');
        $q = db()->query('SELECT o.id,o.plugin_id,o.author_id,p.method,p.merchant,p.charge,p.amount,p.author_amount,p.status,p.paid_at
            FROM plugin_store_payments p LEFT JOIN plugin_store_orders o ON p.order_id=o.id ORDER BY p.paid_at');
        while ($row = $q->fetch(PDO::FETCH_NUM)) {
            foreach ($row as &$value) if (is_string($value) && preg_match('/^[=+@\-\t\r]/', $value)) $value = "'" . $value;
            unset($value);
            fputcsv($out, $row, ',', '"', '');
        }
        exit;
    }
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
        if ($paid) {
            $releases = db()->query("SELECT r.sha256 AS id,p.author_id AS telegram_id,r.plugin_id AS repository,
                r.version AS release_tag,IF(r.status='published','approved',r.status) AS status,r.candidate,
                r.review_note,r.stars,r.nano,p.listed,p.current_hash FROM plugin_store_releases r JOIN plugin_store_products p USING (plugin_id)
                ORDER BY r.created_at DESC LIMIT 100")->fetchAll();
            foreach ($releases as $release) {
                $release['candidate'] = json_decode($release['candidate'], true, 32, JSON_THROW_ON_ERROR);
                $release['paid'] = true;
                $rows[] = $release;
            }
        }
        jsonResponse(['ok' => true, 'data' => $rows]);
    }
    if ($method !== 'POST') jsonResponse(['ok' => false], 405);
    requireAdminCsrf();
    $input = json_decode((string) file_get_contents('php://input'), true) ?: [];
    if ($paid && ($input['paid'] ?? false) === true) {
        $hash = $input['hash'] ?? '';
        $action = $input['action'] ?? '';
        if (!is_string($hash) || !preg_match('/^[a-f0-9]{64}$/D', $hash) || $hash !== ($input['id'] ?? null)
            || ($action === 'approve' && ($input['reviewed'] ?? false) !== true)) throw new InvalidArgumentException('Проверьте точный пакет');
        if (in_array($action, ['pause', 'resume'], true)) {
            storeWrite(db(), 'UPDATE plugin_store_products SET listed=? WHERE current_hash=?', [$action === 'resume' ? 1 : 0, $hash]);
            jsonResponse(['ok'=>true]);
        }
        storeReview(db(), $hash, $admin, (string) $action, trim((string) ($input['note'] ?? '')));
        jsonResponse(['ok'=>true]);
    }
    $id = filter_var($input['id'] ?? null, FILTER_VALIDATE_INT);
    if (!$id || $id < 1) throw new InvalidArgumentException('Неверная заявка');
    $action = $input['action'] ?? '';
    if ($action === 'prepare') {
        preparePluginReview(db(), $id);
    } elseif ($action === 'approve' && ($input['reviewed'] ?? null) === true) {
        if ($paid && storeRow(db(), "SELECT p.plugin_id FROM plugin_submissions s JOIN plugin_store_products p
            ON p.plugin_id=JSON_UNQUOTE(JSON_EXTRACT(s.candidate,'$.manifest.id')) WHERE s.id=? AND p.current_hash IS NOT NULL", [$id]))
            throw new InvalidArgumentException('Этот ID уже используется в платном каталоге');
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
