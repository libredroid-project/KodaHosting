import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiJson, ApiError } from "../lib/api";

function fmt(ts: number) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(ts));
}

export function DashboardPage() {
  const { user, subscription, refresh } = useAuth();
  const [promo, setPromo] = useState("");
  const [promoMsg, setPromoMsg] = useState<string | null>(null);
  const [promoBusy, setPromoBusy] = useState(false);

  const [uName, setUName] = useState("");
  const [curPass, setCurPass] = useState("");
  const [newPass, setNewPass] = useState("");
  const [profMsg, setProfMsg] = useState<string | null>(null);
  const [profBusy, setProfBusy] = useState(false);

  const [dlBusy, setDlBusy] = useState(false);
  const [dlErr, setDlErr] = useState<string | null>(null);

  const [licenses, setLicenses] = useState<
    { publicId: string; id: number; expiresAt: number; status: string; maxActivations: number; createdFrom: string; boundDevices: number }[]
  >([]);
  const [licRedeem, setLicRedeem] = useState("");
  const [licMsg, setLicMsg] = useState<string | null>(null);
  const [licBusy, setLicBusy] = useState(false);
  const [flashLicense, setFlashLicense] = useState<string | null>(null);

  useEffect(() => {
    if (user) setUName(user.username);
  }, [user]);

  useEffect(() => {
    const once = sessionStorage.getItem("mao_show_license_once");
    if (once) {
      sessionStorage.removeItem("mao_show_license_once");
      setFlashLicense(once);
    }
  }, []);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await apiJson<{
          keys: {
            publicId: string;
            id: number;
            expiresAt: number;
            status: string;
            maxActivations: number;
            createdFrom: string;
            boundDevices: number;
          }[];
        }>("/license/mine");
        if (!cancelled) setLicenses(data.keys);
      } catch {
        if (!cancelled) setLicenses([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [user, subscription]);

  if (!user) return null;

  const active = !!subscription && subscription.endsAt > Date.now();

  const redeemLicense = async (e: React.FormEvent) => {
    e.preventDefault();
    setLicMsg(null);
    setLicBusy(true);
    try {
      const data = await apiJson<{ licenseKey: string }>("/license/redeem", {
        method: "POST",
        body: JSON.stringify({ code: licRedeem }),
      });
      setLicRedeem("");
      setLicMsg(`Save this license key now (shown once): ${data.licenseKey}`);
      sessionStorage.setItem("mao_show_license_once", data.licenseKey);
      const mine = await apiJson<{
        keys: {
          publicId: string;
          id: number;
          expiresAt: number;
          status: string;
          maxActivations: number;
          createdFrom: string;
          boundDevices: number;
        }[];
      }>("/license/mine");
      setLicenses(mine.keys);
      await refresh();
    } catch (ex) {
      setLicMsg(ex instanceof ApiError ? ex.message : "Could not redeem");
    } finally {
      setLicBusy(false);
    }
  };

  const redeem = async (e: React.FormEvent) => {
    e.preventDefault();
    setPromoMsg(null);
    setPromoBusy(true);
    try {
      await apiJson("/promo/redeem", { method: "POST", body: JSON.stringify({ code: promo }) });
      setPromo("");
      setPromoMsg("Code applied.");
      await refresh();
    } catch (ex) {
      setPromoMsg(ex instanceof ApiError ? ex.message : "Could not redeem");
    } finally {
      setPromoBusy(false);
    }
  };

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfMsg(null);
    setProfBusy(true);
    try {
      const body: Record<string, string> = {};
      if (uName.trim() && uName.trim() !== user.username) body.username = uName.trim();
      if (newPass) {
        body.password = newPass;
        if (curPass) body.currentPassword = curPass;
      }
      if (Object.keys(body).length === 0) {
        setProfMsg("Change username or password to save.");
        setProfBusy(false);
        return;
      }
      await apiJson<{ user: typeof user }>("/account/profile", { method: "PATCH", body: JSON.stringify(body) });
      setNewPass("");
      setCurPass("");
      setProfMsg("Profile updated.");
      await refresh();
    } catch (ex) {
      setProfMsg(ex instanceof ApiError ? ex.message : "Update failed");
    } finally {
      setProfBusy(false);
    }
  };

  const uploadAvatar = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setProfMsg(null);
    const fd = new FormData();
    fd.set("file", file);
    try {
      await apiJson("/account/avatar", { method: "POST", body: fd });
      setProfMsg("Avatar updated.");
      await refresh();
    } catch (ex) {
      setProfMsg(ex instanceof ApiError ? ex.message : "Upload failed");
    }
  };

  const download = async () => {
    setDlErr(null);
    setDlBusy(true);
    try {
      const { url } = await apiJson<{ url: string }>("/external/download");
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (ex) {
      setDlErr(ex instanceof ApiError ? ex.message : "Download unavailable");
    } finally {
      setDlBusy(false);
    }
  };

  return (
    <main className="mao-main mao-dashboard">
      <div className="mao-container">
        <p className="mao-kicker">Account</p>
        <h1 className="mao-checkout__title" style={{ marginBottom: "0.5rem" }}>
          Dashboard
        </h1>
        <p className="mao-lead" style={{ marginBottom: "2rem", maxWidth: "52ch" }}>
          Download Mao External when your subscription is active, redeem promo codes, manage license keys for the
          desktop client, and edit your profile.
        </p>

        {flashLicense ? (
          <p className="mao-auth__error" style={{ maxWidth: "56ch", marginBottom: "1.25rem", wordBreak: "break-all" }}>
            <strong>Your Mao External license (save now — not stored in plain text on the server):</strong> {flashLicense}
          </p>
        ) : null}

        {user.needs_password_setup ? (
          <p className="mao-auth__error" style={{ maxWidth: "52ch", marginBottom: "1.5rem" }}>
            Set a password below so you can sign in without Discord.
          </p>
        ) : null}

        <div className="mao-dashboard__grid">
          <div className="mao-dash-card">
            <h2>Mao External</h2>
            {active ? (
              <>
                <p>
                  Active until <strong style={{ color: "var(--milky-soft)" }}>{fmt(subscription!.endsAt)}</strong>
                </p>
                <p>Plan: {subscription!.plan}</p>
                {dlErr ? <p className="mao-auth__error">{dlErr}</p> : null}
                <div className="mao-auth__actions" style={{ marginTop: "1rem" }}>
                  <button type="button" className="mao-btn mao-btn--solid" disabled={dlBusy} onClick={() => void download()}>
                    {dlBusy ? "Preparing…" : "Download"}
                  </button>
                </div>
              </>
            ) : (
              <p>No active subscription. <Link to="/checkout">View checkout</Link> or redeem a code.</p>
            )}
          </div>

          <div className="mao-dash-card">
            <h2>License keys</h2>
            <p>Keys for Mao External (HWID bind via desktop). Only hashes are stored; copy new keys immediately.</p>
            <ul style={{ margin: "0.5rem 0 1rem", paddingLeft: "1.1rem", color: "var(--muted)", fontSize: "0.88rem" }}>
              {licenses.length === 0 ? <li>No keys yet</li> : null}
              {licenses.map((k) => (
                <li key={k.id}>
                  {k.publicId} · {k.status} · ends {fmt(k.expiresAt)} · devices {k.boundDevices}/{k.maxActivations} ·{" "}
                  {k.createdFrom}
                </li>
              ))}
            </ul>
            <h2 style={{ marginTop: "1rem" }}>Redeem license code</h2>
            <p>Opaque <code>REDEEM-…</code> codes mint a key (server-side, spec §4).</p>
            <form onSubmit={redeemLicense}>
              <div className="mao-auth__field">
                <label htmlFor="lic-redeem">Redemption code</label>
                <input id="lic-redeem" value={licRedeem} onChange={(e) => setLicRedeem(e.target.value)} autoComplete="off" />
              </div>
              {licMsg ? <p className="mao-checkout__hint">{licMsg}</p> : null}
              <button type="submit" className="mao-btn mao-btn--solid" disabled={licBusy}>
                {licBusy ? "Redeeming…" : "Redeem for license key"}
              </button>
            </form>
          </div>

          <div className="mao-dash-card">
            <h2>Redeem code</h2>
            <p>Promo codes extend or start access (admin-issued).</p>
            <form onSubmit={redeem}>
              <div className="mao-auth__field">
                <label htmlFor="promo">Code</label>
                <input id="promo" value={promo} onChange={(e) => setPromo(e.target.value)} autoComplete="off" />
              </div>
              {promoMsg ? <p className="mao-checkout__hint">{promoMsg}</p> : null}
              <button type="submit" className="mao-btn mao-btn--solid" disabled={promoBusy}>
                {promoBusy ? "Applying…" : "Redeem"}
              </button>
            </form>
          </div>

          <div className="mao-dash-card">
            <h2>Subscription</h2>
            {subscription ? (
              <>
                <p>Status: {subscription.status}</p>
                <p>Plan: {subscription.plan}</p>
                <p>Ends: {fmt(subscription.endsAt)}</p>
              </>
            ) : (
              <p>No subscription on file.</p>
            )}
          </div>

          <div className="mao-dash-card">
            <h2>Profile</h2>
            {user.avatar_url ? <img className="mao-pfp" src={user.avatar_url} alt="" width={72} height={72} /> : null}
            <div className="mao-auth__field">
              <label htmlFor="avatar">Profile picture</label>
              <input id="avatar" type="file" accept="image/png,image/jpeg,image/webp,image/gif" onChange={uploadAvatar} />
            </div>
            <form onSubmit={saveProfile}>
              <div className="mao-auth__field">
                <label htmlFor="dash-user">Username</label>
                <input id="dash-user" value={uName} onChange={(e) => setUName(e.target.value)} />
              </div>
              <div className="mao-auth__field">
                <label htmlFor="dash-cur">Current password (if changing password)</label>
                <input id="dash-cur" type="password" value={curPass} onChange={(e) => setCurPass(e.target.value)} autoComplete="current-password" />
              </div>
              <div className="mao-auth__field">
                <label htmlFor="dash-new">New password</label>
                <input id="dash-new" type="password" value={newPass} onChange={(e) => setNewPass(e.target.value)} autoComplete="new-password" />
              </div>
              {profMsg ? <p className="mao-checkout__hint">{profMsg}</p> : null}
              <button type="submit" className="mao-btn mao-btn--solid" disabled={profBusy}>
                {profBusy ? "Saving…" : "Save profile"}
              </button>
            </form>
          </div>
        </div>

        {user.is_admin ? (
          <p style={{ marginTop: "2rem" }}>
            <Link className="mao-btn mao-btn--outline" to="/admin">
              Admin dashboard
            </Link>
          </p>
        ) : null}
      </div>
    </main>
  );
}
