<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-store.php';
require __DIR__ . '/plugin-store-providers.php';

$config = appConfig();
$base = rtrim($config['plugin_store_url'] ?? '', '/');
header('Cache-Control: private, no-store');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
if (isset($_GET['manifest']) && str_starts_with($base, 'https://')) {
    header('Access-Control-Allow-Origin: *');
    $manifest = ['url'=>dirname($base), 'name'=>'BYgram',
        'iconUrl'=>$config['plugin_store_icon_url'] ?? dirname($base) . '/assets/bygram-logo.png'];
    if (str_starts_with($config['terms_url'] ?? '', 'https://')) $manifest['termsOfUseUrl'] = $config['terms_url'];
    jsonResponse($manifest);
}
$id = $_GET['order'] ?? '';
if (!isset($_GET['order']) && ($config['plugin_store_enabled'] ?? false) === true) {
    require __DIR__ . '/plugin-store-front.php';
    exit;
}
if (($config['plugin_store_enabled'] ?? false) !== true || !is_string($id) || !preg_match('/^[a-f0-9]{32}$/D', $id)) {
    http_response_code(404); exit;
}
if (isset($_GET['status'])) {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') jsonResponse(['ok'=>false],405);
    $order = storeRow(db(), "SELECT method,status,merchant,expires_at FROM plugin_store_orders WHERE id=? AND method='ton'", [$id]);
    if (!$order) jsonResponse(['ok'=>false],404);
    storePollTonOrder(db(),$order,$config);
    $state = storeRow(db(),'SELECT status,expires_at FROM plugin_store_orders WHERE id=?',[$id]);
    $status = $state['status'] === 'pending' && (int) $state['expires_at'] < time() ? 'expired' : $state['status'];
    jsonResponse(['ok'=>true,'status'=>$status]);
}
if (!in_array('ton', storeMethods($config), true)) { http_response_code(404); exit; }
$order = storeRow(db(), "SELECT o.*,r.candidate FROM plugin_store_orders o JOIN plugin_store_releases r ON r.sha256=o.release_hash
    WHERE o.id=? AND o.method='ton' AND o.status='pending' AND r.status='published'", [$id]);
$recipient = $order ? storeTonRecipient($order['merchant']) : null;
if (!$order || $recipient === null || $recipient[0] !== storeConfiguredTonNetwork($config) || (int) $order['expires_at'] <= time()) {
    http_response_code(410);
    header('Content-Type: text/plain; charset=utf-8');
    exit('Заказ завершён или просрочен. Вернитесь в BYgram.');
}
[$network,$address] = $recipient;
$direct = $order['terms_version'] !== '2026-11-18';
$transaction = ['validUntil'=>(int) $order['expires_at'], 'network'=>$network, 'messages'=>[[
    'address'=>storeTonFriendly($address, $network), 'amount'=>(string) $order['amount'], 'payload'=>storeTonPayload($id),
]]];
$manifestUrl = $base . '/plugin-store-web.php?manifest';
$title = json_decode($order['candidate'], true, 32, JSON_THROW_ON_ERROR)['manifest']['name'];
$amount = str_pad((string) $order['amount'], 10, '0', STR_PAD_LEFT);
$amount = rtrim(rtrim(substr($amount, 0, -9) . '.' . substr($amount, -9), '0'), '.');
$nonce = base64_encode(random_bytes(18));
header("Content-Security-Policy: default-src 'none'; script-src 'nonce-$nonce' https://cdn.jsdelivr.net; style-src 'unsafe-inline'; img-src https: data: blob:; connect-src https:; frame-ancestors 'none'; base-uri 'none'");
$escape = static fn($s) => htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
?>
<!doctype html>
<html lang="ru">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Оплата плагина · BYgram</title>
<style>
:root { color-scheme: light dark; font-family: system-ui, sans-serif }
main { max-width: 34rem; margin: 10vh auto; padding: 0 1rem }
button { font: inherit; padding: .6rem 1rem; margin-top: .8rem }
</style>
<main>
  <h1><?= $escape($title) ?></h1>
  <p><?= $escape($amount) ?> TON<?= $network === '-3' ? ' · Testnet' : '' ?></p>
  <p>Заказ <code><?= $escape($id) ?></code></p>
  <p><?= $direct ? 'Кошелёк автора' : 'Кошелёк получателя' ?> <code><?= $escape($transaction['messages'][0]['address']) ?></code></p>
  <label><input id="terms" type="checkbox"> <?= $escape(PLUGIN_STORE_NOTICE . ($direct ? ' ' . PLUGIN_STORE_TON_NOTICE : '')) ?></label>
  <div id="wallet"></div>
  <button id="pay" type="button" disabled>Подтвердить перевод</button>
  <p id="status" role="status">Подключите кошелёк, чтобы продолжить.</p>
</main>
<script crossorigin="anonymous" integrity="sha384-v4PykhWGxgBFLxXCX+QGernI2uQLQGT9VcIBfnMBa0NRcwjWsOoseT4Cc9ljakW7" src="https://cdn.jsdelivr.net/npm/@tonconnect/ui@2.2.0/dist/tonconnect-ui.min.js"></script>
<script nonce="<?= $nonce ?>">
const tx = <?= json_encode($transaction, JSON_HEX_TAG | JSON_THROW_ON_ERROR) ?>;
if (window.TON_CONNECT_UI) {
const checkout = new TON_CONNECT_UI.TonConnectUI({
  manifestUrl: <?= json_encode($manifestUrl, JSON_HEX_TAG | JSON_THROW_ON_ERROR) ?>,
  buttonRootId: 'wallet'
});
const pay = document.getElementById('pay');
const status = document.getElementById('status');
const terms = document.getElementById('terms');
const orderId = <?= json_encode($id, JSON_THROW_ON_ERROR) ?>;
const key = 'bygram-sent:' + orderId;
let sent = sessionStorage.getItem(key) === 'yes';
let sending = false;
let confirmed = false;
let checking = false;
async function checkPayment() {
  if (checking || confirmed || Date.now() / 1000 > tx.validUntil + 3600) return;
  checking = true;
  try {
    const response = await fetch('?order=' + orderId + '&status=1', {cache:'no-store'});
    const order = await response.json();
    if (response.ok && order.status === 'paid') {
      confirmed = sent = true;
      refresh();
    }
  } catch {
    // A temporary indexer failure leaves the order available for another check.
  } finally {
    checking = false;
  }
}
checkout.onStatusChange(wallet => {
  refresh(wallet);
});
function refresh(wallet = checkout.wallet) {
  pay.disabled = sent || sending || !terms.checked || wallet?.account.chain !== tx.network;
  if (confirmed) status.textContent = 'Оплата подтверждена. Плагин доступен в разделе «Мои покупки» BYgram.';
  else if (sent) status.textContent = 'Перевод отправлен. Ожидаем подтверждения сети.';
  else if (!wallet) status.textContent = 'Подключите кошелёк, чтобы продолжить.';
  else if (wallet.account.chain !== tx.network) status.textContent = 'Переключите кошелёк на сеть из заказа.';
  else status.textContent = '';
}
terms.onchange = () => refresh();
refresh();
setInterval(() => { if (sent) void checkPayment(); }, 10000);
if (sent) void checkPayment();
pay.onclick = async () => {
  if (pay.disabled) return;
  sending = true;
  refresh();
  try {
    await checkout.sendTransaction(tx);
    sent = true;
    sessionStorage.setItem(key, 'yes');
    refresh();
    void checkPayment();
  } catch (error) {
    status.textContent = 'Проверьте историю кошелька перед повторной отправкой.';
    sending = false;
    pay.disabled = !terms.checked || checkout.wallet?.account.chain !== tx.network;
  }
};
} else {
  document.getElementById('status').textContent = 'Модуль кошелька недоступен. Обновите страницу.';
}
</script>
</html>
