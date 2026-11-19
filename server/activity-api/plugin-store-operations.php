<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/plugin-store.php';
require __DIR__ . '/plugin-store-providers.php';

$config = appConfig();
$pdo = db();
$action = $argv[1] ?? '';
$method = $argv[2] ?? '';
if (!in_array($method, ['stars','ton'], true)) {
    fwrite(STDERR, "Commands: payout-record stars|ton author amount reference | refund stars charge [merchant] | refund-record ton charge outgoing-hash\n");
    exit(2);
}
$merchant = storeMerchant($config, $method);
if ($action === 'payout-record' && $argc === 6) {
    if ($method === 'ton' && storeTonRecipient($merchant) === null) throw new InvalidArgumentException('Direct TON is already in the author wallet');
    $positive = ['options'=>['min_range'=>1]];
    $author = filter_var($argv[3], FILTER_VALIDATE_INT, $positive);
    $amount = filter_var($argv[4], FILTER_VALIDATE_INT, $positive);
    if (!$author || !$amount) throw new InvalidArgumentException('Expected positive integers');
    storeRecordPayout($pdo, $author, $method, $merchant, $amount, $argv[5]);
    echo "Payout recorded\n";
    exit;
}
$charge = $argv[3] ?? '';
$merchant = $method === 'stars' ? ($argv[4] ?? $merchant) : $merchant;
$payment = $method === 'ton'
    ? storeRow($pdo, "SELECT * FROM plugin_store_payments WHERE method='ton' AND charge=?", [$charge])
    : storeRow($pdo, 'SELECT * FROM plugin_store_payments WHERE method=? AND merchant=? AND charge=?', [$method,$merchant,$charge]);
if (!$payment) throw new InvalidArgumentException('Unknown charge');
$merchant = $payment['merchant'];
if ($payment['status'] === 'refunded') exit("Already refunded\n");
if ($action === 'refund' && $method === 'stars' && ($argc === 4 || $argc === 5)) {
    $result = storeBot(storeStarsMerchantConfig($pdo, $config, $merchant), 'refundStarPayment',
        ['user_id'=>(int) $payment['payer'],'telegram_payment_charge_id'=>$charge]);
    if ($result !== true) throw new RuntimeException('Refund did not complete');
    $reference = $charge;
} elseif ($action === 'refund-record' && $method === 'ton' && $argc === 5 && preg_match('/^[0-9a-f]{64}$/D', $argv[4])) {
    $reference = $argv[4];
} else {
    throw new InvalidArgumentException('Invalid refund command');
}
if ($payment['order_id'] === null) {
    storeWrite($pdo, "UPDATE plugin_store_payments SET status='refunded',refund_reference=?
        WHERE method=? AND merchant=? AND charge=? AND status='unmatched'", [$reference,$method,$merchant,$charge]);
} else {
    storeSettle($pdo, $payment['order_id'], $method, $charge, $payment['payer'], (int) $payment['amount'], time(), $merchant, true, $reference);
}
echo "Refund recorded\n";
