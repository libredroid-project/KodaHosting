import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ApiError } from "../lib/api";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setBusy(true);
    try {
      await login(username, password);
      navigate("/dashboard", { replace: true });
    } catch (ex) {
      setErr(ex instanceof ApiError ? ex.message : "Could not sign in");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="mao-main mao-auth">
      <div className="mao-container">
        <div className="mao-auth__panel">
          <h1 className="mao-auth__title">Sign in</h1>
          <p className="mao-auth__lead">Use your Mao account username and password.</p>
          {err ? <p className="mao-auth__error">{err}</p> : null}
          <form onSubmit={onSubmit}>
            <div className="mao-auth__field">
              <label htmlFor="login-user">Username</label>
              <input
                id="login-user"
                name="username"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="mao-auth__field">
              <label htmlFor="login-pass">Password</label>
              <input
                id="login-pass"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="mao-auth__actions">
              <button type="submit" className="mao-btn mao-btn--solid" disabled={busy}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
              <a className="mao-btn mao-btn--outline" href="/api/auth/discord">
                Continue with Discord
              </a>
            </div>
          </form>
          <p className="mao-auth__hint">
            No account? <Link to="/register">Create one</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
