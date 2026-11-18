<?php
declare(strict_types=1);

return [
    'account_confirmation_enabled' => false,
    'request_signature_checks_enabled' => false,
    'device_revocation_checks_enabled' => false,
    'db_dsn' => 'mysql:host=127.0.0.1;port=3306;dbname=bygram;charset=utf8mb4',
    'db_user' => 'bygram',
    'db_password' => 'replace-me',
    'bot_token' => 'replace-me',
    'bot_username' => 'BYgram_unofficial_client_bot',
    'owner_id' => 123456789,
    'manager_username' => 'BYgram_manager',
    'admin_url' => 'https://example.com/activity-api/admin.php',
    'terms_url' => 'https://example.com/terms',
    'support_url' => 'https://t.me/BYgram_manager',
    'plugin_store_enabled' => false,
    'plugin_store_sales' => false,
    'plugin_store_authors' => [],
    'plugin_store_live' => false,
    'plugin_store_url' => 'https://example.com/activity-api',
    'plugin_store_stars_test' => true,
    'plugin_store_stars_token' => '',
    'plugin_store_stars_secret' => '',
    'plugin_store_ton_test' => true,
    'plugin_store_ton_address' => '', // Legacy receiver for orders from before direct author payments.
    'plugin_store_ton_api_key' => '',
    'plugin_store_author_bps' => 8000,
];
