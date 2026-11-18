<?php
declare(strict_types=1);

const PLUGIN_STORE_TERMS = '2026-11-18-r2';
const PLUGIN_STORE_NOTICE = 'После подтверждения оплаты и предоставления доступа к плагину возврат средств по желанию покупателя невозможен. При проблеме с оплатой обратитесь в поддержку BYgram. Обязательные возвраты рассматриваются по закону и правилам платёжной платформы.';
const PLUGIN_STORE_TON_NOTICE = 'При оплате TON средства поступают напрямую автору плагина. Команда BYgram не осуществляет возвраты таких платежей и не несёт ответственности за возврат средств, полученных автором, в пределах, допускаемых законом. Вопросы обязательного возврата можно передать автору через поддержку BYgram.';

function storeRow(PDO $pdo, string $sql, array $args): array|false {
    $q = $pdo->prepare($sql);
    $q->execute($args);
    return $q->fetch(PDO::FETCH_ASSOC);
}
function storeWrite(PDO $pdo, string $sql, array $args): void {
    $pdo->prepare($sql)->execute($args);
}
function storeTransaction(PDO $pdo, callable $work): mixed {
    $pdo->beginTransaction();
    try {
        $result = $work();
        $pdo->commit();
        return $result;
    } catch (Throwable $error) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $error;
    }
}
function storeSubmit(PDO $pdo, int $author, string $archive, int $stars, int $nano): array {
    if ($stars < 0 || $stars > 10000 || $nano < 0 || $nano > 100000000000000 || ($stars === 0 && $nano === 0))
        throw new InvalidArgumentException('Укажите цену хотя бы в одной валюте');
    $candidate = pluginSignedCandidate($archive);
    $id = $candidate['manifest']['id'];
    storeTransaction($pdo, static function () use ($pdo, $author, $id, $candidate, $archive, $stars, $nano) {
        if (!storeRow($pdo, 'SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE', [$author]))
            throw new InvalidArgumentException('Аккаунт не зарегистрирован');
        $q = $pdo->prepare('SELECT COUNT(*) FROM plugin_store_releases r JOIN plugin_store_products p USING (plugin_id)
            WHERE p.author_id=? AND r.created_at>?');
        $q->execute([$author, time() - 86400]);
        if ((int) $q->fetchColumn() >= 5) throw new InvalidArgumentException('До пяти заявок в сутки');
        $product = storeRow($pdo, 'SELECT * FROM plugin_store_products WHERE plugin_id=? FOR UPDATE', [$id]);
        if (!$product) {
            $free = dirname(__DIR__) . '/plugins/index.json';
            if (is_file($free)) {
                $index = json_decode((string) file_get_contents($free), true, 32, JSON_THROW_ON_ERROR);
                foreach ($index['plugins'] as $entry) if ($entry['manifest']['id'] === $id)
                    throw new InvalidArgumentException('Для платной версии нужен новый ID');
            }
            storeWrite($pdo, 'INSERT INTO plugin_store_products (plugin_id,author_id,author_key) VALUES (?,?,?)',
                [$id, $author, $candidate['author_key']]);
        } elseif ((int) $product['author_id'] !== $author || !hash_equals($product['author_key'], $candidate['author_key'])) {
            throw new InvalidArgumentException('ID закреплён за другим автором или ключом');
        }
        $version = $candidate['manifest']['version'];
        if (storeRow($pdo, 'SELECT sha256 FROM plugin_store_releases WHERE sha256=? OR (plugin_id=? AND version=?)',
            [$candidate['sha256'], $id, $version])) throw new InvalidArgumentException('Эта версия уже подана');
        storeWrite($pdo, 'INSERT INTO plugin_store_releases (sha256,plugin_id,version,candidate,archive,stars,nano,created_at)
            VALUES (?,?,?,?,?,?,?,?)', [$candidate['sha256'], $id, $version,
                json_encode($candidate, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE), $archive, $stars, $nano, time()]);
    });
    return $candidate;
}
function storeReview(PDO $pdo, string $hash, int $admin, string $action, string $note): void {
    if (!in_array($action, ['approve', 'reject', 'revoke'], true) || mb_strlen($note) > 1000
        || ($action !== 'approve' && trim($note) === '')) throw new InvalidArgumentException('Укажите решение и причину');
    storeTransaction($pdo, static function () use ($pdo, $hash, $admin, $action, $note) {
        $release = storeRow($pdo, 'SELECT plugin_id,version,status FROM plugin_store_releases WHERE sha256=?', [$hash]);
        if (!$release) throw new InvalidArgumentException('Версия отсутствует');
        $product = storeRow($pdo, 'SELECT * FROM plugin_store_products WHERE plugin_id=? FOR UPDATE', [$release['plugin_id']]);
        $release = storeRow($pdo, 'SELECT plugin_id,version,status FROM plugin_store_releases WHERE sha256=? FOR UPDATE', [$hash]);
        if ($release['status'] !== ($action === 'revoke' ? 'published' : 'review'))
            throw new InvalidArgumentException('Статус версии уже изменился');
        if ($action === 'approve') {
            $history = $pdo->prepare('SELECT version FROM plugin_store_releases WHERE plugin_id=? AND status IN (\'published\',\'revoked\')');
            $history->execute([$release['plugin_id']]);
            foreach ($history->fetchAll(PDO::FETCH_COLUMN) as $version) if (version_compare($release['version'], $version, '<='))
                throw new InvalidArgumentException('Нужна более новая версия');
            storeWrite($pdo, 'UPDATE plugin_store_products SET current_hash=? WHERE plugin_id=?', [$hash, $release['plugin_id']]);
        } elseif ($product['current_hash'] === $hash) {
            storeWrite($pdo, 'UPDATE plugin_store_products SET current_hash=NULL WHERE plugin_id=?', [$release['plugin_id']]);
        }
        storeWrite($pdo, 'UPDATE plugin_store_releases SET status=?,reviewed_by=?,review_note=? WHERE sha256=?',
            [match ($action) { 'approve' => 'published', 'reject' => 'rejected', 'revoke' => 'revoked' }, $admin, $note, $hash]);
    });
}
function storeCatalog(PDO $pdo, array $methods, string $network): array {
    $rows = $pdo->prepare("SELECT r.candidate,r.stars,r.nano,p.listed,w.address FROM plugin_store_products p
        JOIN plugin_store_releases r ON r.sha256=p.current_hash
        LEFT JOIN plugin_store_author_wallets w ON w.author_id=p.author_id AND w.network=?
        WHERE r.status='published' ORDER BY p.plugin_id LIMIT 100");
    $rows->execute([$network]);
    $entries = [];
    foreach ($rows as $row) {
        $candidate = json_decode($row['candidate'], true, 32, JSON_THROW_ON_ERROR);
        $candidate['purchase'] = [
            'stars' => $row['listed'] && in_array('stars', $methods, true) ? (int) $row['stars'] : 0,
            'nano' => $row['listed'] && $row['address'] !== null && in_array('ton', $methods, true) ? (string) $row['nano'] : '0',
        ];
        unset($candidate['file']);
        $entries[] = $candidate;
    }
    return $entries;
}
function storeMethods(array $config): array {
    if (($config['plugin_store_enabled'] ?? false) !== true || ($config['plugin_store_sales'] ?? true) !== true) return [];
    $methods = [];
    $live = ($config['plugin_store_live'] ?? false) === true;
    if (!empty($config['plugin_store_stars_token']) && !empty($config['plugin_store_stars_secret'])
        && (($config['plugin_store_stars_test'] ?? true) || $live)) $methods[] = 'stars';
    if (str_starts_with($config['plugin_store_url'] ?? '', 'https://')
        && (storeConfiguredTonNetwork($config) === '-3' || $live)) $methods[] = 'ton';
    return $methods;
}
function storeConfiguredTonNetwork(array $config): string {
    return ($config['plugin_store_ton_test'] ?? true) ? '-3' : '-239';
}
function storeTonRecipient(string $merchant): ?array {
    return preg_match('/^ton:(-3|-239):(0:[0-9a-f]{64})$/D', $merchant, $match) ? [$match[1], $match[2]] : null;
}
function storeMerchant(array $config, string $method): string {
    if ($method === 'ton') {
        $network = storeConfiguredTonNetwork($config);
        return 'ton:' . $network . ':' . ($config['plugin_store_ton_address'] ?? '');
    }
    $environment = ($config['plugin_store_stars_test'] ?? true) ? 'test' : 'live';
    return 'stars:' . $environment . ':' . explode(':', (string) ($config['plugin_store_stars_token'] ?? ''), 2)[0];
}
function storeCreateOrder(PDO $pdo, int $buyer, string $hash, string $method, string $clientKey, string $terms, array $config): array {
    if ($terms !== PLUGIN_STORE_TERMS || !preg_match('/^[0-9a-f]{32}$/D', $clientKey) || !in_array($method, storeMethods($config), true))
        throw new InvalidArgumentException('Способ оплаты недоступен или условия изменились');
    return storeTransaction($pdo, static function () use ($pdo, $buyer, $hash, $method, $clientKey, $terms, $config) {
        if (!storeRow($pdo, 'SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE', [$buyer]))
            throw new InvalidArgumentException('Аккаунт не зарегистрирован');
        $retry = storeRow($pdo, 'SELECT * FROM plugin_store_orders WHERE buyer=? AND client_key=?', [$buyer, $clientKey]);
        if ($retry) {
            if ($retry['release_hash'] !== $hash || $retry['method'] !== $method) throw new InvalidArgumentException('Повтор запроса изменился');
            return $retry;
        }
        $offer = storeRow($pdo, "SELECT r.plugin_id,r.stars,r.nano,p.author_id FROM plugin_store_releases r JOIN plugin_store_products p ON p.current_hash=r.sha256
            WHERE r.sha256=? AND r.status='published' AND p.listed=1 FOR UPDATE", [$hash]);
        $amount = (int) ($offer[$method === 'stars' ? 'stars' : 'nano'] ?? 0);
        if ($amount < 1) throw new InvalidArgumentException('Предложение недоступно. Обновите каталог.');
        if (storeRow($pdo, 'SELECT order_id FROM plugin_store_entitlements WHERE buyer=? AND plugin_id=?', [$buyer, $offer['plugin_id']]))
            throw new InvalidArgumentException('Плагин уже приобретён');
        $pending = storeRow($pdo, "SELECT * FROM plugin_store_orders WHERE buyer=? AND plugin_id=? AND status='pending'
            AND expires_at>? ORDER BY created_at DESC LIMIT 1 FOR UPDATE", [$buyer, $offer['plugin_id'], time()]);
        if ($pending) {
            if ($pending['method'] === $method && $pending['release_hash'] === $hash) return $pending;
            throw new InvalidArgumentException('Уже есть незавершённый заказ. Дождитесь истечения его срока.');
        }
        $merchant = storeMerchant($config, $method);
        if ($method === 'ton') {
            $network = storeConfiguredTonNetwork($config);
            $wallet = storeRow($pdo, 'SELECT address FROM plugin_store_author_wallets WHERE author_id=? AND network=?',
                [$offer['author_id'],$network]);
            if (!$wallet) throw new InvalidArgumentException('Автор ещё не подключил TON-кошелёк для этой сети.');
            $merchant = 'ton:' . $network . ':' . $wallet['address'];
        }
        $id = bin2hex(random_bytes(16));
        $now = time();
        // Direct TON payments are already in the author's wallet and are not payable again.
        $rate = $method === 'ton' ? 0 : (int) ($config['plugin_store_author_bps'] ?? 8000);
        if ($rate < 0 || $rate > 10000) throw new RuntimeException('Invalid author share');
        storeWrite($pdo, 'INSERT INTO plugin_store_orders
            (id,buyer,author_id,plugin_id,release_hash,method,merchant,amount,author_bps,terms_version,created_at,expires_at,client_key)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)', [$id,$buyer,$offer['author_id'],$offer['plugin_id'],$hash,$method,$merchant,$amount,
                $rate,$terms,$now,$now + 900,$clientKey]);
        return storeRow($pdo, 'SELECT * FROM plugin_store_orders WHERE id=?', [$id]);
    });
}
function storeCanCheckout(PDO $pdo, string $id, string $checkout, int $buyer, string $currency, int $amount, string $merchant): bool {
    return storeTransaction($pdo, static function () use ($pdo, $id, $checkout, $buyer, $currency, $amount, $merchant) {
        $order = storeRow($pdo, 'SELECT * FROM plugin_store_orders WHERE id=? FOR UPDATE', [$id]);
        if (!$order || $order['method'] !== 'stars' || $order['merchant'] !== $merchant || (int) $order['buyer'] !== $buyer || $currency !== 'XTR'
            || (int) $order['amount'] !== $amount) return false;
        if ($order['checkout_id'] !== null) return $order['checkout_id'] === $checkout;
        if ($order['status'] !== 'pending' || (int) $order['expires_at'] <= time()
            || !storeRow($pdo, "SELECT sha256 FROM plugin_store_releases WHERE sha256=? AND status='published'", [$order['release_hash']])
            || storeRow($pdo, 'SELECT order_id FROM plugin_store_entitlements WHERE buyer=? AND plugin_id=?', [$buyer, $order['plugin_id']])) return false;
        storeWrite($pdo, 'UPDATE plugin_store_orders SET checkout_id=? WHERE id=?', [$checkout, $id]);
        return true;
    });
}
function storeSettle(PDO $pdo, string $id, string $method, string $charge, string $payer, int $amount, int $paidAt,
    string $merchant, bool $refund = false, ?string $refundReference = null): string {
    if (!in_array($method, ['stars', 'ton'], true) || $charge === '' || strlen($charge) > 128 || strlen($payer) > 128 || $amount < 1)
        throw new InvalidArgumentException('Некорректная запись оплаты');
    return storeTransaction($pdo, static function () use ($pdo, $id, $method, $charge, $payer, $amount, $paidAt, $merchant, $refund, $refundReference) {
        $parties = storeRow($pdo, 'SELECT buyer,author_id FROM plugin_store_orders WHERE id=? AND method=? AND merchant=?', [$id, $method, $merchant]);
        if (!$parties) return 'unknown';
        sort($parties, SORT_NUMERIC);
        foreach (array_unique($parties) as $party) storeRow($pdo, 'SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE', [$party]);
        $order = storeRow($pdo, 'SELECT * FROM plugin_store_orders WHERE id=? FOR UPDATE', [$id]);
        $correctBuyer = $method === 'ton' || (string) $order['buyer'] === $payer;
        $previous = storeRow($pdo, 'SELECT * FROM plugin_store_payments WHERE method=? AND merchant=? AND charge=? FOR UPDATE', [$method, $merchant, $charge]);
        if ($previous && $previous['order_id'] !== $id) return 'mismatch';
        if ($previous && (!$refund || $previous['status'] === 'refunded')) return $previous['status'];
        if ($refund) {
            if ($previous && ((string) $previous['payer'] !== $payer || (int) $previous['amount'] !== $amount)) return 'mismatch';
            if (!$previous && (!$correctBuyer || (int) $order['amount'] !== $amount)) return 'mismatch';
            if ($previous) {
                storeWrite($pdo, "UPDATE plugin_store_payments SET status='refunded',refund_reference=? WHERE method=? AND merchant=? AND charge=?",
                    [$refundReference,$method,$merchant,$charge]);
                if ($previous['status'] === 'paid') {
                    storeWrite($pdo, 'DELETE FROM plugin_store_entitlements WHERE buyer=? AND plugin_id=? AND order_id=?',
                        [$order['buyer'], $order['plugin_id'], $id]);
                    storeWrite($pdo, "UPDATE plugin_store_orders SET status='refunded' WHERE id=?", [$id]);
                }
            } else {
                storeWrite($pdo, "INSERT INTO plugin_store_payments (method,merchant,charge,order_id,amount,payer,status,paid_at)
                    VALUES (?,?,?,?,?,?,'refunded',?)", [$method,$merchant,$charge,$id,$amount,$payer,$paidAt]);
                if ($order['status'] === 'pending') storeWrite($pdo, "UPDATE plugin_store_orders SET status='refunded' WHERE id=?", [$id]);
            }
            return 'refunded';
        }
        $valid = $correctBuyer && (int) $order['amount'] === $amount && $order['status'] === 'pending'
            && ($method === 'stars' ? $order['checkout_id'] !== null : $paidAt >= (int) $order['created_at'] && $paidAt <= (int) $order['expires_at'])
            && storeRow($pdo, "SELECT sha256 FROM plugin_store_releases WHERE sha256=? AND status='published'", [$order['release_hash']])
            && !storeRow($pdo, 'SELECT order_id FROM plugin_store_entitlements WHERE buyer=? AND plugin_id=?', [$order['buyer'], $order['plugin_id']]);
        $status = $valid ? 'paid' : 'review';
        $authorAmount = $valid ? intdiv($amount * (int) $order['author_bps'], 10000) : 0;
        storeWrite($pdo, 'INSERT INTO plugin_store_payments (method,merchant,charge,order_id,amount,author_amount,payer,status,paid_at)
            VALUES (?,?,?,?,?,?,?,?,?)', [$method,$merchant,$charge,$id,$amount,$authorAmount,$payer,$status,$paidAt]);
        if ($valid) {
            storeWrite($pdo, 'INSERT INTO plugin_store_entitlements (buyer,plugin_id,order_id) VALUES (?,?,?)', [$order['buyer'], $order['plugin_id'], $id]);
            storeWrite($pdo, "UPDATE plugin_store_orders SET status='paid' WHERE id=?", [$id]);
        }
        return $status;
    });
}
function storeDownload(PDO $pdo, int $buyer, string $hash): ?string {
    $row = storeRow($pdo, "SELECT r.archive FROM plugin_store_releases r JOIN plugin_store_entitlements e USING (plugin_id)
        WHERE r.sha256=? AND e.buyer=? AND r.status='published'", [$hash, $buyer]);
    return $row ? $row['archive'] : null;
}
function storeAuthorBalance(PDO $pdo, int $author, string $method, string $merchant): int {
    $earned = storeRow($pdo, "SELECT COALESCE(SUM(p.author_amount),0) AS total FROM plugin_store_payments p
        JOIN plugin_store_orders o ON o.id=p.order_id WHERE o.author_id=? AND o.method=? AND o.merchant=? AND p.status='paid'",
        [$author,$method,$merchant]);
    $paid = storeRow($pdo, 'SELECT COALESCE(SUM(amount),0) AS total FROM plugin_store_payouts WHERE author=? AND method=? AND merchant=?',
        [$author,$method,$merchant]);
    return (int) $earned['total'] - (int) $paid['total'];
}
function storeRecordPayout(PDO $pdo, int $author, string $method, string $merchant, int $amount, string $reference): void {
    if (!in_array($method, ['stars','ton'], true) || $amount < 1 || trim($reference) === '' || strlen($reference) > 128)
        throw new InvalidArgumentException('Некорректная выплата');
    storeTransaction($pdo, static function () use ($pdo,$author,$method,$merchant,$amount,$reference) {
        if (!storeRow($pdo, 'SELECT telegram_id FROM users WHERE telegram_id=? FOR UPDATE', [$author]))
            throw new InvalidArgumentException('Автор отсутствует');
        $previous = storeRow($pdo, 'SELECT author,amount FROM plugin_store_payouts WHERE method=? AND merchant=? AND reference=?',
            [$method,$merchant,$reference]);
        if ($previous) {
            if ((int) $previous['author'] !== $author || (int) $previous['amount'] !== $amount)
                throw new InvalidArgumentException('Реквизиты выплаты изменились');
            return;
        }
        if ($amount > storeAuthorBalance($pdo,$author,$method,$merchant))
            throw new InvalidArgumentException('Сумма больше доступного остатка');
        storeWrite($pdo, 'INSERT INTO plugin_store_payouts (author,method,merchant,reference,amount,paid_at) VALUES (?,?,?,?,?,?)',
            [$author,$method,$merchant,$reference,$amount,time()]);
    });
}
