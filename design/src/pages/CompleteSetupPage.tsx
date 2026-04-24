import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiJson, ApiError } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import type { AuthUser, SubscriptionInfo } from "../context/AuthContext";

export function CompleteSetupPage() {
  const [params] = useSearchParams();
  const sessionId = params.get("session_id");
  const tokenParam = params.get("token");
  const navigate = useNavigate();
  const { refresh } = useAuth();

  const [setupToken, setSetupToken] = useState(tokenParam ?? "");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loadingSession, setLoadingSession] = useState(!!sessionId);
  const [checkoutLicense, setCheckoutLicense] = useState<string | null>(null);

  useEffect(() => {
    if (!sessionId) {
      setLoadingSession(false);
      return;
    }
    let cancelled = false;
    (async () => {
      setErr(null);
      try {
        const data = await apiJson<{
          setupToken: string;
          email: string;
          needsUsername: boolean;
          needsPassword: boolean;
          licenseKey?: string;
        }>(`/purchase/session?session_id=${encodeURIComponent(sessionId)}`);
        if (cancelled) return;
        setSetupToken(data.setupToken);
        setEmail(data.email);
        if (data.licenseKey) setCheckoutLicense(data.licenseKey);
      } catch (ex) {
        if (!cancelled) setErr(ex instanceof ApiError ? ex.message : "Could not load purchase");
      } finally {
        if (!cancelled) setLoadingSession(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!setupToken.trim()) {
      setErr("Missing setup token. Open the link from checkout or paste your token.");
      return;
    }
    setErr(null);
    setBusy(true);
    try {
      await apiJson<{ user: AuthUser; subscription: SubscriptionInfo | null }>("/purchase/complete-setup", {
        method: "POST",
        body: JSON.stringify({ token: setupToken.trim(), username, password }),
      });
      await refresh();
      navigate("/dashboard", { replace: true });
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Setup failed");
    } finally {
      setBusy(false);
    }
  };

  if (loadingSession) {
    return (
      <main className="mao-main mao-auth">
        <div className="mao-container">
          <div className="mao-auth__panel">
            <p className="mao-auth__lead">Confirming your purchase…</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="mao-main mao-auth">
      <div className="mao-container">
        <div className="mao-auth__panel">
          <h1 className="mao-auth__title">Finish your account</h1>
          <p className="mao-auth__lead">
            {email
              ? `We created an account for ${email}. Choose the username and password you want to use.`
              : "Set a username and password for the account linked to your purchase."}
          </p>
          {err ? <p className="mao-auth__error">{err}</p> : null}
          {checkoutLicense ? (
            <p className="mao-auth__error" style={{ marginBottom: "1rem", wordBreak: "break-all" }}>
              <strong>Your Mao External license (copy now):</strong> {checkoutLicense}
            </p>
          ) : null}
          <form onSubmit={onSubmit}>
            {!sessionId ? (
              <div className="mao-auth__field">
                <label htmlFor="setup-token">Setup token</label>
                <input
                  id="setup-token"
                  value={setupToken}
                  onChange={(e) => setSetupToken(e.target.value)}
                  placeholder="From checkout redirect or email"
                  autoComplete="off"
                />
              </div>
            ) : null}
            <div className="mao-auth__field">
              <label htmlFor="setup-user">Username</label>
              <input
                id="setup-user"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                minLength={2}
                maxLength={32}
                pattern="^[a-zA-Z0-9_]+$"
                title="Letters, numbers, and underscores only"
              />
            </div>
            <div className="mao-auth__field">
              <label htmlFor="setup-pass">Password</label>
              <input
                id="setup-pass"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                autoComplete="new-password"
              />
            </div>
            <div className="mao-auth__actions">
              <button type="submit" className="mao-btn mao-btn--solid" disabled={busy}>
                {busy ? "Saving…" : "Save and continue"}
              </button>
              <Link className="mao-btn mao-btn--outline" to="/login">
                Already set up? Sign in
              </Link>
            </div>
          </form>
        </div>
      </div>
    </main>
  );
}
