<?php
declare(strict_types=1);

$pdo = db();
$route = ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' ? ($_GET['action'] ?? 'order') : 'view';
$cookiePath = rtrim(dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/')), '/') . '/';
session_name('bygram_plugin_checkout');
ini_set('session.use_strict_mode', '1');
session_set_cookie_params(['path'=>$cookiePath,'secure'=>true,'httponly'=>true,'samesite'=>'Lax']);
session_start();
$csrf = $_SESSION['csrf'] ??= bin2hex(random_bytes(32));
$userId = (int) ($_SESSION['expires_at'] ?? 0) > time() ? (int) ($_SESSION['telegram_id'] ?? 0) : 0;
$network = storeConfiguredTonNetwork($config);
$walletPage = ($_GET['wallet'] ?? null) === '1';
$error = null;
if (in_array($route, ['wallet_challenge','wallet_bind','wallet_remove'], true)) {
    $given = $_SERVER['HTTP_X_BYGRAM_CSRF'] ?? $_POST['csrf'] ?? '';
    if (!$userId || !is_string($given) || !hash_equals($csrf, $given)) jsonResponse(['ok'=>false], 403);
    if ($route === 'wallet_remove') {
        unset($_SESSION['wallet_proof']);
        storeWrite($pdo, 'DELETE FROM plugin_store_author_wallets WHERE author_id=? AND network=?', [$userId,$network]);
        header('Location: ?wallet=1', true, 303);
        exit;
    }
    if ($route === 'wallet_challenge') {
        if ((int) ($_SESSION['wallet_challenge_at'] ?? 0) > time() - 3) jsonResponse(['ok'=>false], 429);
        $_SESSION['wallet_challenge_at'] = time();
        $challenge = bin2hex(random_bytes(32));
        $_SESSION['wallet_proof'] = ['challenge'=>$challenge,'expires'=>time()+300,'network'=>$network];
        jsonResponse(['ok'=>true,'challenge'=>$challenge]);
    }
    $pending = $_SESSION['wallet_proof'] ?? [];
    unset($_SESSION['wallet_proof']);
    try {
        if (($pending['expires'] ?? 0) <= time() || ($pending['network'] ?? null) !== $network)
            throw new InvalidArgumentException('Срок подтверждения истёк. Подключите кошелёк заново.');
        $raw = (string) file_get_contents('php://input', false, null, 0, 4097);
        if (strlen($raw) > 4096) throw new InvalidArgumentException('Некорректное подтверждение.');
        $data = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
        if (!is_array($data)) throw new InvalidArgumentException('Некорректное подтверждение.');
        require __DIR__ . '/plugin-store-wallet.php';
        $domain = (string) parse_url($base, PHP_URL_HOST);
        $address = storeVerifyTonWallet($data,$pending['challenge'],$domain,$network,
            static fn($chain,$rawAddress) => storeTonWalletKey($config,$chain,$rawAddress),time());
        storeWrite($pdo, 'INSERT INTO plugin_store_author_wallets (author_id,network,address,verified_at) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE address=VALUES(address),verified_at=VALUES(verified_at)', [$userId,$network,$address,time()]);
        jsonResponse(['ok'=>true]);
    } catch (InvalidArgumentException | JsonException $failure) {
        jsonResponse(['ok'=>false,'error'=>$failure->getMessage()],422);
    } catch (Throwable $failure) {
        error_log('BYgram wallet verification: ' . get_class($failure));
        jsonResponse(['ok'=>false,'error'=>'Проверка кошелька временно недоступна.'],503);
    }
}
if ($route === 'login') {
    try {
        if (!hash_equals($csrf, (string) ($_SERVER['HTTP_X_BYGRAM_CSRF'] ?? ''))) throw new InvalidArgumentException();
        $raw = (string) file_get_contents('php://input', false, null, 0, 8193);
        if (strlen($raw) > 8192) throw new InvalidArgumentException();
        $identity = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
        $id = filter_var($identity['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1,'max_range'=>0xfffffffffffff]]);
        $authDate = filter_var($identity['auth_date'] ?? null, FILTER_VALIDATE_INT);
        $hash = $identity['hash'] ?? '';
        $token = $config['bot_token'] ?? '';
        if (!$id || !$authDate || abs(time() - $authDate) > 300 || !is_string($hash) || $token === ''
            || !preg_match('/^[a-f0-9]{64}$/D', $hash) || !hash_equals(telegramLoginHash($identity, $token), $hash))
            throw new InvalidArgumentException();
        storeTransaction($pdo, static function () use ($pdo,$id,$hash) {
            $q = $pdo->prepare('INSERT IGNORE INTO typing_nonces (telegram_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)');
            $q->execute([$id, 'shop:' . substr($hash,0,31)]);
            if ($q->rowCount() !== 1) throw new InvalidArgumentException();
            storeWrite($pdo, 'INSERT IGNORE INTO users (telegram_id) VALUES (?)', [$id]);
        });
        session_regenerate_id(true);
        $_SESSION = ['telegram_id'=>$id,'expires_at'=>time()+1800,'csrf'=>bin2hex(random_bytes(32))];
        jsonResponse(['ok'=>true]);
    } catch (InvalidArgumentException | JsonException | AdminAuthenticationException) {
        jsonResponse(['ok'=>false], 403);
    }
}
if ($route === 'order') {
    try {
        $givenCsrf = $_POST['csrf'] ?? '';
        if (!$userId || !is_string($givenCsrf) || !hash_equals($csrf, $givenCsrf))
            throw new InvalidArgumentException('Войдите через Telegram и повторите заказ.');
        $hash = $_POST['hash'] ?? '';
        if (!is_string($hash) || !preg_match('/^[0-9a-f]{64}$/D', $hash)) throw new InvalidArgumentException('Неверный релиз');
        $terms = $_POST['terms'] ?? '';
        if (!is_string($terms)) throw new InvalidArgumentException('Подтвердите условия продажи');
        $order = storeCreateOrder($pdo, $userId, $hash, 'ton', bin2hex(random_bytes(16)), $terms, $config);
        header('Location: ' . storeCheckoutUrl($pdo, $order, $config), true, 303);
        exit;
    } catch (InvalidArgumentException $failure) {
        http_response_code(422);
        $error = $failure->getMessage();
    }
}
$wallet = $walletPage && $userId ? storeRow($pdo, 'SELECT address FROM plugin_store_author_wallets WHERE author_id=? AND network=?', [$userId,$network]) : false;
$entries = $walletPage ? [] : storeCatalog($pdo, storeMethods($config), $network);
$selected = $_GET['plugin'] ?? '';
$termsUrl = (string) ($config['terms_url'] ?? '');
$support = (string) ($config['support_url'] ?? '');
$username = appSetting('bot_username');
$nonce = base64_encode(random_bytes(18));
$walletSources = $walletPage && $userId ? ' https://cdn.jsdelivr.net' : '';
$connectSources = $walletPage && $userId ? "https: 'self'" : "'self'";
header("Content-Security-Policy: default-src 'none'; script-src 'nonce-$nonce' https://telegram.org$walletSources; frame-src https://oauth.telegram.org https://telegram.org; connect-src $connectSources; img-src https: data: blob:; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
$escape = static fn($s) => htmlspecialchars((string) $s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
?>
<!doctype html>
<html lang="ru">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Платные плагины · BYgram</title>
<style>
:root { color-scheme: light dark; font-family: system-ui,sans-serif }
main { max-width: 42rem; margin: 3rem auto; padding: 0 1rem }
article { border-top: 1px solid gray; padding: 1rem 0 }
input,button { font: inherit }
button { padding: .5rem 1rem; margin-top: .8rem }
a { color: inherit }
</style>
<main>
<h1><?= $walletPage ? 'TON-кошелёк автора' : 'Платные плагины BYgram' ?></h1>
<?php if ($error !== null): ?><p role="alert"><?= $escape($error) ?></p><?php endif; ?>
<?php if (!$userId): ?>
  <p>Войдите под тем же аккаунтом Telegram, который используете в BYgram.</p>
  <p id="auth-status" role="status"></p>
  <script nonce="<?= $nonce ?>">
  window.bygramStoreLogin = async user => {
    const status = document.getElementById('auth-status');
    try {
      const result = await fetch('?action=login', {
        method: 'POST', headers: {'Content-Type':'application/json','X-BYgram-CSRF':<?= json_encode($csrf) ?>}, body: JSON.stringify(user)
      });
      if (!result.ok) throw new Error();
      location.reload();
    } catch (error) {
      status.textContent = 'Не удалось подтвердить вход. Попробуйте ещё раз.';
    }
  };
  </script>
  <?php if (preg_match('/^[A-Za-z0-9_]{5,32}$/D', $username)): ?>
    <script async src="https://telegram.org/js/telegram-widget.js?22" data-telegram-login="<?= $escape($username) ?>"
      data-size="large" data-userpic="false" data-onauth="bygramStoreLogin(user)"></script>
  <?php endif; ?>
<?php endif; ?>
<?php if ($walletPage && $userId): ?>
  <p>Оплата TON за ваши плагины поступит на подтверждённый кошелёк. Смена кошелька применяется к новым заказам.</p>
  <p>Аккаунт Telegram <code><?= $userId ?></code></p>
  <p>Сеть <?= $network === '-3' ? 'Testnet' : 'Mainnet' ?>.</p>
  <?php if ($wallet): ?><p>Текущий кошелёк <code><?= $escape(storeTonFriendly($wallet['address'],$network)) ?></code></p><?php endif; ?>
  <button id="wallet-connect" type="button"><?= $wallet ? 'Заменить кошелёк' : 'Подключить кошелёк' ?></button>
  <p id="wallet-status" role="status"></p>
  <?php if ($wallet): ?>
    <form method="post" action="?action=wallet_remove">
      <input type="hidden" name="csrf" value="<?= $escape($csrf) ?>">
      <button type="submit">Отключить приём TON</button>
    </form>
  <?php endif; ?>
  <script crossorigin="anonymous" integrity="sha384-v4PykhWGxgBFLxXCX+QGernI2uQLQGT9VcIBfnMBa0NRcwjWsOoseT4Cc9ljakW7" src="https://cdn.jsdelivr.net/npm/@tonconnect/ui@2.2.0/dist/tonconnect-ui.min.js"></script>
  <script nonce="<?= $nonce ?>">
  const button = document.getElementById('wallet-connect');
  const result = document.getElementById('wallet-status');
  const net = <?= json_encode($network) ?>;
  const csrf = <?= json_encode($csrf) ?>;
  async function walletRequest(action, data = {}) {
    const response = await fetch('?action=' + action, {
      method: 'POST', headers: {'Content-Type':'application/json','X-BYgram-CSRF':csrf}, body: JSON.stringify(data)
    });
    const payload = await response.json();
    if (!response.ok || !payload.ok) throw new Error(payload.error || 'Повторите подключение кошелька.');
    return payload;
  }
  if (window.TON_CONNECT_UI) {
    const walletUi = new TON_CONNECT_UI.TonConnectUI({
      manifestUrl: <?= json_encode($base . '/plugin-store-web.php?manifest', JSON_HEX_TAG | JSON_THROW_ON_ERROR) ?>
    });
    let challenge = '';
    let verifying = false;
    walletUi.onStatusChange(async wallet => {
      if (!wallet || !challenge) return;
      const proof = wallet.connectItems?.tonProof?.proof;
      const expected = challenge;
      challenge = '';
      if (wallet.account.chain !== net || !proof || proof.payload !== expected) {
        result.textContent = 'Выберите кошелёк с поддержкой подписи TON Connect и проверьте сеть.';
        button.disabled = false;
        return;
      }
      verifying = true;
      button.disabled = true;
      try {
        const {address, chain} = wallet.account;
        await walletRequest('wallet_bind', {account:{address,chain},proof});
        location.reload();
      } catch (error) {
        result.textContent = error.message;
        verifying = false;
        button.disabled = false;
      }
    });
    button.onclick = async () => {
      if (verifying) return;
      button.disabled = true;
      try {
        challenge = '';
        await walletUi.connectionRestored;
        if (walletUi.connected) await walletUi.disconnect();
        challenge = (await walletRequest('wallet_challenge')).challenge;
        walletUi.setConnectRequestParameters({state:'ready',value:{tonProof:challenge}});
        await walletUi.openModal();
      } catch (error) {
        result.textContent = error.message;
      } finally {
        button.disabled = verifying;
      }
    };
  } else {
    button.disabled = true;
    result.textContent = 'Модуль кошелька недоступен. Обновите страницу.';
  }
  </script>
<?php elseif (!$walletPage && $userId): ?>
  <p><a href="?wallet=1">TON-кошелёк автора</a></p>
<?php endif; ?>
<?php foreach ($entries as $entry):
    $manifest = $entry['manifest'];
    if ($selected !== '' && $selected !== $manifest['id']) continue;
    $nano = $entry['purchase']['nano'];
    $amount = str_pad($nano, 10, '0', STR_PAD_LEFT);
    $amount = rtrim(rtrim(substr($amount,0,-9) . '.' . substr($amount,-9),'0'),'.');
?>
  <article>
    <h2><?= $escape($manifest['name']) ?></h2>
    <p><?= $escape($manifest['description']) ?></p>
    <?php if ($nano !== '0'): ?>
      <p><?= $escape($amount) ?> TON<?= ($config['plugin_store_ton_test'] ?? true) ? ' · Testnet' : '' ?></p>
      <?php if ($userId): ?>
        <form method="post">
          <input type="hidden" name="csrf" value="<?= $escape($csrf) ?>">
          <input type="hidden" name="hash" value="<?= $escape($entry['sha256']) ?>">
          <label><input type="checkbox" required name="terms" value="<?= PLUGIN_STORE_TERMS ?>"> <?= $escape(PLUGIN_STORE_NOTICE . ' ' . PLUGIN_STORE_TON_NOTICE) ?></label>
          <br><button type="submit">Перейти к оплате TON</button>
        </form>
      <?php endif; ?>
    <?php else: ?>
      <p>Покупка через веб-кассу сейчас недоступна.</p>
    <?php endif; ?>
  </article>
<?php endforeach; ?>
<p>
  <?php if (str_starts_with($termsUrl, 'https://')): ?><a href="<?= $escape($termsUrl) ?>">Условия продажи</a><?php endif; ?>
  <?php if (str_starts_with($support, 'https://')): ?> · <a href="<?= $escape($support) ?>">Поддержка</a><?php endif; ?>
</p>
</main>
</html>
