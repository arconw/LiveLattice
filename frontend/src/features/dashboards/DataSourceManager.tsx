import { FlaskConical, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import type { CreateDataSourcePayload, DataSourceType, DataSourceView, UpdateDataSourcePayload } from "../../contracts/dashboards";
import { dataSourceTypes } from "../../contracts/dashboards";
import { Badge, Button, ErrorState, Input, Panel, Select, StatusChip } from "../../design-system/components";

type DataSourceFormProps = {
  workspaceId: string;
  dataSource?: DataSourceView | null;
  onSubmit: (payload: CreateDataSourcePayload | UpdateDataSourcePayload) => Promise<void>;
  onCancel?: () => void;
};

export function DataSourceForm({ workspaceId, dataSource, onSubmit, onCancel }: DataSourceFormProps) {
  const editing = Boolean(dataSource);
  const [name, setName] = useState(dataSource?.name ?? "");
  const [type, setType] = useState<DataSourceType>(dataSource?.type ?? "CLICKHOUSE");
  const [host, setHost] = useState("");
  const [port, setPort] = useState("");
  const [database, setDatabase] = useState("");
  const [table, setTable] = useState("");
  const [url, setUrl] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError("");

    if (!name.trim()) {
      setFormError("Name is required.");
      return;
    }

    setSubmitting(true);

    try {
      if (editing) {
        await onSubmit({ name: name.trim() });
      } else {
        await onSubmit({
          workspaceId,
          name: name.trim(),
          type,
          config: buildConfig({ type, host, port, database, table, url, username, password, token })
        });
      }

      setPassword("");
      setToken("");
      setHost("");
      setPort("");
      setDatabase("");
      setTable("");
      setUrl("");
      setUsername("");

      if (!editing) {
        setName("");
        setType("CLICKHOUSE");
      }
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Data source could not be saved.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="data-source-form" onSubmit={submit} aria-describedby={formError ? "data-source-form-error" : undefined}>
      {formError ? (
        <div className="form-alert form-alert-danger" id="data-source-form-error" role="alert">
          {formError}
        </div>
      ) : null}
      <div className="form-field">
        <label className="field-label" htmlFor={editing ? `data-source-name-${dataSource?.id}` : "data-source-name-new"}>
          Name
        </label>
        <Input id={editing ? `data-source-name-${dataSource?.id}` : "data-source-name-new"} value={name} onChange={(event) => setName(event.target.value)} autoComplete="off" />
      </div>

      <div className="form-field">
        <label className="field-label" htmlFor={editing ? `data-source-type-${dataSource?.id}` : "data-source-type-new"}>
          Type
        </label>
        <Select id={editing ? `data-source-type-${dataSource?.id}` : "data-source-type-new"} value={type} onChange={(event) => setType(event.target.value as DataSourceType)} disabled={editing}>
          {dataSourceTypes.map((candidate) => (
            <option value={candidate} key={candidate}>
              {candidate}
            </option>
          ))}
        </Select>
      </div>

      {editing ? (
        <div className="secret-saved-state" role="status">
          <StatusChip tone="info">secret saved but hidden</StatusChip>
          <p className="small-copy">Core does not return saved connection config or secret values.</p>
        </div>
      ) : (
        <DataSourceConfigFields type={type} host={host} port={port} database={database} table={table} url={url} username={username} password={password} token={token} setHost={setHost} setPort={setPort} setDatabase={setDatabase} setTable={setTable} setUrl={setUrl} setUsername={setUsername} setPassword={setPassword} setToken={setToken} />
      )}

      <div className="form-action-row">
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting ? "Saving" : editing ? "Save metadata" : "Create data source"}
        </Button>
        {onCancel ? (
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        ) : null}
      </div>
    </form>
  );
}

export function DataSourceManager({ workspaceId, dataSources, canManage, onCreate, onUpdate, onDelete, onTest }: { workspaceId: string; dataSources: DataSourceView[]; canManage: boolean; onCreate: (payload: CreateDataSourcePayload) => Promise<void>; onUpdate: (dataSourceId: string, payload: UpdateDataSourcePayload) => Promise<void>; onDelete: (dataSourceId: string) => Promise<void>; onTest: (dataSourceId: string) => Promise<boolean> }) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [connectionState, setConnectionState] = useState<Record<string, "idle" | "testing" | "healthy" | "failed">>({});
  const editingSource = dataSources.find((dataSource) => dataSource.id === editingId) ?? null;

  async function testConnection(dataSource: DataSourceView) {
    setConnectionState((current) => ({ ...current, [dataSource.id]: "testing" }));

    try {
      const healthy = await onTest(dataSource.id);
      setConnectionState((current) => ({ ...current, [dataSource.id]: healthy ? "healthy" : "failed" }));
    } catch {
      setConnectionState((current) => ({ ...current, [dataSource.id]: "failed" }));
    }
  }

  async function remove(dataSource: DataSourceView) {
    if (!window.confirm(`Delete data source ${dataSource.name}?`)) {
      return;
    }

    await onDelete(dataSource.id);
  }

  return (
    <Panel className="data-source-manager" as="aside">
      <div className="panel-title-row">
        <div>
          <span className="kicker">Data sources</span>
          <h2>Workspace connections</h2>
        </div>
        <Button variant="secondary" icon={<Plus aria-hidden="true" size={14} />} disabled={!canManage} onClick={() => setCreating(true)}>
          Add
        </Button>
      </div>

      {dataSources.length === 0 ? (
        <div className="query-empty-state" role="status">
          No data sources yet
        </div>
      ) : null}

      <div className="data-source-list">
        {dataSources.map((dataSource) => {
          const state = connectionState[dataSource.id] ?? "idle";

          return (
            <article className="data-source-card" key={dataSource.id}>
              <div>
                <strong>{dataSource.name}</strong>
                <span>{dataSource.type}</span>
              </div>
              <Badge tone="neutral">workspace scoped</Badge>
              {state === "healthy" ? <StatusChip tone="healthy">connection healthy</StatusChip> : null}
              {state === "failed" ? (
                <ErrorState title="Connection test failed" copy="The Gateway/Core data source test returned a failure. Rotate the write-only secret or check metadata." />
              ) : null}
              <div className="data-source-actions">
                <Button variant="ghost" icon={<FlaskConical aria-hidden="true" size={14} />} onClick={() => void testConnection(dataSource)}>
                  {state === "testing" ? "Testing" : "Test"}
                </Button>
                <Button variant="ghost" disabled={!canManage} onClick={() => setEditingId(dataSource.id)}>
                  Edit
                </Button>
                <Button variant="ghost" icon={<Trash2 aria-hidden="true" size={14} />} disabled={!canManage} onClick={() => void remove(dataSource)}>
                  Delete
                </Button>
              </div>
            </article>
          );
        })}
      </div>

      {creating ? (
        <section className="data-source-editor-panel">
          <span className="kicker">Create connection</span>
          <DataSourceForm
            workspaceId={workspaceId}
            onSubmit={async (payload) => {
              await onCreate(payload as CreateDataSourcePayload);
              setCreating(false);
            }}
            onCancel={() => setCreating(false)}
          />
        </section>
      ) : null}

      {editingSource ? (
        <section className="data-source-editor-panel">
          <span className="kicker">Edit metadata</span>
          <DataSourceForm
            workspaceId={workspaceId}
            dataSource={editingSource}
            onSubmit={async (payload) => {
              await onUpdate(editingSource.id, payload as UpdateDataSourcePayload);
              setEditingId(null);
            }}
            onCancel={() => setEditingId(null)}
          />
        </section>
      ) : null}
    </Panel>
  );
}

function DataSourceConfigFields({ type, host, port, database, table, url, username, password, token, setHost, setPort, setDatabase, setTable, setUrl, setUsername, setPassword, setToken }: { type: DataSourceType; host: string; port: string; database: string; table: string; url: string; username: string; password: string; token: string; setHost: (value: string) => void; setPort: (value: string) => void; setDatabase: (value: string) => void; setTable: (value: string) => void; setUrl: (value: string) => void; setUsername: (value: string) => void; setPassword: (value: string) => void; setToken: (value: string) => void }) {
  if (type === "REST_API" || type === "PROMETHEUS") {
    return (
      <>
        <div className="form-field">
          <label className="field-label" htmlFor="data-source-url">
            URL
          </label>
          <Input id="data-source-url" value={url} onChange={(event) => setUrl(event.target.value)} autoComplete="off" />
        </div>
        <div className="form-field">
          <label className="field-label" htmlFor="data-source-token">
            Token
          </label>
          <Input id="data-source-token" type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="new-password" />
        </div>
      </>
    );
  }

  if (type === "CSV") {
    return (
      <>
        <div className="form-field">
          <label className="field-label" htmlFor="data-source-url">
            File URL
          </label>
          <Input id="data-source-url" value={url} onChange={(event) => setUrl(event.target.value)} autoComplete="off" />
        </div>
        <div className="form-field">
          <label className="field-label" htmlFor="data-source-table">
            Table alias
          </label>
          <Input id="data-source-table" value={table} onChange={(event) => setTable(event.target.value)} autoComplete="off" />
        </div>
      </>
    );
  }

  return (
    <>
      <div className="form-field">
        <label className="field-label" htmlFor="data-source-host">
          Host
        </label>
        <Input id="data-source-host" value={host} onChange={(event) => setHost(event.target.value)} autoComplete="off" />
      </div>
      <div className="form-field two-column-field">
        <span>
          <label className="field-label" htmlFor="data-source-port">
            Port
          </label>
          <Input id="data-source-port" inputMode="numeric" value={port} onChange={(event) => setPort(event.target.value)} autoComplete="off" />
        </span>
        <span>
          <label className="field-label" htmlFor="data-source-database">
            Database
          </label>
          <Input id="data-source-database" value={database} onChange={(event) => setDatabase(event.target.value)} autoComplete="off" />
        </span>
      </div>
      <div className="form-field">
        <label className="field-label" htmlFor="data-source-table">
          Table
        </label>
        <Input id="data-source-table" value={table} onChange={(event) => setTable(event.target.value)} autoComplete="off" />
      </div>
      <div className="form-field two-column-field">
        <span>
          <label className="field-label" htmlFor="data-source-username">
            Username
          </label>
          <Input id="data-source-username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </span>
        <span>
          <label className="field-label" htmlFor="data-source-password">
            Password
          </label>
          <Input id="data-source-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" />
        </span>
      </div>
    </>
  );
}

function buildConfig({ type, host, port, database, table, url, username, password, token }: { type: DataSourceType; host: string; port: string; database: string; table: string; url: string; username: string; password: string; token: string }) {
  if (type === "REST_API" || type === "PROMETHEUS") {
    return compactRecord({
      url,
      token
    });
  }

  if (type === "CSV") {
    return compactRecord({
      url,
      table
    });
  }

  return compactRecord({
    host,
    port: port ? Number(port) : undefined,
    database,
    table,
    username,
    password
  });
}

function compactRecord(record: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(record).filter(([, value]) => value !== "" && value !== undefined));
}
