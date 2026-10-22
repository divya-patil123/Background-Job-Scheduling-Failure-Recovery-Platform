import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Auth0Provider, useAuth0 } from "@auth0/auth0-react";
import {
  Activity,
  AlertTriangle,
  Bell,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileClock,
  LayoutDashboard,
  LoaderCircle,
  LogOut,
  Menu,
  Play,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  ToggleLeft,
  ToggleRight,
  Trash2,
  X,
  Zap,
} from "lucide-react";
import "./styles.css";

type AuthConfig = {
  enabled: boolean;
  developmentMode: boolean;
  domain?: string;
  clientId?: string;
  audience?: string;
};

type Job = {
  id: number;
  name: string;
  description?: string;
  taskType: "NOTIFICATION" | "REPORT" | "BATCH_PROCESSING";
  scheduleType: "INTERVAL" | "CRON";
  intervalSeconds?: number;
  cronExpression?: string;
  configuration: Record<string, unknown>;
  enabled: boolean;
  maxRetries: number;
  initialRetryDelayMs: number;
  maxRetryDelayMs: number;
  failureThreshold: number;
  consecutiveFailures: number;
  nextRunAt: string;
  createdAt: string;
  updatedAt: string;
};

type Execution = {
  id: number;
  jobId: number;
  jobName: string;
  status: "QUEUED" | "RUNNING" | "RETRYING" | "SUCCESS" | "FAILED";
  triggerType: "MANUAL" | "SCHEDULED";
  attemptCount: number;
  createdAt: string;
  startedAt?: string;
  endedAt?: string;
  durationMs?: number;
  nextRetryAt?: string;
  errorMessage?: string;
};

type Attempt = {
  id: number;
  attemptNumber: number;
  status: "RUNNING" | "SUCCESS" | "FAILED";
  startedAt: string;
  endedAt?: string;
  durationMs?: number;
  errorMessage?: string;
};

type Alert = {
  id: number;
  jobId: number;
  jobName: string;
  executionId: number;
  message: string;
  failureCount: number;
  acknowledged: boolean;
  createdAt: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

type UserInfo = {
  subject: string;
  name: string;
  email?: string;
  roles: string[];
};

type ApiCall = <T>(path: string, options?: RequestInit) => Promise<T>;

const apiCall: ApiCall = async <T,>(path: string, options: RequestInit = {}) => {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? `Request failed (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
};

function AuthenticatedEntry({ config }: { config: AuthConfig }) {
  const { isLoading, isAuthenticated, loginWithRedirect, logout, getAccessTokenSilently, user } = useAuth0();
  if (isLoading) return <LoadingScreen label="Connecting securely…" />;
  if (!isAuthenticated) {
    return (
      <div className="login-screen">
        <div className="login-card">
          <BrandMark />
          <span className="eyebrow">BACKGROUND JOB OPERATIONS</span>
          <h1>Good work runs<br /><span>in the background.</span></h1>
          <p>Sign in to monitor schedules, investigate executions, and keep your workflows healthy.</p>
          <button className="button button-primary button-wide" onClick={() => void loginWithRedirect()}>
            Sign in to JobFlow <ChevronRight size={17} />
          </button>
          <div className="login-foot"><ShieldCheck size={15} /> Protected with Auth0</div>
        </div>
      </div>
    );
  }
  const call: ApiCall = async <T,>(path: string, options: RequestInit = {}) => {
    const token = await getAccessTokenSilently({ authorizationParams: { audience: config.audience } });
    return apiCall<T>(path, {
      ...options,
      headers: { ...options.headers, Authorization: `Bearer ${token}` },
    });
  };
  return (
    <JobFlowDashboard
      call={call}
      authEnabled
      onLogout={() => logout({ logoutParams: { returnTo: window.location.origin } })}
      fallbackName={user?.name ?? user?.email ?? "Operator"}
    />
  );
}

function Boot() {
  const [config, setConfig] = useState<AuthConfig | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    apiCall<AuthConfig>("/api/auth/config")
      .then(setConfig)
      .catch((reason: Error) => setError(reason.message));
  }, []);
  if (error) return <FailureScreen message={`Could not load application settings: ${error}`} />;
  if (!config) return <LoadingScreen label="Starting JobFlow…" />;
  if (config.enabled && config.domain && config.clientId && config.audience) {
    return (
      <Auth0Provider
        domain={config.domain}
        clientId={config.clientId}
        authorizationParams={{
          redirect_uri: window.location.origin,
          audience: config.audience,
          scope: "openid profile email",
        }}
        cacheLocation="memory"
      >
        <AuthenticatedEntry config={config} />
      </Auth0Provider>
    );
  }
  return (
    <JobFlowDashboard
      call={apiCall}
      authEnabled={false}
      onLogout={() => undefined}
      fallbackName={config.developmentMode ? "Local developer" : "Authentication not configured"}
      developmentMode={config.developmentMode}
    />
  );
}

function JobFlowDashboard({
  call,
  authEnabled,
  onLogout,
  fallbackName,
  developmentMode = false,
}: {
  call: ApiCall;
  authEnabled: boolean;
  onLogout: () => void;
  fallbackName: string;
  developmentMode?: boolean;
}) {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [executions, setExecutions] = useState<Page<Execution> | null>(null);
  const [alerts, setAlerts] = useState<Page<Alert> | null>(null);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [section, setSection] = useState<"overview" | "jobs" | "executions" | "alerts">("overview");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Job | null>(null);
  const [selectedExecution, setSelectedExecution] = useState<number | null>(null);
  const [detail, setDetail] = useState<{ execution: Execution; attempts: Attempt[] } | null>(null);
  const [compactMenu, setCompactMenu] = useState(false);
  const isAdmin = user?.roles.includes("ADMIN") ?? false;
  const canOperate = !authEnabled || isAdmin || (user?.roles.includes("OPERATOR") ?? false);

  const refresh = async () => {
    setError("");
    const [jobRows, executionPage, alertPage, userInfo] = await Promise.all([
      call<Job[]>("/api/jobs"),
      call<Page<Execution>>("/api/executions?page=0&size=8"),
      call<Page<Alert>>("/api/alerts?page=0&size=6"),
      authEnabled ? call<UserInfo>("/api/auth/me") : Promise.resolve(null),
    ]);
    setJobs(jobRows);
    setExecutions(executionPage);
    setAlerts(alertPage);
    if (userInfo) setUser(userInfo);
  };

  useEffect(() => {
    let active = true;
    Promise.resolve()
      .then(refresh)
      .catch((reason: Error) => {
        if (active) setError(reason.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [authEnabled]);

  useEffect(() => {
    if (selectedExecution === null) {
      setDetail(null);
      return;
    }
    call<{ execution: Execution; attempts: Attempt[] }>(`/api/executions/${selectedExecution}`)
      .then(setDetail)
      .catch((reason: Error) => setError(reason.message));
  }, [selectedExecution]);

  const visibleJobs = useMemo(
    () => jobs.filter((job) => job.name.toLowerCase().includes(query.toLowerCase())
      || job.taskType.toLowerCase().includes(query.toLowerCase())),
    [jobs, query],
  );
  const filteredExecutions = useMemo(
    () => (executions?.content ?? []).filter((execution) => !statusFilter || execution.status === statusFilter),
    [executions, statusFilter],
  );
  const activeJobs = jobs.filter((job) => job.enabled).length;
  const failedExecutions = executions?.content.filter((execution) => execution.status === "FAILED").length ?? 0;
  const pendingAlerts = alerts?.content.filter((alert) => !alert.acknowledged).length ?? 0;
  const completedExecutions = executions?.content.filter((execution) => execution.status === "SUCCESS").length ?? 0;
  const successRate = executions?.content.length
    ? Math.round((completedExecutions / executions.content.length) * 100)
    : 100;

  const runAction = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    setError("");
    try {
      await action();
      await refresh();
      setNotice(message);
      window.setTimeout(() => setNotice(""), 3500);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Action failed");
    } finally {
      setBusy(false);
    }
  };

  const openCreate = () => { setEditing(null); setShowForm(true); };
  const openEdit = (job: Job) => { setEditing(job); setShowForm(true); };

  if (loading) return <LoadingScreen label="Loading your workspace…" />;

  return (
    <div className="app-shell">
      <aside className={`sidebar ${compactMenu ? "sidebar-open" : ""}`}>
        <div className="brand"><BrandMark /><span>jobflow<span className="brand-dot">.</span></span><button className="icon-button mobile-close" onClick={() => setCompactMenu(false)} aria-label="Close navigation"><X size={18} /></button></div>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">
          <NavItem icon={<LayoutDashboard size={18} />} label="Overview" active={section === "overview"} onClick={() => setSection("overview")} />
          <NavItem icon={<Zap size={18} />} label="Scheduled jobs" count={jobs.length} active={section === "jobs"} onClick={() => setSection("jobs")} />
          <NavItem icon={<FileClock size={18} />} label="Executions" active={section === "executions"} onClick={() => setSection("executions")} />
          <NavItem icon={<Bell size={18} />} label="Alerts" count={pendingAlerts} active={section === "alerts"} onClick={() => setSection("alerts")} />
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-status"><span className="status-pulse" /> Scheduler online</div>
          <div className="profile">
            <div className="avatar">{fallbackName.slice(0, 1).toUpperCase()}</div>
            <div className="profile-text"><strong>{fallbackName}</strong><span>{user?.roles.join(" · ") || (developmentMode ? "Development" : "Auth not configured")}</span></div>
            {authEnabled && <button className="icon-button logout-button" title="Sign out" onClick={onLogout}><LogOut size={16} /></button>}
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button className="icon-button mobile-menu" onClick={() => setCompactMenu(true)} aria-label="Open navigation"><Menu size={20} /></button>
          <div className="breadcrumbs"><span>JobFlow</span><ChevronRight size={14} /><strong>{sectionTitle(section)}</strong></div>
          <div className="topbar-actions">
            <div className="system-chip"><span className="status-pulse" /> All systems operational</div>
            <button className="icon-button" onClick={() => void runAction(refresh, "Workspace refreshed")} aria-label="Refresh"><RefreshCw size={17} /></button>
            <button className="avatar avatar-small" title={fallbackName}>{fallbackName.slice(0, 1).toUpperCase()}</button>
          </div>
        </header>

        <div className="page-content">
          {!authEnabled && !developmentMode && (
            <div className="banner banner-warning"><ShieldCheck size={17} /><span>Auth0 is not configured. Backend API requests are blocked until Auth0 is configured, except in the development H2 profile.</span></div>
          )}
          {developmentMode && <div className="banner banner-dev"><Sparkles size={16} /><span>Local development mode · H2 in-memory database · data resets when the app stops</span></div>}
          {error && <div className="banner banner-error"><AlertTriangle size={17} /><span>{error}</span><button className="icon-button" onClick={() => setError("")}><X size={15} /></button></div>}
          {notice && <div className="banner banner-success"><Check size={17} /><span>{notice}</span><button className="icon-button" onClick={() => setNotice("")}><X size={15} /></button></div>}

          {section === "overview" && (
            <>
              <PageHeading eyebrow="WORKSPACE OVERVIEW" title="Good morning" description="Here’s what’s happening across your background jobs." action={canOperate ? <button className="button button-primary" onClick={openCreate}><Plus size={16} /> Create job</button> : undefined} />
              <section className="stats-grid">
                <StatCard icon={<Zap size={18} />} label="Active jobs" value={activeJobs} foot={`${jobs.length - activeJobs} paused`} tone="blue" />
                <StatCard icon={<Activity size={18} />} label="Success rate" value={`${successRate}%`} foot="Recent executions" tone="green" />
                <StatCard icon={<Clock3 size={18} />} label="Executions today" value={executions?.totalElements ?? 0} foot={`${completedExecutions} successful`} tone="purple" />
                <StatCard icon={<Bell size={18} />} label="Open alerts" value={pendingAlerts} foot={pendingAlerts ? "Needs your attention" : "You’re all caught up"} tone={pendingAlerts ? "orange" : "green"} />
              </section>
              <div className="content-grid">
                <section className="panel panel-wide">
                  <PanelHeading title="Recent executions" subtitle="Latest activity from your scheduled work" action={<button className="text-button" onClick={() => setSection("executions")}>View all <ChevronRight size={15} /></button>} />
                  <ExecutionTable rows={executions?.content.slice(0, 5) ?? []} onSelect={setSelectedExecution} />
                </section>
                <section className="panel">
                  <PanelHeading title="Job health" subtitle="Overview by current status" />
                  <div className="health-summary">
                    <div className="health-ring" style={{ "--progress": `${successRate}%` } as React.CSSProperties}><div><strong>{successRate}%</strong><span>success</span></div></div>
                    <div className="health-legend"><span><i className="legend-dot dot-green" />Successful <b>{completedExecutions}</b></span><span><i className="legend-dot dot-red" />Failed <b>{failedExecutions}</b></span><span><i className="legend-dot dot-gray" />Active jobs <b>{activeJobs}</b></span></div>
                  </div>
                  <div className="panel-divider" />
                  <div className="mini-heading">Upcoming jobs <span>{jobs.filter((job) => job.enabled).length}</span></div>
                  <div className="upcoming-list">
                    {jobs.filter((job) => job.enabled).slice(0, 4).map((job) => <div className="upcoming-item" key={job.id}><div className="job-glyph"><Settings2 size={15} /></div><div className="upcoming-name"><strong>{job.name}</strong><span>{job.taskType.replace("_", " ")}</span></div><span className="time-text">{formatRelative(job.nextRunAt)}</span></div>)}
                    {!jobs.some((job) => job.enabled) && <EmptyInline text="No active jobs yet." />}
                  </div>
                </section>
              </div>
              <section className="panel">
                <PanelHeading title="Scheduled jobs" subtitle="Manage your background workflows" action={<button className="text-button" onClick={() => setSection("jobs")}>Manage jobs <ChevronRight size={15} /></button>} />
                <JobTable rows={jobs.slice(0, 5)} busy={busy} canOperate={canOperate} isAdmin={isAdmin} onEdit={openEdit} onToggle={(job) => void runAction(() => call(`/api/jobs/${job.id}/enabled`, { method: "PATCH", body: JSON.stringify({ enabled: !job.enabled }) }), `${job.name} ${job.enabled ? "paused" : "enabled"}`)} onTrigger={(job) => void runAction(() => call(`/api/jobs/${job.id}/trigger`, { method: "POST" }), `${job.name} triggered`)} onDelete={(job) => { if (window.confirm(`Delete "${job.name}"? Jobs with execution history cannot be deleted.`)) void runAction(() => call(`/api/jobs/${job.id}`, { method: "DELETE" }), `${job.name} deleted`); }} />
              </section>
            </>
          )}

          {section === "jobs" && (
            <>
              <PageHeading eyebrow="WORKSPACE / JOBS" title="Scheduled jobs" description="Create, update, pause, and manually trigger your background workflows." action={canOperate ? <button className="button button-primary" onClick={openCreate}><Plus size={16} /> Create job</button> : undefined} />
              <section className="panel">
                <div className="toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search jobs…" /></div><div className="toolbar-meta">{visibleJobs.length} jobs</div></div>
                <JobTable rows={visibleJobs} busy={busy} canOperate={canOperate} isAdmin={isAdmin} onEdit={openEdit} onToggle={(job) => void runAction(() => call(`/api/jobs/${job.id}/enabled`, { method: "PATCH", body: JSON.stringify({ enabled: !job.enabled }) }), `${job.name} ${job.enabled ? "paused" : "enabled"}`)} onTrigger={(job) => void runAction(() => call(`/api/jobs/${job.id}/trigger`, { method: "POST" }), `${job.name} triggered`)} onDelete={(job) => { if (window.confirm(`Delete "${job.name}"? Jobs with execution history cannot be deleted.`)) void runAction(() => call(`/api/jobs/${job.id}`, { method: "DELETE" }), `${job.name} deleted`); }} />
              </section>
            </>
          )}

          {section === "executions" && (
            <>
              <PageHeading eyebrow="WORKSPACE / HISTORY" title="Execution history" description="Inspect every run, retry, and attempt across your jobs." />
              <section className="panel">
                <div className="toolbar"><div className="toolbar-meta">{executions?.totalElements ?? 0} total executions</div><select className="select-control" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">All statuses</option>{["QUEUED", "RUNNING", "RETRYING", "SUCCESS", "FAILED"].map((status) => <option key={status}>{status}</option>)}</select></div>
                <ExecutionTable rows={filteredExecutions} onSelect={setSelectedExecution} />
                <Pagination page={page} pages={executions?.totalPages ?? 0} onPage={(nextPage) => { setPage(nextPage); void call<Page<Execution>>(`/api/executions?page=${nextPage}&size=20${statusFilter ? `&status=${statusFilter}` : ""}`).then(setExecutions).catch((reason: Error) => setError(reason.message)); }} />
              </section>
            </>
          )}

          {section === "alerts" && (
            <>
              <PageHeading eyebrow="WORKSPACE / ALERTS" title="Alerts & incidents" description="Threshold alerts created after repeated consecutive job failures." />
              <section className="panel">
                <PanelHeading title="Failure alerts" subtitle={`${pendingAlerts} unacknowledged`} />
                <div className="alerts-list">
                  {(alerts?.content ?? []).map((alert) => <article className={`alert-row ${alert.acknowledged ? "alert-acknowledged" : ""}`} key={alert.id}><div className="alert-icon"><AlertTriangle size={17} /></div><div className="alert-copy"><div className="alert-title"><strong>{alert.jobName}</strong><StatusBadge status={alert.acknowledged ? "SUCCESS" : "FAILED"} /></div><p>{alert.message} <span>· {alert.failureCount} consecutive failures</span></p><div className="alert-meta">Execution #{alert.executionId} <span>·</span> {formatDate(alert.createdAt)}</div></div>{!alert.acknowledged && canOperate && <button className="button button-secondary button-small" disabled={busy} onClick={() => void runAction(() => call(`/api/alerts/${alert.id}/acknowledge`, { method: "PATCH" }), "Alert acknowledged")}><Check size={15} /> Acknowledge</button>}</article>)}
                  {!alerts?.content.length && <EmptyState icon={<Bell size={22} />} title="No alerts" text="When a job reaches its failure threshold, you’ll see an alert here." />}
                </div>
              </section>
            </>
          )}
          <footer className="footer"><span>JobFlow <span className="brand-dot">·</span> Background work, in control.</span><span>API status <b className="footer-status"><i /> Operational</b></span></footer>
        </div>
      </main>

      {showForm && <JobForm job={editing} call={call} onClose={() => setShowForm(false)} onSaved={async () => { setShowForm(false); await runAction(refresh, editing ? "Job updated" : "Job created"); }} />}
      {selectedExecution !== null && <ExecutionDrawer executionId={selectedExecution} detail={detail} onClose={() => setSelectedExecution(null)} />}
    </div>
  );
}

function JobForm({ job, call, onClose, onSaved }: { job: Job | null; call: ApiCall; onClose: () => void; onSaved: () => Promise<void> }) {
  const [name, setName] = useState(job?.name ?? "");
  const [description, setDescription] = useState(job?.description ?? "");
  const [taskType, setTaskType] = useState<Job["taskType"]>(job?.taskType ?? "REPORT");
  const [scheduleType, setScheduleType] = useState<Job["scheduleType"]>(job?.scheduleType ?? "INTERVAL");
  const [intervalSeconds, setIntervalSeconds] = useState(String(job?.intervalSeconds ?? 3600));
  const [cronExpression, setCronExpression] = useState(job?.cronExpression ?? "0 0 * * * *");
  const [taskConfig, setTaskConfig] = useState(() => initialTaskConfig(job));
  const [maxRetries, setMaxRetries] = useState(String(job?.maxRetries ?? 3));
  const [initialDelay, setInitialDelay] = useState(String(job?.initialRetryDelayMs ?? 1000));
  const [maxDelay, setMaxDelay] = useState(String(job?.maxRetryDelayMs ?? 60000));
  const [threshold, setThreshold] = useState(String(job?.failureThreshold ?? 3));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const configKey = taskType === "REPORT" ? "reportName" : taskType === "NOTIFICATION" ? "message" : "batchSize";

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    const body = {
      name: name.trim(),
      description: description.trim() || null,
      taskType,
      scheduleType,
      intervalSeconds: scheduleType === "INTERVAL" ? Number(intervalSeconds) : null,
      cronExpression: scheduleType === "CRON" ? cronExpression : null,
      configuration: { [configKey]: taskType === "BATCH_PROCESSING" ? Number(taskConfig) : taskConfig },
      enabled: job?.enabled ?? true,
      maxRetries: Number(maxRetries),
      initialRetryDelayMs: Number(initialDelay),
      maxRetryDelayMs: Number(maxDelay),
      failureThreshold: Number(threshold),
    };
    try {
      await call(job ? `/api/jobs/${job.id}` : "/api/jobs", { method: job ? "PUT" : "POST", body: JSON.stringify(body) });
      await onSaved();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not save job");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <form className="job-modal" onSubmit={(event) => void submit(event)}>
        <div className="modal-header"><div><span className="eyebrow">{job ? "EDIT WORKFLOW" : "NEW WORKFLOW"}</span><h2>{job ? "Update job" : "Create a job"}</h2></div><button type="button" className="icon-button" onClick={onClose}><X size={18} /></button></div>
        {error && <div className="form-error">{error}</div>}
        <div className="form-grid">
          <label className="field field-full">Job name<input required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. nightly-report" /></label>
          <label className="field field-full">Description <span className="optional">Optional</span><input maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="What does this job do?" /></label>
          <label className="field">Task type<select value={taskType} onChange={(event) => { const next = event.target.value as Job["taskType"]; setTaskType(next); setTaskConfig(next === "BATCH_PROCESSING" ? "100" : ""); }}><option value="REPORT">Report</option><option value="NOTIFICATION">Notification</option><option value="BATCH_PROCESSING">Batch processing</option></select></label>
          <label className="field">Schedule type<select value={scheduleType} onChange={(event) => setScheduleType(event.target.value as Job["scheduleType"])}><option value="INTERVAL">Interval</option><option value="CRON">Cron expression</option></select></label>
          {scheduleType === "INTERVAL" ? <label className="field field-full">Run every (seconds)<input type="number" min="1" max="31536000" required value={intervalSeconds} onChange={(event) => setIntervalSeconds(event.target.value)} /></label> : <label className="field field-full">Spring cron expression<input required value={cronExpression} onChange={(event) => setCronExpression(event.target.value)} placeholder="0 0 * * * *" /></label>}
          <label className="field field-full">{taskConfigLabel(taskType)}<input required type={taskType === "BATCH_PROCESSING" ? "number" : "text"} min={taskType === "BATCH_PROCESSING" ? 1 : undefined} max={taskType === "BATCH_PROCESSING" ? 100000 : undefined} maxLength={taskType === "REPORT" ? 120 : 2000} value={taskConfig} onChange={(event) => setTaskConfig(event.target.value)} /></label>
        </div>
        <div className="form-section-label">RETRY & FAILURE POLICY</div>
        <div className="form-grid form-grid-four">
          <label className="field">Max retries<input type="number" min="0" max="20" value={maxRetries} onChange={(event) => setMaxRetries(event.target.value)} /></label>
          <label className="field">Initial delay (ms)<input type="number" min="0" value={initialDelay} onChange={(event) => setInitialDelay(event.target.value)} /></label>
          <label className="field">Max delay (ms)<input type="number" min="0" value={maxDelay} onChange={(event) => setMaxDelay(event.target.value)} /></label>
          <label className="field">Alert threshold<input type="number" min="1" max="1000" value={threshold} onChange={(event) => setThreshold(event.target.value)} /></label>
        </div>
        <div className="modal-footer"><button type="button" className="button button-secondary" onClick={onClose}>Cancel</button><button type="submit" className="button button-primary" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />}{job ? "Save changes" : "Create job"}</button></div>
      </form>
    </div>
  );

  function initialTaskConfig(currentJob: Job | null) {
    if (!currentJob) return "Daily summary";
    const key = currentJob.taskType === "REPORT" ? "reportName" : currentJob.taskType === "NOTIFICATION" ? "message" : "batchSize";
    return String(currentJob.configuration[key] ?? "");
  }
}

function JobTable({ rows, busy, canOperate, isAdmin, onEdit, onToggle, onTrigger, onDelete }: {
  rows: Job[]; busy: boolean; canOperate: boolean; isAdmin: boolean;
  onEdit: (job: Job) => void; onToggle: (job: Job) => void; onTrigger: (job: Job) => void; onDelete: (job: Job) => void;
}) {
  return (
    <div className="table-scroll"><table><thead><tr><th>JOB</th><th>TYPE</th><th>SCHEDULE</th><th>NEXT RUN</th><th>HEALTH</th><th>STATUS</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>
      {rows.map((job) => <tr key={job.id}><td><div className="table-job"><div className={`job-glyph glyph-${job.taskType.toLowerCase()}`}><Settings2 size={15} /></div><div><strong>{job.name}</strong><span>{job.description || `Job #${job.id}`}</span></div></div></td><td><span className="type-label">{job.taskType.replaceAll("_", " ").toLowerCase()}</span></td><td><span className="schedule-label"><Clock3 size={13} />{job.scheduleType === "INTERVAL" ? formatInterval(job.intervalSeconds) : job.cronExpression}</span></td><td className="muted-cell">{formatRelative(job.nextRunAt)}</td><td>{job.consecutiveFailures > 0 ? <span className="failure-count"><AlertTriangle size={13} />{job.consecutiveFailures} failures</span> : <span className="healthy-label"><i />Healthy</span>}</td><td><StatusBadge status={job.enabled ? "RUNNING" : "QUEUED"} label={job.enabled ? "Enabled" : "Paused"} /></td><td><div className="row-actions">{canOperate && <><button className="icon-button" title="Edit" disabled={busy} onClick={() => onEdit(job)}><Settings2 size={15} /></button><button className="icon-button" title={job.enabled ? "Pause" : "Enable"} disabled={busy} onClick={() => onToggle(job)}>{job.enabled ? <ToggleRight size={17} /> : <ToggleLeft size={17} />}</button><button className="icon-button" title="Trigger now" disabled={busy} onClick={() => onTrigger(job)}><Play size={14} /></button>{isAdmin && <button className="icon-button danger-icon" title="Delete" disabled={busy} onClick={() => onDelete(job)}><Trash2 size={14} /></button>}</>}</div></td></tr>)}
      {!rows.length && <tr><td colSpan={7}><EmptyState icon={<Zap size={22} />} title="No jobs yet" text="Create your first scheduled workflow to get started." /></td></tr>}
    </tbody></table></div>
  );
}

function ExecutionTable({ rows, onSelect }: { rows: Execution[]; onSelect: (id: number) => void }) {
  return <div className="table-scroll"><table><thead><tr><th>JOB</th><th>TRIGGER</th><th>STARTED</th><th>DURATION</th><th>ATTEMPTS</th><th>STATUS</th><th /></tr></thead><tbody>
    {rows.map((row) => <tr className="clickable-row" key={row.id} onClick={() => onSelect(row.id)}><td><div className="table-job"><div className="job-glyph glyph-execution"><Activity size={15} /></div><div><strong>{row.jobName}</strong><span>Execution #{row.id}</span></div></div></td><td><span className="trigger-label">{row.triggerType.toLowerCase()}</span></td><td className="muted-cell">{formatDate(row.startedAt ?? row.createdAt)}</td><td className="muted-cell">{formatDuration(row.durationMs)}</td><td>{row.attemptCount}</td><td><StatusBadge status={row.status} /></td><td><ChevronRight size={15} className="table-chevron" /></td></tr>)}
    {!rows.length && <tr><td colSpan={7}><EmptyState icon={<FileClock size={22} />} title="No executions found" text="Job runs will appear here once scheduled or triggered." /></td></tr>}
  </tbody></table></div>;
}

function ExecutionDrawer({ executionId, detail, onClose }: { executionId: number; detail: { execution: Execution; attempts: Attempt[] } | null; onClose: () => void }) {
  return <div className="drawer-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><aside className="drawer"><div className="drawer-header"><div><span className="eyebrow">EXECUTION DETAILS</span><h2>Run #{executionId}</h2></div><button className="icon-button" onClick={onClose}><X size={18} /></button></div>{!detail ? <LoadingScreen label="Loading execution…" /> : <><div className="drawer-summary"><div><span>Job</span><strong>{detail.execution.jobName}</strong></div><div><span>Status</span><StatusBadge status={detail.execution.status} /></div><div><span>Trigger</span><strong>{detail.execution.triggerType.toLowerCase()}</strong></div><div><span>Duration</span><strong>{formatDuration(detail.execution.durationMs)}</strong></div></div>{detail.execution.errorMessage && <div className="failure-message"><AlertTriangle size={16} />{detail.execution.errorMessage}</div>}<div className="attempt-heading"><h3>Attempts</h3><span>{detail.attempts.length} total</span></div><div className="attempt-list">{detail.attempts.map((attempt) => <div className="attempt-item" key={attempt.id}><div className={`attempt-marker marker-${attempt.status.toLowerCase()}`}>{attempt.status === "SUCCESS" ? <Check size={13} /> : attempt.status === "FAILED" ? <X size={13} /> : <LoaderCircle size={13} />}</div><div className="attempt-body"><div className="attempt-title"><strong>Attempt {attempt.attemptNumber}</strong><StatusBadge status={attempt.status} /></div><span>{formatDate(attempt.startedAt)} {attempt.durationMs !== undefined && `· ${formatDuration(attempt.durationMs)}`}</span>{attempt.errorMessage && <p>{attempt.errorMessage}</p>}</div></div>)}{!detail.attempts.length && <EmptyInline text="No attempts have started yet." />}</div></>}</aside></div>;
}

function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}<span className="heading-period">.</span></h1><p>{description}</p></div>{action}</div>;
}

function PanelHeading({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>;
}

function StatCard({ icon, label, value, foot, tone }: { icon: React.ReactNode; label: string; value: string | number; foot: string; tone: string }) {
  return <article className="stat-card"><div className="stat-top"><span>{label}</span><div className={`stat-icon tone-${tone}`}>{icon}</div></div><strong className="stat-value">{value}</strong><span className="stat-foot">{foot}</span></article>;
}

function NavItem({ icon, label, count, active, onClick }: { icon: React.ReactNode; label: string; count?: number; active: boolean; onClick: () => void }) {
  return <button className={`nav-item ${active ? "nav-active" : ""}`} onClick={onClick}>{icon}<span>{label}</span>{count !== undefined && count > 0 && <b>{count}</b>}</button>;
}

function StatusBadge({ status, label }: { status: string; label?: string }) {
  const normalized = status.toLowerCase();
  return <span className={`status-badge badge-${normalized}`}><i />{label ?? status.toLowerCase().replace("_", " ")}</span>;
}

function Pagination({ page, pages, onPage }: { page: number; pages: number; onPage: (page: number) => void }) {
  if (pages <= 1) return null;
  return <div className="pagination"><span>Page {page + 1} of {pages}</span><div><button className="icon-button" disabled={page === 0} onClick={() => onPage(page - 1)}><ChevronLeft size={16} /></button><button className="icon-button" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}><ChevronRight size={16} /></button></div></div>;
}

function EmptyState({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><strong>{title}</strong><p>{text}</p></div>;
}

function EmptyInline({ text }: { text: string }) {
  return <div className="empty-inline">{text}</div>;
}

function BrandMark() {
  return <div className="brand-mark"><div /><div /><div /><div /></div>;
}

function LoadingScreen({ label }: { label: string }) {
  return <div className="loading-screen"><div className="loading-mark"><BrandMark /></div><LoaderCircle className="spin loading-spinner" size={19} /><span>{label}</span></div>;
}

function FailureScreen({ message }: { message: string }) {
  return <div className="loading-screen"><div className="error-symbol"><AlertTriangle size={23} /></div><strong>JobFlow could not start</strong><span>{message}</span><button className="button button-secondary" onClick={() => window.location.reload()}><RefreshCw size={15} /> Retry</button></div>;
}

function sectionTitle(section: string) {
  return ({ overview: "Overview", jobs: "Scheduled jobs", executions: "Executions", alerts: "Alerts" } as Record<string, string>)[section] ?? section;
}

function taskConfigLabel(taskType: Job["taskType"]) {
  return taskType === "REPORT" ? "Report name" : taskType === "NOTIFICATION" ? "Notification message" : "Batch size";
}

function formatInterval(seconds?: number) {
  if (!seconds) return "—";
  if (seconds % 86400 === 0) return `Every ${seconds / 86400}d`;
  if (seconds % 3600 === 0) return `Every ${seconds / 3600}h`;
  if (seconds % 60 === 0) return `Every ${seconds / 60}m`;
  return `Every ${seconds}s`;
}

function formatDate(value?: string) {
  if (!value) return "—";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatRelative(value?: string) {
  if (!value) return "—";
  const delta = new Date(value).getTime() - Date.now();
  const absSeconds = Math.round(Math.abs(delta) / 1000);
  if (absSeconds < 60) return delta > 0 ? "in <1m" : "just now";
  const minutes = Math.round(absSeconds / 60);
  if (minutes < 60) return delta > 0 ? `in ${minutes}m` : `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return delta > 0 ? `in ${hours}h` : `${hours}h ago`;
  return formatDate(value);
}

function formatDuration(value?: number) {
  if (value === undefined || value === null) return "—";
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(2)} s`;
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode><Boot /></React.StrictMode>,
);
