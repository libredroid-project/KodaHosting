import type { ReactElement } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function RequireAuth({ children }: { children: ReactElement }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <main className="mao-main mao-auth">
        <div className="mao-container">
          <p className="mao-auth__lead">Loading…</p>
        </div>
      </main>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export function RequireAdmin({ children }: { children: ReactElement }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <main className="mao-main mao-auth">
        <div className="mao-container">
          <p className="mao-auth__lead">Loading…</p>
        </div>
      </main>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  if (!user.is_admin) return <Navigate to="/dashboard" replace />;
  return children;
}
