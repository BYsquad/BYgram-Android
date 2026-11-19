<?php
declare(strict_types=1);

require __DIR__ . '/../server/activity-api/chat-flames.php';
require __DIR__ . '/../server/activity-api/admin-flames.php';

function check(bool $condition, string $label): void {
    if (!$condition) throw new RuntimeException($label);
}

[$today] = flameCalendar();
check(adminFlameDay(adminFlameDate($today), $today) === $today, 'Admin date uses the streak calendar');
foreach (['2026-02-30', adminFlameDate($today + 1), ['2026-11-17']] as $invalidDate) {
    try {
        adminFlameDay($invalidDate, $today);
        throw new RuntimeException('Invalid admin date accepted');
    } catch (InvalidArgumentException) {
    }
}

$empty = array_fill_keys(['last_day', 'days', 'lost_day', 'lost_days', 'restore_month', 'restores', 'revision'], 0);
$month = 202611;
$first = flameApply($empty, [[100, 102]]);
check($first['days'] === 3 && $first['last_day'] === 102, 'Start from an existing mutual streak');
check(flameDays($first, 103, $month)['lost_days'] === 0, 'One day to complete the exchange');
check(flameDays($first, 104, $month)['lost_days'] === 3, 'Expire and offer recovery at the next midnight');
check(flameDays($first, 110, $month)['lost_days'] === 3, 'Last lost streak remains recoverable');
check(flameRestore($first, 110, $month, 102)['last_day'] === 109, 'Recovery after several missed days');
check(flameApply($first, [[101, 102]]) === $first, 'Repeated sync is idempotent');
check(flameApply($first, [[103, 103]])['days'] === 4, 'A contiguous day extends the streak');

$restarted = flameApply($first, [[104, 104]]);
check($restarted['days'] === 1 && $restarted['lost_days'] === 3, 'New streak can coexist with recovery');
$restored = flameRestore($restarted, 104, $month, 102);
check($restored['days'] === 4 && $restored['last_day'] === 104, 'Recovery includes completed day and skips missed day');
check(flameRestore($restored, 104, $month, 102) === $restored, 'Recovery retries do not spend the quota');
$restoredBeforeExchange = flameRestore($first, 104, $month, 102);
check($restoredBeforeExchange['days'] === 3 && $restoredBeforeExchange['last_day'] === 103, 'Recovery before exchange preserves count');
check(flameApply($restoredBeforeExchange, [[104, 104]])['days'] === 4, 'A restored series can continue');
$limit = $restoredBeforeExchange;
$limit['restores'] = 3;
check(flameDays($limit, 104, $month)['remaining'] === 0, 'Shared monthly cap');
check(flameRestore($limit, 105, $month, 103) === $limit, 'Fourth recovery denied');
check(flameRestore($limit, 105, 202612, 103)['restores'] === 1, 'Calendar month resets quota');
$longGap = flameApply($first, [[107, 108]]);
check(flameRestore($longGap, 109, $month, 102)['days'] === 5, 'Recovery preserves a new active streak');
check(flameRanges([[100, 102], [104, 104]], 104) === [[100, 102], [104, 104]], 'Accept chronological spans');
try {
    flameRanges([[101, 105]], 104);
    throw new RuntimeException('Future spans rejected');
} catch (InvalidArgumentException) {
}
echo "PASS: flame streak, expiration, recovery, quota, and validation\n";
