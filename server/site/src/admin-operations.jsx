import React, { useState } from 'react';
import { ArrowDownToLine, ChevronLeft, ChevronRight, ExternalLink, PackageCheck, Upload } from 'lucide-react';
import { Badge } from '@astryxdesign/core/Badge';
import { Banner } from '@astryxdesign/core/Banner';
import { Button } from '@astryxdesign/core/Button';
import { Card } from '@astryxdesign/core/Card';
import { CheckboxInput } from '@astryxdesign/core/CheckboxInput';
import { Dialog, DialogHeader } from '@astryxdesign/core/Dialog';
import { FormLayout } from '@astryxdesign/core/FormLayout';
import { Grid } from '@astryxdesign/core/Grid';
import { Icon } from '@astryxdesign/core/Icon';
import { Layout, LayoutContent, LayoutFooter } from '@astryxdesign/core/Layout';
import { Selector } from '@astryxdesign/core/Selector';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Table, pixel, proportional } from '@astryxdesign/core/Table';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextArea } from '@astryxdesign/core/TextArea';
import { TextInput } from '@astryxdesign/core/TextInput';
import { adminRequest, useAdminResource } from './admin-api';

const values = form => Object.fromEntries(new FormData(form));

function Field({ name, initial = '', ...props }) {
  const [value, setValue] = useState(String(initial));
  return <TextInput htmlName={name} value={value} onChange={setValue} {...props} />;
}

function FormCard({ title, description, action, run, children, label = 'Сохранить', onSubmit }) {
  const [busy, setBusy] = useState(false);
  return <Card padding={4}>
    <form onSubmit={async event => {
      event.preventDefault();
      if (busy) return;
      const data = values(event.currentTarget);
      setBusy(true);
      try {
        await (onSubmit ? onSubmit(data) : run({ ...data, action }));
      } finally {
        setBusy(false);
      }
    }}>
      <VStack gap={4}>
        <VStack gap={1}>
          <Heading level={2}>{title}</Heading>
          {description && <Text type="supporting">{description}</Text>}
        </VStack>
        <FormLayout>{children}</FormLayout>
        <HStack hAlign="start"><Button label={label} type="submit" variant="primary" isLoading={busy} /></HStack>
      </VStack>
    </form>
  </Card>;
}

function Records({ title, description, rows, columns, rowKey, empty }) {
  return <Card padding={0}>
    <VStack gap={1} padding={4}>
      <Heading level={2}>{title}</Heading>
      {description && <Text type="supporting">{description}</Text>}
    </VStack>
    {rows.length ? <Table data={rows} columns={columns} idKey={rowKey} hasHover />
      : <VStack padding={4}><Text type="supporting">{empty}</Text></VStack>}
  </Card>;
}

export function BadgesPage({ badges, run, confirmAction }) {
  const [type, setType] = useState('user');
  const rows = Object.entries(badges).filter(([, value]) => value === true || value?.bygram).map(([key]) => {
    const [peer_type, telegram_id] = key.includes(':') ? key.split(':') : ['user', key];
    return { key, peer_type, telegram_id };
  });
  const columns = [
    { key: 'telegram_id', header: 'Telegram ID' },
    { key: 'peer_type', header: 'Тип', renderCell: row => row.peer_type === 'chat' ? 'Канал или группа' : 'Пользователь' },
    { key: 'remove', header: 'Действие', renderCell: row => <Button label="Снять" size="sm" variant="ghost"
      onClick={() => confirmAction('Снять значок?', 'Telegram ID ' + row.telegram_id,
        () => run({ action: 'badge.delete', telegram_id: row.telegram_id, peer_type: row.peer_type }))} /> },
  ];
  return <Grid columns={{ minWidth: 300, repeat: 'fit' }} gap={4}>
    <FormCard title="Выдать значок" action="badge.save" run={run} label="Выдать">
      <Selector label="Получатель" htmlName="peer_type" value={type} onChange={setType}
        options={[{ value: 'user', label: 'Пользователь' }, { value: 'chat', label: 'Канал или группа' }]} />
      <Field name="telegram_id" label="Telegram ID" inputMode="numeric" isRequired />
    </FormCard>
    <Records title="Назначенные значки" rows={rows} columns={columns} rowKey="key" empty="Значки пока не выданы." />
  </Grid>;
}

export function AccessPage({ data, run, confirmAction }) {
  const admins = [
    { key: 'telegram_id', header: 'Telegram ID' },
    { key: 'username', header: 'Аккаунт', renderCell: row => row.username ? '@' + row.username : row.first_name || '—' },
    { key: 'remove', header: 'Действие', renderCell: row => <Button label="Удалить" size="sm" variant="ghost"
      onClick={() => confirmAction('Убрать доступ администратора?', 'Telegram ID ' + row.telegram_id,
        () => run({ action: 'admin.remove', telegram_id: row.telegram_id }))} /> },
  ];
  const devices = [
    { key: 'device_name', header: 'Устройство', width: proportional(2, { minWidth: 130 }) },
    { key: 'telegram_id', header: 'Telegram ID', width: pixel(132) },
    { key: 'last_seen_at', header: 'В сети (UTC)', width: pixel(175) },
    { key: 'remove', header: 'Действие', width: pixel(112),
      renderCell: row => <Button label="Отозвать" size="sm" variant="ghost"
        onClick={() => confirmAction('Отозвать устройство?', row.device_name + ' · ' + row.telegram_id,
          () => run({ action: 'device.revoke', device_id: row.id }))} /> },
  ];
  return <VStack gap={4}>
    {data.is_owner && <Grid columns={{ minWidth: 300, repeat: 'fit' }} gap={4}>
      <FormCard title="Добавить администратора" action="admin.add" run={run} label="Добавить">
        <Field name="telegram_id" label="Telegram ID" inputMode="numeric" isRequired />
      </FormCard>
      <Records title="Администраторы" rows={data.admins} columns={admins} rowKey="telegram_id" empty="Других администраторов пока нет." />
    </Grid>}
    <Records title="Устройства" description="До 400 записей по времени последней активности." rows={data.devices}
      columns={devices} rowKey="id" empty="Активных устройств нет." />
  </VStack>;
}

export function UsersPage({ revision }) {
  const [page, setPage] = useState(1);
  const [attempt, setAttempt] = useState(0);
  const { data, error } = useAdminResource('admin-api.php?view=users&page=' + page, revision + attempt);
  if (error) return <Banner status="error" title={error}
    endContent={<Button label="Повторить" onClick={() => setAttempt(value => value + 1)} />} />;
  if (!data) return <Text role="status">Загрузка списка устройств…</Text>;
  const columns = [
    { key: 'model', header: 'Устройство', width: proportional(2, { minWidth: 155 }),
      renderCell: row => <VStack gap={1}><Text weight="semibold" maxLines={1}>{row.model}</Text><Text type="supporting">{row.device}</Text></VStack> },
    { key: 'app_version', header: 'Версия BYgram', width: pixel(140) },
    { key: 'android_api', header: 'Android API', width: pixel(120) },
    { key: 'first_seen_at', header: 'Первый запуск (UTC)', width: pixel(190) },
    { key: 'last_seen_at', header: 'Последний запуск (UTC)', width: pixel(190) },
  ];
  return <Card padding={0}>
    <VStack padding={4} gap={1}>
      <Heading level={2}>{Number(data.total).toLocaleString('ru-RU')} устройств</Heading>
      <Text type="supporting">Один пользователь в счётчике соответствует одному устройству с запущенным BYgram.</Text>
    </VStack>
    {data.rows.length ? <Table data={data.rows} columns={columns} idKey="device" hasHover
      rowCount={data.total} rowIndexStart={(data.page - 1) * data.page_size + 1} />
      : <VStack padding={4}><Text type="supporting">Устройства пока не зарегистрированы.</Text></VStack>}
    {data.total > data.page_size && <HStack padding={4} hAlign="between" vAlign="center" gap={2}>
      <Text type="supporting">{(data.page - 1) * data.page_size + 1}–{Math.min(data.page * data.page_size, data.total)} из {data.total}</Text>
      <HStack gap={2}>
        <Button label="Предыдущая страница" isIconOnly icon={<Icon icon={ChevronLeft} />} isDisabled={data.page <= 1}
          onClick={() => setPage(data.page - 1)} />
        <Button label="Следующая страница" isIconOnly icon={<Icon icon={ChevronRight} />} isDisabled={data.page * data.page_size >= data.total}
          onClick={() => setPage(data.page + 1)} />
      </HStack>
    </HStack>}
  </Card>;
}

async function uploadApk(file, name, channel, updateProgress) {
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()));
  const hash = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
  const chunkSize = 4 * 1024 * 1024;
  const uploadId = crypto.randomUUID();
  for (let offset = 0; offset < file.size; offset += chunkSize) {
    const end = Math.min(file.size, offset + chunkSize);
    const form = new FormData();
    form.append('channel', channel);
    form.append('upload_id', uploadId);
    form.append('name', name);
    form.append('offset', offset);
    form.append('total', file.size);
    form.append('chunk', file.slice(offset, end), 'chunk.apk');
    if (end === file.size) { form.append('finish', '1'); form.append('sha256', hash); }
    await adminRequest('admin-upload.php', { body: form });
    updateProgress(Math.round(end * 100 / file.size));
  }
}

export function PublicationPage({ release, channel = 'stable', run, notify }) {
  const [progress, setProgress] = useState(null);
  const beta = channel === 'beta';
  const current = release || {};
  async function publish(data) {
    const file = data.apk;
    delete data.apk;
    if (!/^[0-9]+(?:\.[0-9]+){1,3}$/.test(data.version) || !/^\d{1,12}$/.test(data.version_code)
      || !/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/.test(data.apk_name)
      || data.apk_name.toLowerCase().startsWith('bygram-beta-') !== beta
      || ['status', 'requirements'].some(key => data[key].length > 120)) {
      notify('Проверьте версию, имя APK и выбранный канал', true);
      return;
    }
    if (file?.name && (file.size < 1024 || file.size > 536870912 || !file.name.toLowerCase().endsWith('.apk'))) {
      notify('Выберите APK размером от 1 КБ до 512 МБ', true);
      return;
    }
    try {
      if (file?.name) {
        setProgress(0);
        await uploadApk(file, data.apk_name, channel, setProgress);
      }
      await run({ ...data, channel, package: 'com.bygram.mobile', action: 'release.save' },
        beta ? 'Бета-версия опубликована' : 'Релиз опубликован');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setProgress(null);
    }
  }
  return <FormCard title={beta ? 'APK бета-версии' : 'APK стабильной версии'}
    description={beta ? 'Имя файла начинается с BYgram-beta-. Сборка появится в отдельном разделе скачивания.' : 'Эти данные появятся в разделе стабильной версии.'}
    onSubmit={publish} label="Опубликовать">
    <HStack gap={2} vAlign="center"><Icon icon={Upload} size="sm" />
      <label>APK (необязательно) <input name="apk" type="file" accept=".apk" /></label>
    </HStack>
    {progress !== null && <Text role="status">Загрузка APK · {progress}%</Text>}
    <Grid columns={{ minWidth: 210, repeat: 'fit' }} gap={3}>
      <Field name="version" label="Версия" initial={current.version} isRequired />
      <Field name="version_code" label="Код версии" initial={current.version_code} inputMode="numeric" isRequired />
      <Field name="apk_name" label="Имя APK" initial={current.apk_name} placeholder={beta ? 'BYgram-beta-13.2.2.apk' : 'BYgram-13.2.2.apk'} isRequired />
      <Field name="status" label="Подпись релиза" initial={current.status || (beta ? 'Бета-версия' : '')} />
      <Field name="requirements" label="Требования" initial={current.requirements || (beta ? 'Android 7.0+' : '')} />
    </Grid>
  </FormCard>;
}

export function IntegrationsPage({ settings, run }) {
  return <FormCard title="Вход через Telegram" description="Имя интеграции для виджета входа Telegram."
    action="settings.save" run={run}>
    <Field name="bot_username" label="Имя бота" initial={settings.bot_username} isRequired hasSpellCheck={false} />
  </FormCard>;
}

const pluginStatuses = {
  pending: { label: 'Ожидает', variant: 'neutral' },
  review: { label: 'На проверке', variant: 'orange' },
  approved: { label: 'Опубликован', variant: 'green' },
  rejected: { label: 'Отклонён', variant: 'gray' },
  revoked: { label: 'Отозван', variant: 'red' },
};

function PluginReview({ row, close, save }) {
  const [reviewed, setReviewed] = useState(false);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const candidate = row.candidate;
  const pending = row.status === 'pending' || row.status === 'review';
  async function submit(action) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await save({ id: row.id, action, reviewed, note: note.trim(), hash: candidate?.sha256 });
    } catch (failure) {
      setError(failure.message);
      setBusy(false);
    }
  }
  return <Dialog isOpen onOpenChange={open => { if (!open && !busy) close(); }} purpose="form" width="min(38rem, 100%)">
    <Layout header={<DialogHeader title={`Заявка №${row.id}`} subtitle={'Автор · ' + row.telegram_id} onOpenChange={busy ? undefined : close} />}
      content={<LayoutContent><VStack gap={4}>
        {error && <Banner status="error" title={error} />}
        <HStack gap={2} wrap="wrap" vAlign="center">
          <Badge {...(pluginStatuses[row.status] || { label: row.status })} />
          <Button label="Репозиторий" href={row.repository + '/releases'} target="_blank" rel="noopener noreferrer"
            icon={<Icon icon={ExternalLink} />} />
          {row.status === 'review' && candidate && <Button label="Скачать кандидат" href={`plugins.php?download=${row.id}`}
            icon={<Icon icon={ArrowDownToLine} />} />}
        </HStack>
        {candidate && <TextArea label="Метаданные пакета" value={JSON.stringify(candidate, null, 2)} isReadOnly rows={6} />}
        {row.status === 'review' && candidate && <CheckboxInput label="Я проверил исходники и скачанный пакет" value={reviewed} onChange={setReviewed} />}
        {(pending || row.status === 'approved') && <TextArea label={pending ? 'Причина отклонения' : 'Причина отзыва'}
          value={note} onChange={setNote} maxLength={1000} rows={2} />}
        {row.review_note && <Text type="supporting">{row.review_note}</Text>}
      </VStack></LayoutContent>}
      footer={<LayoutFooter><HStack gap={2} hAlign="end" wrap="wrap">
        <Button label="Закрыть" onClick={close} isDisabled={busy} />
        {pending && <Button label="Отклонить" variant="destructive" isDisabled={!note.trim() || busy} onClick={() => submit('reject')} />}
        {pending && <Button label={row.status === 'pending' ? 'Подготовить' : 'Обновить кандидат'}
          isLoading={busy} onClick={() => submit('prepare')} />}
        {row.status === 'review' && candidate && <Button label="Опубликовать" variant="primary"
          isDisabled={!reviewed || busy} onClick={() => submit('approve')} />}
        {row.status === 'approved' && candidate && <Button label="Отозвать" variant="destructive"
          isDisabled={!note.trim() || busy} onClick={() => submit('revoke')} />}
      </HStack></LayoutFooter>} />
  </Dialog>;
}

export function PluginsPage({ revision, notify }) {
  const [changes, setChanges] = useState(0);
  const [active, setActive] = useState(null);
  const { data, error } = useAdminResource('plugins.php?view=list', revision + changes);
  const columns = [
    { key: 'repository', header: 'Репозиторий', width: proportional(3, { minWidth: 180 }),
      renderCell: row => <Text maxLines={1}>{row.repository.replace('https://github.com/', '')}</Text> },
    { key: 'release_tag', header: 'Тег релиза', width: pixel(125), renderCell: row => row.release_tag || '—' },
    { key: 'status', header: 'Статус', width: pixel(132),
      renderCell: row => <Badge {...(pluginStatuses[row.status] || { label: row.status })} /> },
    { key: 'open', header: 'Действие', width: pixel(118),
      renderCell: row => <Button label="Открыть" size="sm" variant="ghost" icon={<Icon icon={PackageCheck} />} onClick={() => setActive(row)} /> },
  ];
  if (error) return <Banner status="error" title={error}
    endContent={<Button label="Повторить" onClick={() => setChanges(value => value + 1)} />} />;
  if (!data) return <Text role="status">Загрузка заявок…</Text>;
  return <VStack gap={4}>
    <Records title="Заявки на публикацию" description="Последние 100 заявок." rows={data} columns={columns} rowKey="id" empty="Заявок пока нет." />
    {active && <PluginReview row={active} close={() => setActive(null)} save={async body => {
      await adminRequest('plugins.php', { body });
      setActive(null);
      setChanges(value => value + 1);
      notify(body.action === 'prepare' ? 'Кандидат готов к проверке' : 'Статус заявки обновлён');
    }} />}
  </VStack>;
}
