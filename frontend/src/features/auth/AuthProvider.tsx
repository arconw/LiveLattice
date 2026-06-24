import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { createGatewayClient } from "../../contracts/api-client";
import type { GatewayClient } from "../../contracts/api-client";
import type { AuthTokenResponse, AuthUser, LoginCredentials } from "../../contracts/auth";
import { loginWithGateway, logoutGatewaySession, refreshGatewaySession } from "../../contracts/auth";

export const refreshTokenStorageKey = "livelattice.refreshToken";

export type AuthStatus = "restoring" | "authenticated" | "unauthenticated";

export type AuthSessionState = {
  status: AuthStatus;
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  expired: boolean;
};

export type AuthContextValue = AuthSessionState & {
  client: GatewayClient;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<string | null>;
  clearExpired: () => void;
};

const unauthenticatedState: AuthSessionState = {
  status: "unauthenticated",
  accessToken: null,
  refreshToken: null,
  user: null,
  expired: false
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children, fetchImpl, initialSession }: { children: ReactNode; fetchImpl?: typeof fetch; initialSession?: AuthTokenResponse }) {
  const [state, setState] = useState<AuthSessionState>(() => sessionStateFromResponse(initialSession));
  const accessTokenRef = useRef(initialSession?.accessToken ?? null);
  const refreshTokenRef = useRef(initialSession?.refreshToken ?? null);
  const initialSessionRef = useRef(Boolean(initialSession));
  const unauthenticatedClient = useMemo(() => createGatewayClient({ fetchImpl }), [fetchImpl]);

  const applyAuthResponse = useCallback((response: AuthTokenResponse) => {
    const nextRefreshToken = response.refreshToken ?? refreshTokenRef.current;

    accessTokenRef.current = response.accessToken;
    refreshTokenRef.current = nextRefreshToken ?? null;

    if (nextRefreshToken) {
      window.sessionStorage.setItem(refreshTokenStorageKey, nextRefreshToken);
    } else {
      window.sessionStorage.removeItem(refreshTokenStorageKey);
    }

    setState({
      status: "authenticated",
      accessToken: response.accessToken,
      refreshToken: nextRefreshToken ?? null,
      user: response.user,
      expired: false
    });
  }, []);

  const clearSession = useCallback((expired = false) => {
    accessTokenRef.current = null;
    refreshTokenRef.current = null;
    window.sessionStorage.removeItem(refreshTokenStorageKey);
    setState({ ...unauthenticatedState, expired });
  }, []);

  const refreshSession = useCallback(async () => {
    const refreshToken = refreshTokenRef.current;

    if (!refreshToken) {
      clearSession(true);
      return null;
    }

    try {
      const response = await refreshGatewaySession(unauthenticatedClient, refreshToken);
      applyAuthResponse(response);
      return response.accessToken;
    } catch {
      clearSession(true);
      return null;
    }
  }, [applyAuthResponse, clearSession, unauthenticatedClient]);

  const client = useMemo(
    () =>
      createGatewayClient({
        fetchImpl,
        getAccessToken: () => accessTokenRef.current,
        refreshOnUnauthorized: refreshSession
      }),
    [fetchImpl, refreshSession]
  );

  const login = useCallback(
    async (credentials: LoginCredentials) => {
      const response = await loginWithGateway(unauthenticatedClient, credentials);
      applyAuthResponse(response);
    },
    [applyAuthResponse, unauthenticatedClient]
  );

  const logout = useCallback(async () => {
    const refreshToken = refreshTokenRef.current;
    clearSession(false);

    if (!refreshToken) {
      return;
    }

    try {
      await logoutGatewaySession(unauthenticatedClient, refreshToken);
    } catch {
      return;
    }
  }, [clearSession, unauthenticatedClient]);

  const clearExpired = useCallback(() => {
    setState((current) => ({ ...current, expired: false }));
  }, []);

  useEffect(() => {
    if (initialSessionRef.current) {
      return;
    }

    let active = true;
    const refreshToken = window.sessionStorage.getItem(refreshTokenStorageKey);

    if (!refreshToken) {
      setState(unauthenticatedState);
      return;
    }

    refreshTokenRef.current = refreshToken;
    setState((current) => ({ ...current, status: "restoring", refreshToken, expired: false }));

    refreshGatewaySession(unauthenticatedClient, refreshToken)
      .then((response) => {
        if (active) {
          applyAuthResponse(response);
        }
      })
      .catch(() => {
        if (active) {
          clearSession(true);
        }
      });

    return () => {
      active = false;
    };
  }, [applyAuthResponse, clearSession, unauthenticatedClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      client,
      login,
      logout,
      refreshSession,
      clearExpired
    }),
    [clearExpired, client, login, logout, refreshSession, state]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}

function sessionStateFromResponse(response: AuthTokenResponse | undefined): AuthSessionState {
  if (!response) {
    return { ...unauthenticatedState, status: "restoring" };
  }

  return {
    status: "authenticated",
    accessToken: response.accessToken,
    refreshToken: response.refreshToken ?? null,
    user: response.user,
    expired: false
  };
}
