import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ApiError } from "../lib/api";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      const { trialLicenseKey } = await register({
        email,
        username,
        password,
        inviteCode: inviteCode.trim() || undefined,
      });
      if (trialLicenseKey) {
        sessionStorage.setItem("mao_show_license_once", trialLicenseKey);
      }
      navigate("/dashboard", { replace: true });
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="mao-main mao-auth">
      <div className="mao-container">
        <div className="mao-auth__panel">
          <h1 className="mao-auth__title">Create account</h1>
          <p className="mao-auth__lead">
            Email, username, and password. Add an invite code for a two-week Mao External trial — optional.
          </p>
          {err ? <p className="mao-auth__error">{err}</p> : null}
          <form onSubmit={onSubmit}>
            <div className="mao-auth__field">
              <label htmlFor="reg-email">Email</label>
              <input
                id="reg-email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="mao-auth__field">
              <label htmlFor="reg-user">Username</label>
              <input
                id="reg-user"
                name="username"
                autoComplete="username"
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
              <label htmlFor="reg-pass">Password</label>
              <input
                id="reg-pass"
                name="password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </div>
            <div className="mao-auth__field">
              <label htmlFor="reg-invite">Invite code (optional)</label>
              <input
                id="reg-invite"
                name="inviteCode"
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                placeholder="14-day External trial if valid"
                autoComplete="off"
              />
            </div>
            <div className="mao-auth__actions">
              <button type="submit" className="mao-btn mao-btn--solid" disabled={busy}>
                {busy ? "Creating…" : "Create account"}
              </button>
              <a className="mao-btn mao-btn--outline" href="/api/auth/discord">
                Sign up with Discord
              </a>
            </div>
          </form>
          <p className="mao-auth__hint">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
