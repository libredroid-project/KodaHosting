import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiJson, ApiError } from "../lib/api";

type InviteRow = {
  id: number;
  code: string;
  trial_days: number;
  max_uses: number;
  uses: number;
  expires_at: number | null;
  active: number;
  created_at: number;
};

type PromoRow = {
  id: number;
  code: string;
  grant_days: number;
  max_uses: number;
  uses: number;
  expires_at: number | null;
  active: number;
  created_at: number;
};

type SubRow = {
  id: number;
  user_id: number;
  plan: string;
  status: string;
  starts_at: number;
  ends_at: number;
  stripe_subscription_id: string | null;
  checkout_session_id: string | null;
  created_at: number;
  deactivated_at: number | null;
};

function fmt(ts: number) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "short", timeStyle: "short" }).format(new Date(ts));
}

type RedemptionRow = {
  id: number;
  max_redemptions: number;
  redemptions_used: number;
  expires_at: number | null;
  active: number;
  grant_days: number;
  created_at: number;
};

type LicenseKeyAdminRow = Record<string, unknown>;

type AuditRow = {
  id: number;
  admin_user_id: number;
  action: string;
  target_type: string | null;
  target_id: number | null;
  payload_json: string | null;
  created_at: number;
};

export function AdminPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<"subs" | "invites" | "promos" | "redemption" | "licenses" | "audit">("subs");
  const [err, setErr] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [subs, setSubs] = useState<SubRow[]>([]);
  const [subQ, setSubQ] = useState("");
  const [subEmail, setSubEmail] = useState("");
  const [subPlan, setSubPlan] = useState("external_monthly");
  const [subDays, setSubDays] = useState(30);

  const [invites, setInvites] = useState<InviteRow[]>([]);
  const [trialDays, setTrialDays] = useState(14);
  const [inviteMax, setInviteMax] = useState(1);

  const [promos, setPromos] = useState<PromoRow[]>([]);
  const [grantDays, setGrantDays] = useState(30);
  const [promoMax, setPromoMax] = useState(100);

  const [redemptions, setRedemptions] = useState<RedemptionRow[]>([]);
  const [redGrantDays, setRedGrantDays] = useState(30);
  const [redMax, setRedMax] = useState(1);

  const [licRows, setLicRows] = useState<LicenseKeyAdminRow[]>([]);
  const [licQ, setLicQ] = useState("");
  const [mintEmail, setMintEmail] = useState("");
  const [mintDays, setMintDays] = useState(30);
  const [mintMaxAct, setMintMaxAct] = useState(1);

  const [audit, setAudit] = useState<AuditRow[]>([]);

  const loadSubs = useCallback(async () => {
    const q = subQ.trim();
    const path = q ? `/admin/subscriptions?q=${encodeURIComponent(q)}` : "/admin/subscriptions";
    const data = await apiJson<{ subscriptions: SubRow[] }>(path);
    setSubs(data.subscriptions);
  }, [subQ]);

  const loadInvites = useCallback(async () => {
    const data = await apiJson<{ invites: InviteRow[] }>("/admin/invites");
    setInvites(data.invites);
  }, []);

  const loadPromos = useCallback(async () => {
    const data = await apiJson<{ promos: PromoRow[] }>("/admin/promos");
    setPromos(data.promos);
  }, []);

  const loadRedemptions = useCallback(async () => {
    const data = await apiJson<{ redemptionCodes: RedemptionRow[] }>("/admin/redemption-codes");
    setRedemptions(data.redemptionCodes);
  }, []);

  const loadLicenses = useCallback(async () => {
    const q = licQ.trim();
    const path = q ? `/admin/license-keys?q=${encodeURIComponent(q)}` : "/admin/license-keys";
    const data = await apiJson<{ licenseKeys: LicenseKeyAdminRow[] }>(path);
    setLicRows(data.licenseKeys);
  }, [licQ]);

  const loadAudit = useCallback(async () => {
    const data = await apiJson<{ entries: AuditRow[] }>("/admin/audit-log");
    setAudit(data.entries);
  }, []);

  useEffect(() => {
    if (!user?.is_admin) return;
    setErr(null);
    (async () => {
      try {
        if (tab === "subs") await loadSubs();
        if (tab === "invites") await loadInvites();
        if (tab === "promos") await loadPromos();
        if (tab === "redemption") await loadRedemptions();
        if (tab === "licenses") await loadLicenses();
        if (tab === "audit") await loadAudit();
      } catch (ex) {
        setErr(ex instanceof ApiError ? ex.message : "Load failed");
      }
    })();
  }, [user?.is_admin, tab, loadSubs, loadInvites, loadPromos, loadRedemptions, loadLicenses, loadAudit]);

  const createSub = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setNotice(null);
    try {
      const data = await apiJson<{ ok: boolean; licenseKey?: string }>("/admin/subscriptions", {
        method: "POST",
        body: JSON.stringify({ email: subEmail.trim(), plan: subPlan, days: subDays }),
      });
      setSubEmail("");
      setNotice(
        data.licenseKey
          ? `Subscription created. One-time license key (save now): ${data.licenseKey}`
          : "Subscription created."
      );
      await loadSubs();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Create failed");
    }
  };

  const deactivateSub = async (id: number) => {
    setErr(null);
    try {
      await apiJson(`/admin/subscriptions/${id}`, { method: "DELETE" });
      await loadSubs();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Update failed");
    }
  };

  const patchSub = async (id: number, patch: Record<string, unknown>) => {
    setErr(null);
    try {
      await apiJson(`/admin/subscriptions/${id}`, { method: "PATCH", body: JSON.stringify(patch) });
      await loadSubs();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Update failed");
    }
  };

  const genInvite = async () => {
    setErr(null);
    try {
      await apiJson("/admin/invites", {
        method: "POST",
        body: JSON.stringify({ trialDays, maxUses: inviteMax }),
      });
      await loadInvites();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Failed");
    }
  };

  const toggleInvite = async (id: number, active: boolean) => {
    await apiJson(`/admin/invites/${id}`, { method: "PATCH", body: JSON.stringify({ active }) });
    await loadInvites();
  };

  const deleteInvite = async (id: number) => {
    await apiJson(`/admin/invites/${id}`, { method: "DELETE" });
    await loadInvites();
  };

  const genPromo = async () => {
    setErr(null);
    try {
      await apiJson("/admin/promos", {
        method: "POST",
        body: JSON.stringify({ grantDays, maxUses: promoMax }),
      });
      await loadPromos();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Failed");
    }
  };

  const togglePromo = async (id: number, active: boolean) => {
    await apiJson(`/admin/promos/${id}`, { method: "PATCH", body: JSON.stringify({ active }) });
    await loadPromos();
  };

  const deletePromo = async (id: number) => {
    await apiJson(`/admin/promos/${id}`, { method: "DELETE" });
    await loadPromos();
  };

  const genRedemption = async () => {
    setErr(null);
    setNotice(null);
    try {
      const data = await apiJson<{ code: string }>("/admin/redemption-codes", {
        method: "POST",
        body: JSON.stringify({ grantDays: redGrantDays, maxRedemptions: redMax }),
      });
      setNotice(`New redemption code (save now): ${data.code}`);
      await loadRedemptions();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Failed");
    }
  };

  const toggleRedemption = async (id: number, active: boolean) => {
    await apiJson(`/admin/redemption-codes/${id}`, { method: "PATCH", body: JSON.stringify({ active }) });
    await loadRedemptions();
  };

  const deleteRedemption = async (id: number) => {
    await apiJson(`/admin/redemption-codes/${id}`, { method: "DELETE" });
    await loadRedemptions();
  };

  const mintDirectLicense = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setNotice(null);
    try {
      const data = await apiJson<{ licenseKey: string }>("/admin/license-keys", {
        method: "POST",
        body: JSON.stringify({ email: mintEmail.trim(), days: mintDays, maxActivations: mintMaxAct }),
      });
      setMintEmail("");
      setNotice(`License minted (save now): ${data.licenseKey}`);
      await loadLicenses();
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Mint failed");
    }
  };

  const revokeLicense = async (id: number) => {
    setErr(null);
    await apiJson(`/admin/license-keys/${id}`, { method: "PATCH", body: JSON.stringify({ status: "revoked" }) });
    await loadLicenses();
  };

  const resetLicenseHwid = async (id: number) => {
    setErr(null);
    await apiJson(`/admin/license-keys/${id}/hwids`, { method: "DELETE" });
    await loadLicenses();
  };

  if (!user?.is_admin) return null;

  return (
    <main className="mao-main mao-dashboard">
      <div className="mao-container">
        <p className="mao-kicker">Admin</p>
        <h1 className="mao-checkout__title" style={{ marginBottom: "0.5rem" }}>
          Operations
        </h1>
        <p style={{ marginBottom: "1.5rem" }}>
          <Link to="/dashboard">← Back to account</Link>
        </p>
        {err ? <p className="mao-auth__error">{err}</p> : null}
        {notice ? (
          <p className="mao-checkout__hint" style={{ marginBottom: "1rem", wordBreak: "break-all" }}>
            {notice}
          </p>
        ) : null}

        <div className="mao-auth__actions" style={{ marginBottom: "1.5rem", flexWrap: "wrap" }}>
          <button type="button" className={`mao-btn ${tab === "subs" ? "mao-btn--solid" : "mao-btn--outline"}`} onClick={() => setTab("subs")}>
            Subscriptions
          </button>
          <button
            type="button"
            className={`mao-btn ${tab === "invites" ? "mao-btn--solid" : "mao-btn--outline"}`}
            onClick={() => setTab("invites")}
          >
            Invite codes
          </button>
          <button
            type="button"
            className={`mao-btn ${tab === "promos" ? "mao-btn--solid" : "mao-btn--outline"}`}
            onClick={() => setTab("promos")}
          >
            Promo codes
          </button>
          <button
            type="button"
            className={`mao-btn ${tab === "redemption" ? "mao-btn--solid" : "mao-btn--outline"}`}
            onClick={() => setTab("redemption")}
          >
            License redemption
          </button>
          <button
            type="button"
            className={`mao-btn ${tab === "licenses" ? "mao-btn--solid" : "mao-btn--outline"}`}
            onClick={() => setTab("licenses")}
          >
            License keys
          </button>
          <button
            type="button"
            className={`mao-btn ${tab === "audit" ? "mao-btn--solid" : "mao-btn--outline"}`}
            onClick={() => setTab("audit")}
          >
            Audit log
          </button>
        </div>

        {tab === "subs" ? (
          <div className="mao-dash-card" style={{ marginBottom: "1.25rem" }}>
            <h2>Create / search</h2>
            <form onSubmit={createSub} style={{ marginBottom: "1rem" }}>
              <div className="mao-auth__field">
                <label>User email</label>
                <input value={subEmail} onChange={(e) => setSubEmail(e.target.value)} type="email" required />
              </div>
              <div className="mao-auth__field">
                <label>Plan id</label>
                <input value={subPlan} onChange={(e) => setSubPlan(e.target.value)} placeholder="external_monthly" />
              </div>
              <div className="mao-auth__field">
                <label>Duration (days)</label>
                <input type="number" min={1} value={subDays} onChange={(e) => setSubDays(Number(e.target.value))} />
              </div>
              <button type="submit" className="mao-btn mao-btn--solid">
                Grant subscription
              </button>
            </form>
            <div className="mao-auth__field">
              <label>Search</label>
              <input value={subQ} onChange={(e) => setSubQ(e.target.value)} placeholder="email, username, or user id" />
            </div>
            <button type="button" className="mao-btn mao-btn--outline" onClick={() => void loadSubs()}>
              Search
            </button>
            <table className="mao-admin-table" style={{ marginTop: "1rem" }}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>User</th>
                  <th>Plan</th>
                  <th>Ends</th>
                  <th>Active</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {subs.map((s) => (
                  <tr key={s.id}>
                    <td>{s.id}</td>
                    <td>{s.user_id}</td>
                    <td>{s.plan}</td>
                    <td>{fmt(s.ends_at)}</td>
                    <td>{s.deactivated_at ? "no" : "yes"}</td>
                    <td>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void deactivateSub(s.id)}>
                        Deactivate
                      </button>
                      <button
                        type="button"
                        className="mao-btn mao-btn--outline"
                        style={{ marginLeft: "0.35rem" }}
                        onClick={() => {
                          const ends = prompt("New end timestamp (ms since epoch)", String(s.ends_at));
                          if (ends) void patchSub(s.id, { endsAt: Number(ends) });
                        }}
                      >
                        Edit end
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === "invites" ? (
          <div className="mao-dash-card">
            <h2>Generate invite (trial)</h2>
            <div className="mao-auth__field">
              <label>Trial days</label>
              <input type="number" min={1} value={trialDays} onChange={(e) => setTrialDays(Number(e.target.value))} />
            </div>
            <div className="mao-auth__field">
              <label>Max uses</label>
              <input type="number" min={1} value={inviteMax} onChange={(e) => setInviteMax(Number(e.target.value))} />
            </div>
            <button type="button" className="mao-btn mao-btn--solid" onClick={() => void genInvite()}>
              Generate code
            </button>
            <table className="mao-admin-table" style={{ marginTop: "1rem" }}>
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Trial</th>
                  <th>Uses</th>
                  <th>Active</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {invites.map((i) => (
                  <tr key={i.id}>
                    <td>
                      <code>{i.code}</code>
                    </td>
                    <td>{i.trial_days}d</td>
                    <td>
                      {i.uses}/{i.max_uses}
                    </td>
                    <td>{i.active ? "yes" : "no"}</td>
                    <td>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void toggleInvite(i.id, !i.active)}>
                        Toggle
                      </button>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void deleteInvite(i.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === "promos" ? (
          <div className="mao-dash-card">
            <h2>Generate promo (redeem on dashboard)</h2>
            <div className="mao-auth__field">
              <label>Grant days</label>
              <input type="number" min={1} value={grantDays} onChange={(e) => setGrantDays(Number(e.target.value))} />
            </div>
            <div className="mao-auth__field">
              <label>Max uses</label>
              <input type="number" min={1} value={promoMax} onChange={(e) => setPromoMax(Number(e.target.value))} />
            </div>
            <button type="button" className="mao-btn mao-btn--solid" onClick={() => void genPromo()}>
              Generate promo
            </button>
            <table className="mao-admin-table" style={{ marginTop: "1rem" }}>
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Days</th>
                  <th>Uses</th>
                  <th>Active</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {promos.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <code>{p.code}</code>
                    </td>
                    <td>{p.grant_days}</td>
                    <td>
                      {p.uses}/{p.max_uses}
                    </td>
                    <td>{p.active ? "yes" : "no"}</td>
                    <td>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void togglePromo(p.id, !p.active)}>
                        Toggle
                      </button>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void deletePromo(p.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === "redemption" ? (
          <div className="mao-dash-card">
            <h2>Redemption codes (opaque REDEEM-…)</h2>
            <p>Stored as hash only; plaintext shown once when generated.</p>
            <div className="mao-auth__field">
              <label>Grant days (license length)</label>
              <input type="number" min={1} value={redGrantDays} onChange={(e) => setRedGrantDays(Number(e.target.value))} />
            </div>
            <div className="mao-auth__field">
              <label>Max redemptions</label>
              <input type="number" min={1} value={redMax} onChange={(e) => setRedMax(Number(e.target.value))} />
            </div>
            <button type="button" className="mao-btn mao-btn--solid" onClick={() => void genRedemption()}>
              Generate code
            </button>
            <table className="mao-admin-table" style={{ marginTop: "1rem" }}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Uses</th>
                  <th>Days</th>
                  <th>Active</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {redemptions.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>
                      {r.redemptions_used}/{r.max_redemptions}
                    </td>
                    <td>{r.grant_days}</td>
                    <td>{r.active ? "yes" : "no"}</td>
                    <td>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void toggleRedemption(r.id, !r.active)}>
                        Toggle
                      </button>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void deleteRedemption(r.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === "licenses" ? (
          <div className="mao-dash-card">
            <h2>Mint license (no subscription row)</h2>
            <form onSubmit={mintDirectLicense} style={{ marginBottom: "1rem" }}>
              <div className="mao-auth__field">
                <label>User email</label>
                <input type="email" value={mintEmail} onChange={(e) => setMintEmail(e.target.value)} required />
              </div>
              <div className="mao-auth__field">
                <label>Valid days</label>
                <input type="number" min={1} value={mintDays} onChange={(e) => setMintDays(Number(e.target.value))} />
              </div>
              <div className="mao-auth__field">
                <label>Max HWID activations</label>
                <input type="number" min={1} max={10} value={mintMaxAct} onChange={(e) => setMintMaxAct(Number(e.target.value))} />
              </div>
              <button type="submit" className="mao-btn mao-btn--solid">
                Mint license key
              </button>
            </form>
            <div className="mao-auth__field">
              <label>Search</label>
              <input value={licQ} onChange={(e) => setLicQ(e.target.value)} placeholder="user id, email, license id" />
            </div>
            <button type="button" className="mao-btn mao-btn--outline" onClick={() => void loadLicenses()}>
              Search
            </button>
            <table className="mao-admin-table" style={{ marginTop: "1rem" }}>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>User</th>
                  <th>Status</th>
                  <th>Ends</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {licRows.map((k) => (
                  <tr key={Number(k.id)}>
                    <td>{String(k.id)}</td>
                    <td>{String(k.user_email ?? k.user_id ?? "—")}</td>
                    <td>{String(k.status)}</td>
                    <td>{fmt(Number(k.expires_at))}</td>
                    <td>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void revokeLicense(Number(k.id))}>
                        Revoke
                      </button>
                      <button type="button" className="mao-btn mao-btn--outline" onClick={() => void resetLicenseHwid(Number(k.id))}>
                        Reset HWID
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === "audit" ? (
          <div className="mao-dash-card">
            <h2>Admin audit log</h2>
            <table className="mao-admin-table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Admin</th>
                  <th>Action</th>
                  <th>Target</th>
                </tr>
              </thead>
              <tbody>
                {audit.map((a) => (
                  <tr key={a.id}>
                    <td>{fmt(a.created_at)}</td>
                    <td>{a.admin_user_id}</td>
                    <td>{a.action}</td>
                    <td>
                      {a.target_type ?? "—"} {a.target_id ?? ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </div>
    </main>
  );
}
