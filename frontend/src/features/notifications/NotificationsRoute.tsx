import { Bell, CheckCheck, Eye, EyeOff, Trash2, Webhook } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useOutletContext, useParams } from "react-router-dom";
import { AppError } from "../../contracts/api-client";
import { notificationPreferencesFixture, notificationsFixture } from "../../contracts/fixtures";
import {
  addNotificationWebhook,
  deleteNotificationWebhook,
  digestFrequencies,
  getNotificationPreferences,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationTargetHref,
  updateNotificationPreferences
} from "../../contracts/notifications";
import type { DigestFrequency, NotificationItem, NotificationPreferences } from "../../contracts/notifications";
import { Badge, Button, EmptyState, ErrorState, Input, LoadingState, Panel, Select, StatusChip } from "../../design-system/components";
import { useAuth } from "../auth/AuthProvider";
import type { ShellOutletContext } from "../shell/AppShell";

type NotificationLoadStatus = "loading" | "ready" | "empty" | "error";

const knownNotificationTypes = ["canvas.comment", "canvas.@mention", "canvas.export.complete", "canvas.import.complete", "dashboard.shared", "workspace.quota.warning", "member.joined", "system.announcement"];

export function NotificationsRoute() {
  const { workspaceSlug = "factory-floor" } = useParams();
  const outlet = useOutletContext<ShellOutletContext>();
  const auth = useAuth();
  const workspaceId = outlet.activeWorkspace?.id ?? notificationsFixture[0]?.workspaceId ?? workspaceSlug;
  const [notifications, setNotifications] = useState<NotificationItem[]>(notificationsFixture);
  const [preferences, setPreferences] = useState<NotificationPreferences>(notificationPreferencesFixture);
  const [status, setStatus] = useState<NotificationLoadStatus>("ready");
  const [error, setError] = useState<AppError | null>(null);
  const [readError, setReadError] = useState<string | null>(null);
  const [preferencesSaved, setPreferencesSaved] = useState(false);
  const [preferencesError, setPreferencesError] = useState<string | null>(null);
  const [webhookError, setWebhookError] = useState<string | null>(null);
  const unread = useMemo(() => notifications.filter((notification) => notification.readAt === null).length, [notifications]);

  useEffect(() => {
    const controller = new AbortController();
    setStatus("loading");

    Promise.all([listNotifications(auth.client, workspaceId, controller.signal), getNotificationPreferences(auth.client, controller.signal)])
      .then(([list, nextPreferences]) => {
        setNotifications(list.notifications);
        setPreferences(nextPreferences);
        setStatus(list.notifications.length > 0 ? "ready" : "empty");
        setError(null);
      })
      .catch((loadError) => {
        if (loadError instanceof AppError && loadError.code === "REQUEST_ABORTED") {
          return;
        }

        setError(loadError instanceof AppError ? loadError : new AppError({ status: 0, code: "NOTIFICATION_LOAD_FAILED", message: "Notifications could not be loaded.", retryable: true }));
        setStatus("error");
      });

    return () => controller.abort();
  }, [auth.client, workspaceId]);

  async function markRead(notification: NotificationItem) {
    if (notification.readAt) {
      return;
    }

    const previous = notifications;
    setReadError(null);
    setNotifications((current) => current.map((item) => (item.id === notification.id ? { ...item, readAt: new Date().toISOString() } : item)));

    try {
      await markNotificationRead(auth.client, notification.id);
    } catch {
      setNotifications(previous);
      setReadError("Read state update failed and was rolled back.");
    }
  }

  async function markAllRead() {
    const previous = notifications;
    setReadError(null);
    setNotifications((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? new Date().toISOString() })));

    try {
      await markAllNotificationsRead(auth.client, workspaceId);
    } catch {
      setNotifications(previous);
      setReadError("Mark all read failed and was rolled back.");
    }
  }

  async function savePreferences(nextPreferences: NotificationPreferences) {
    const previousPreferences = preferences;
    setPreferences(nextPreferences);
    setPreferencesSaved(false);
    setPreferencesError(null);

    try {
      const saved = await updateNotificationPreferences(auth.client, nextPreferences);
      setPreferences(saved);
      setPreferencesSaved(true);
    } catch {
      setPreferences(previousPreferences);
      setPreferencesSaved(false);
      setPreferencesError("Preference save failed and was rolled back.");
    }
  }

  async function addWebhook(payload: { url: string; secret: string; events: string[] }) {
    setWebhookError(null);

    try {
      const webhook = await addNotificationWebhook(auth.client, payload);

      if (!webhook) {
        throw new Error("Webhook response missing");
      }

      setPreferences((current) => ({ ...current, webhooks: [...current.webhooks, webhook] }));
    } catch {
      setWebhookError("Webhook could not be added. Confirm the URL and event selection.");
    }
  }

  async function removeWebhook(webhookId: string) {
    const previous = preferences;
    setPreferences((current) => ({ ...current, webhooks: current.webhooks.filter((webhook) => webhook.id !== webhookId) }));

    try {
      await deleteNotificationWebhook(auth.client, webhookId);
    } catch {
      setPreferences(previous);
      setWebhookError("Webhook removal failed.");
    }
  }

  return (
    <section className="feature-route notifications-route" aria-labelledby="notifications-route-title">
      <div className="route-heading">
        <span className="kicker">Canonical inbox</span>
        <h1 id="notifications-route-title">Notifications</h1>
        <p>Unread state, target links, digest frequency, muted types, and webhooks preserve backend notification vocabulary.</p>
      </div>

      <div className="notification-summary-strip">
        <Panel as="section">
          <span className="kicker">Unread</span>
          <h2>{unread}</h2>
          <p className="small-copy">Unread is visible in the inbox and shell; toasts are only transient hints.</p>
        </Panel>
        <Panel as="section">
          <span className="kicker">Digest</span>
          <h2>{preferences.emailDigest}</h2>
          <p className="small-copy">Allowed backend enum values are instant, hourly, daily, and never.</p>
        </Panel>
      </div>

      <div className="feature-grid notifications-layout">
        <Panel className="notification-inbox-panel" as="section">
          <div className="panel-heading-row">
            <div>
              <span className="kicker">Inbox</span>
              <h2>Workspace notifications</h2>
            </div>
            <Button variant="secondary" icon={<CheckCheck size={16} aria-hidden="true" />} onClick={() => void markAllRead()}>
              Mark all read
            </Button>
          </div>
          {readError ? (
            <div className="form-alert form-alert-danger" role="alert">
              {readError}
            </div>
          ) : null}
          {status === "loading" ? <LoadingState label="Loading inbox" /> : null}
          {status === "error" && error ? <ErrorState title="Notifications unavailable" copy={error.message} requestId={error.requestId} /> : null}
          {status === "empty" ? <EmptyState title="Inbox empty" copy="Notifications for mentions, exports, quota warnings, and shared dashboards will appear here." /> : null}
          <div className="notification-card-list">
            {notifications.map((notification) => (
              <article className={`notification-card ${notification.readAt ? "is-read" : "is-unread"}`} key={notification.id}>
                <div className="notification-card-heading">
                  <div>
                    <Badge tone={notification.readAt ? "neutral" : "danger"}>{notification.readAt ? "read" : "unread"}</Badge>
                    <h3>{notification.title}</h3>
                  </div>
                  <StatusChip tone="info">{notification.type}</StatusChip>
                </div>
                <p className="small-copy">{notification.body}</p>
                <div className="job-action-row">
                  <Link className="button button-primary" to={notificationTargetHref(notification.target, workspaceSlug)}>
                    <Bell size={16} aria-hidden="true" />
                    <span>Open target</span>
                  </Link>
                  <Button variant="secondary" icon={notification.readAt ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />} disabled={Boolean(notification.readAt)} onClick={() => void markRead(notification)}>
                    {notification.readAt ? "Read" : "Mark read"}
                  </Button>
                </div>
              </article>
            ))}
          </div>
        </Panel>

        <Panel className="preferences-panel" as="aside">
          <PreferencesForm preferences={preferences} saved={preferencesSaved} error={preferencesError} onSave={(next) => void savePreferences(next)} />
          <WebhookForm preferences={preferences} error={webhookError} onAdd={(payload) => void addWebhook(payload)} onRemove={(webhookId) => void removeWebhook(webhookId)} />
        </Panel>
      </div>
    </section>
  );
}

function PreferencesForm({ preferences, saved, error, onSave }: { preferences: NotificationPreferences; saved: boolean; error: string | null; onSave: (preferences: NotificationPreferences) => void }) {
  const [emailDigest, setEmailDigest] = useState<DigestFrequency>(preferences.emailDigest);
  const [mutedTypes, setMutedTypes] = useState<string[]>(preferences.mutedTypes);

  useEffect(() => {
    setEmailDigest(preferences.emailDigest);
    setMutedTypes(preferences.mutedTypes);
  }, [preferences]);

  function toggleMutedType(type: string) {
    setMutedTypes((current) => (current.includes(type) ? current.filter((item) => item !== type) : [...current, type]));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSave({ ...preferences, emailDigest, mutedTypes });
  }

  return (
    <form className="preferences-form" onSubmit={submit}>
      <div className="panel-heading-row">
        <div>
          <span className="kicker">Preferences</span>
          <h2>Email and mute rules</h2>
        </div>
        {saved ? <StatusChip tone="healthy">saved</StatusChip> : null}
      </div>
      {error ? (
        <div className="form-alert form-alert-danger" role="alert">
          {error}
        </div>
      ) : null}
      <div className="form-field">
        <label className="field-label" htmlFor="digest-frequency">
          Digest frequency
        </label>
        <Select id="digest-frequency" value={emailDigest} onChange={(event) => setEmailDigest(event.target.value as DigestFrequency)}>
          {digestFrequencies.map((frequency) => (
            <option value={frequency} key={frequency}>
              {frequency}
            </option>
          ))}
        </Select>
      </div>
      <fieldset className="facet-group">
        <legend>Muted types</legend>
        {knownNotificationTypes.map((type) => (
          <label className="facet-option" key={type}>
            <input type="checkbox" checked={mutedTypes.includes(type)} onChange={() => toggleMutedType(type)} />
            <span>{type}</span>
          </label>
        ))}
      </fieldset>
      <Button variant="primary" type="submit">
        Save preferences
      </Button>
    </form>
  );
}

function WebhookForm({ preferences, error, onAdd, onRemove }: { preferences: NotificationPreferences; error: string | null; onAdd: (payload: { url: string; secret: string; events: string[] }) => void; onRemove: (webhookId: string) => void }) {
  const [url, setUrl] = useState("");
  const [secret, setSecret] = useState("");
  const [events, setEvents] = useState<string[]>(["canvas.export.complete"]);

  function toggleEvent(type: string) {
    setEvents((current) => (current.includes(type) ? current.filter((item) => item !== type) : [...current, type]));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!url.trim() || !secret.trim() || events.length === 0) {
      return;
    }

    onAdd({ url: url.trim(), secret, events });
    setUrl("");
    setSecret("");
  }

  return (
    <section className="webhook-section" aria-labelledby="webhook-title">
      <div className="panel-heading-row">
        <div>
          <span className="kicker">Webhooks</span>
          <h2 id="webhook-title">Delivery endpoints</h2>
        </div>
        <Webhook size={20} aria-hidden="true" />
      </div>
      {error ? (
        <div className="form-alert form-alert-danger" role="alert">
          {error}
        </div>
      ) : null}
      <form className="preferences-form" onSubmit={submit}>
        <div className="form-field">
          <label className="field-label" htmlFor="webhook-url">
            URL
          </label>
          <Input id="webhook-url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://hooks.example.test/livelattice" />
        </div>
        <div className="form-field">
          <label className="field-label" htmlFor="webhook-secret">
            Secret
          </label>
          <Input id="webhook-secret" type="password" value={secret} onChange={(event) => setSecret(event.target.value)} autoComplete="new-password" />
        </div>
        <fieldset className="facet-group">
          <legend>Events</legend>
          {knownNotificationTypes.slice(0, 6).map((type) => (
            <label className="facet-option" key={type}>
              <input type="checkbox" checked={events.includes(type)} onChange={() => toggleEvent(type)} />
              <span>{type}</span>
            </label>
          ))}
        </fieldset>
        <Button variant="secondary" type="submit">
          Add webhook
        </Button>
      </form>
      <p className="small-copy">Secret is write-only and is not displayed after creation.</p>
      <div className="webhook-list">
        {preferences.webhooks.map((webhook) => (
          <article className="webhook-card" key={webhook.id}>
            <div>
              <strong>{webhook.url}</strong>
              <p className="small-copy">Events: {webhook.events.join(", ") || "none"}</p>
            </div>
            <Button variant="ghost" icon={<Trash2 size={16} aria-hidden="true" />} onClick={() => onRemove(webhook.id)}>
              Remove
            </Button>
          </article>
        ))}
      </div>
    </section>
  );
}
