import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { apiJson } from "../lib/api";

export type AuthUser = {
  id: number;
  email: string;
  username: string;
  is_admin: boolean;
  needs_password_setup: boolean;
  has_discord: boolean;
  avatar_url: string | null;
};

export type SubscriptionInfo = {
  id: number;
  plan: string;
  status: string;
  startsAt: number;
  endsAt: number;
  stripeSubscriptionId: string | null;
};

type MeResponse = {
  user: AuthUser | null;
  subscription: SubscriptionInfo | null;
};

type RegisterResponse = MeResponse & { trialLicenseKey?: string };

type AuthContextValue = {
  user: AuthUser | null;
  subscription: SubscriptionInfo | null;
  loading: boolean;
  refresh: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  register: (input: {
    email: string;
    username: string;
    password: string;
    inviteCode?: string;
  }) => Promise<{ trialLicenseKey?: string }>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionInfo | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiJson<MeResponse>("/auth/me");
      setUser(data.user);
      setSubscription(data.subscription);
    } catch {
      setUser(null);
      setSubscription(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(async (username: string, password: string) => {
    const data = await apiJson<MeResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
    setUser(data.user as AuthUser);
    setSubscription(data.subscription);
  }, []);

  const register = useCallback(
    async (input: { email: string; username: string; password: string; inviteCode?: string }) => {
      const body: Record<string, string> = {
        email: input.email,
        username: input.username,
        password: input.password,
      };
      if (input.inviteCode?.trim()) body.inviteCode = input.inviteCode.trim();
      const data = await apiJson<RegisterResponse>("/auth/register", {
        method: "POST",
        body: JSON.stringify(body),
      });
      setUser(data.user as AuthUser);
      setSubscription(data.subscription);
      return { trialLicenseKey: data.trialLicenseKey };
    },
    []
  );

  const logout = useCallback(async () => {
    await apiJson("/auth/logout", { method: "POST", body: "{}" });
    setUser(null);
    setSubscription(null);
  }, []);

  const value = useMemo(
    () => ({
      user,
      subscription,
      loading,
      refresh,
      login,
      register,
      logout,
    }),
    [user, subscription, loading, refresh, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
