<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

try{
    $currentUserId=requireAdminSession();
    $pdo=db();$method=$_SERVER['REQUEST_METHOD']??'GET';$input=json_decode((string)file_get_contents('php://input'),true)?:[];
    if($method==='POST'){
        $action=(string)($input['action']??'');
        if($action==='settings.save'){
            $username=ltrim(trim((string)($input['bot_username']??'')),'@');
            if(!preg_match('/^[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]$/D',$username))throw new InvalidArgumentException('Username бота: от 5 до 32 символов, латиница, цифры и подчёркивание');
            saveAppSetting('bot_username',$username);
        }elseif($action==='admin.add'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может добавлять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1||isOwner($id))throw new InvalidArgumentException('Укажите Telegram ID нового администратора');
            $pdo->prepare('INSERT IGNORE INTO admins (telegram_id,added_by) VALUES (?,?)')->execute([$id,$currentUserId]);
        }elseif($action==='admin.remove'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может удалять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1)throw new InvalidArgumentException('Неверный Telegram ID');
            $pdo->prepare('DELETE FROM admins WHERE telegram_id=?')->execute([$id]);
        }elseif($action==='promo.save'){
            $code=strtoupper(trim((string)($input['code']??'')));$discount=(int)($input['discount_percent']??0);$limit=!empty($input['unlimited_uses'])?null:(int)($input['usage_limit']??0);$expiresRaw=trim((string)($input['expires_at']??''));$expires=!empty($input['never_expires'])?null:str_replace('T',' ',$expiresRaw);
            if(!preg_match('/^[A-Z0-9_-]{3,40}$/',$code)||$discount<1||$discount>100||($limit!==null&&$limit<1)||($expires!==null&&!preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/',$expires)))throw new InvalidArgumentException('Проверьте промокод, лимиты и срок действия');
            if(!empty($input['id']))$pdo->prepare('UPDATE promo_codes SET code=?,discount_percent=?,usage_limit=?,expires_at=?,active=? WHERE id=?')->execute([$code,$discount,$limit,$expires,!empty($input['active']),$input['id']]);
            else $pdo->prepare('INSERT INTO promo_codes (code,discount_percent,usage_limit,expires_at) VALUES (?,?,?,?)')->execute([$code,$discount,$limit,$expires]);
        }elseif($action==='promo.toggle'){$pdo->prepare('UPDATE promo_codes SET active=? WHERE id=?')->execute([!empty($input['active']),$input['id']]);
        }elseif($action==='license.save'){
            $id=(int)($input['telegram_id']??0);$status=(string)($input['status']??'');$validRaw=trim((string)($input['valid_until']??''));$validUntil=!empty($input['permanent'])?null:str_replace('T',' ',$validRaw);
            if($id<1||!in_array($status,['active','blocked','refunded'],true)||($validUntil!==null&&!preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/',$validUntil)))throw new InvalidArgumentException('Проверьте Telegram ID, статус и срок лицензии');
            $pdo->prepare('INSERT IGNORE INTO users (telegram_id) VALUES (?)')->execute([$id]);
            $pdo->prepare('INSERT INTO licenses (telegram_id,status,valid_until) VALUES (?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),valid_until=VALUES(valid_until)')->execute([$id,$status,$validUntil]);
            if($status!=='active'){$pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);$pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);}
        }elseif($action==='license.revoke'){
            $id=(int)($input['telegram_id']??0);if($id<1)throw new InvalidArgumentException('Неверный Telegram ID');
            $q=$pdo->prepare("UPDATE licenses SET status='blocked' WHERE telegram_id=? AND status='active'");$q->execute([$id]);if(!$q->rowCount())throw new InvalidArgumentException('Активная лицензия не найдена');
            $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);
            $pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE telegram_id=? AND revoked_at IS NULL')->execute([$id]);
        }elseif($action==='device.revoke'){
            $deviceId=(string)($input['device_id']??'');if(!preg_match('/^[a-f0-9]{32}$/D',$deviceId))throw new InvalidArgumentException('Неверное устройство');
            $q=$pdo->prepare('UPDATE licensed_devices SET revoked_at=UTC_TIMESTAMP() WHERE id=? AND revoked_at IS NULL');$q->execute([$deviceId]);if(!$q->rowCount())throw new InvalidArgumentException('Активное устройство не найдено');
            $pdo->prepare('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE device_id=? AND revoked_at IS NULL')->execute([$deviceId]);
        }elseif($action==='auth.confirm'){
            $id=(int)($input['id']??0);if($id<1)throw new InvalidArgumentException('Неверная заявка');
            $q=$pdo->prepare('UPDATE auth_challenges SET confirmed_user_id=expected_user_id,confirmed_at=UTC_TIMESTAMP() WHERE id=? AND confirmed_user_id IS NULL AND expires_at>UTC_TIMESTAMP()');$q->execute([$id]);if(!$q->rowCount())throw new InvalidArgumentException('Заявка не найдена или истекла');
        }elseif($action==='badge.save'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();$badges[$key]=true;saveBadges($badges);
        }elseif($action==='badge.delete'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();unset($badges[$key]);if($type==='user')unset($badges[(string)$id]);saveBadges($badges);
        }elseif($action==='method.save'){
            $code=(string)($input['code']??'');if(!in_array($code,['stars','crypto','manual'],true)||(float)($input['price']??0)<0)throw new InvalidArgumentException('Неверный способ оплаты');
            $pdo->prepare('UPDATE payment_methods SET enabled=?,price=? WHERE code=?')->execute([!empty($input['enabled']),$input['price'],$code]);
        }elseif($action==='referral.save'){
            $percent=(float)($input['percent']??-1);if($percent<0||$percent>100)throw new InvalidArgumentException('Процент должен быть от 0 до 100');
            $pdo->prepare('INSERT INTO referral_settings (id,enabled,percent) VALUES (1,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),percent=VALUES(percent)')->execute([!empty($input['enabled']),$percent]);
        }elseif($action==='referral.grant'){
            $id=(int)($input['telegram_id']??0);$amount=trim((string)($input['amount']??''));$currency=strtoupper(trim((string)($input['currency']??'')));$note=trim((string)($input['note']??''));
            $q=$pdo->prepare('SELECT 1 FROM users WHERE telegram_id=?');$q->execute([$id]);if($id<1||!$q->fetchColumn()||!preg_match('/^(?:0|[1-9]\d{0,15})(?:\.\d{1,2})?$/',$amount)||(float)$amount<=0||!preg_match('/^[A-Z0-9]{2,8}$/',$currency)||mb_strlen($note)>255)throw new InvalidArgumentException('Проверьте пользователя, сумму, валюту и комментарий');
            $pdo->prepare('INSERT INTO referral_adjustments (telegram_id,amount,currency,note) VALUES (?,?,?,?)')->execute([$id,$amount,$currency,$note?:null]);
        }elseif($action==='release.save'){
            $release=['version'=>trim((string)($input['version']??'')),'version_code'=>trim((string)($input['version_code']??'')),'status'=>trim((string)($input['status']??'')),'size'=>trim((string)($input['size']??'')),'requirements'=>trim((string)($input['requirements']??'')),'package'=>trim((string)($input['package']??'')),'apk_name'=>trim((string)($input['apk_name']??''))];
            if(!preg_match('/^[0-9]+(?:\.[0-9]+){1,3}$/',$release['version'])||!preg_match('/^[0-9]{1,12}$/',$release['version_code'])||!preg_match('/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/',$release['apk_name'])||!preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,99}$/',$release['package'])||array_filter($release,fn($value)=>mb_strlen($value)>120))throw new InvalidArgumentException('Проверьте данные релиза');
            $json=json_encode($release,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_PRETTY_PRINT);if(file_put_contents(__DIR__.'/release.json.tmp',$json,LOCK_EX)===false||!rename(__DIR__.'/release.json.tmp',__DIR__.'/release.json'))throw new RuntimeException('Не удалось сохранить данные релиза');
        }elseif($action==='requisite.save'){
            $bank=(string)($input['bank']??'');$card=preg_replace('/\s+/','',trim((string)($input['card_number']??'')));if(!in_array($bank,['belarusbank','mtbank'],true)||!preg_match('/^[0-9*+-]{8,40}$/',$card))throw new InvalidArgumentException('Проверьте реквизиты');
            if(!empty($input['id']))$pdo->prepare('UPDATE bank_requisites SET bank=?,card_number=?,holder_name=?,active=? WHERE id=?')->execute([$bank,$card,trim((string)($input['holder_name']??''))?:null,!empty($input['active']),$input['id']]);
            else $pdo->prepare('INSERT INTO bank_requisites (bank,card_number,holder_name) VALUES (?,?,?)')->execute([$bank,$card,trim((string)($input['holder_name']??''))?:null]);
        }else throw new InvalidArgumentException('Неизвестное действие');
    }elseif($method!=='GET'){jsonResponse(['ok'=>false,'error'=>'Method not allowed'],405);}
    $data=[];$data['is_owner']=isOwner($currentUserId);$data['settings']=['bot_username'=>appSetting('bot_username')];$data['release']=json_decode((string)@file_get_contents(__DIR__.'/release.json'),true)?:['version'=>'12.10.1','version_code'=>'70389','status'=>'Стабильная версия · APK','size'=>'≈ 123 МБ','requirements'=>'Android 6.0+','package'=>'com.bygram.mobile','apk_name'=>'BYgram-12.10.1.apk'];$data['badges']=loadBadges();$data['methods']=$pdo->query('SELECT * FROM payment_methods')->fetchAll();$data['promos']=$pdo->query('SELECT * FROM promo_codes ORDER BY id DESC LIMIT 100')->fetchAll();$data['licenses']=$pdo->query("SELECT l.*,u.username,u.first_name,(SELECT COUNT(*) FROM orders o WHERE o.beneficiary_id=l.telegram_id) order_count,(SELECT COUNT(*) FROM licensed_devices d WHERE d.telegram_id=l.telegram_id AND d.revoked_at IS NULL) device_count FROM licenses l LEFT JOIN users u ON u.telegram_id=l.telegram_id ORDER BY l.updated_at DESC LIMIT 200")->fetchAll();$data['devices']=$pdo->query('SELECT id,telegram_id,device_name,created_at,last_seen_at FROM licensed_devices WHERE revoked_at IS NULL ORDER BY last_seen_at DESC LIMIT 400')->fetchAll();$data['requisites']=$pdo->query('SELECT * FROM bank_requisites ORDER BY bank,id')->fetchAll();$data['orders']=$pdo->query('SELECT * FROM orders ORDER BY id DESC LIMIT 100')->fetchAll();
    $data['auth_challenges']=$pdo->query('SELECT id,expected_user_id,token_hint,expires_at FROM auth_challenges WHERE confirmed_user_id IS NULL AND expires_at>UTC_TIMESTAMP() ORDER BY created_at DESC LIMIT 100')->fetchAll();
    $data['referral_settings']=$pdo->query('SELECT enabled,percent FROM referral_settings WHERE id=1')->fetch()?:['enabled'=>0,'percent'=>0];$data['referrals']=$pdo->query("SELECT e.order_id,e.referrer_id,e.referred_id,e.commission,e.currency,e.created_at,'auto' source,u.username,u.first_name,NULL note FROM referral_earnings e LEFT JOIN users u ON u.telegram_id=e.referrer_id UNION ALL SELECT a.id,a.telegram_id,NULL,a.amount,a.currency,a.created_at,'manual',u.username,u.first_name,a.note FROM referral_adjustments a LEFT JOIN users u ON u.telegram_id=a.telegram_id ORDER BY created_at DESC LIMIT 100")->fetchAll();
    $perPage=25;$total=(int)$pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();$pages=max(1,(int)ceil($total/$perPage));$page=min($pages,max(1,(int)($_GET['users_page']??1)));$users=$pdo->prepare("SELECT u.*,r.referrer_id,inv.username inviter_username,inv.first_name inviter_first_name,l.status license_status,l.valid_until,(l.status='active' AND (l.valid_until IS NULL OR l.valid_until>UTC_TIMESTAMP())) has_license FROM users u LEFT JOIN referrals r ON r.referred_id=u.telegram_id LEFT JOIN users inv ON inv.telegram_id=r.referrer_id LEFT JOIN licenses l ON l.telegram_id=u.telegram_id ORDER BY u.created_at DESC LIMIT ? OFFSET ?");$users->bindValue(1,$perPage,PDO::PARAM_INT);$users->bindValue(2,($page-1)*$perPage,PDO::PARAM_INT);$users->execute();$data['users']=$users->fetchAll();$data['users_page']=$page;$data['users_pages']=$pages;$data['users_total']=$total;
    $data['admins']=$data['is_owner']?$pdo->query('SELECT a.*,u.username,u.first_name FROM admins a LEFT JOIN users u ON u.telegram_id=a.telegram_id ORDER BY a.created_at DESC')->fetchAll():[];
    jsonResponse(['ok'=>true,'data'=>$data]);
}catch(Throwable $e){if(isset($pdo)&&$pdo->inTransaction())$pdo->rollBack();jsonResponse(['ok'=>false,'error'=>$e->getMessage()],$e instanceof InvalidArgumentException?422:($e instanceof AdminAuthenticationException?401:403));}
