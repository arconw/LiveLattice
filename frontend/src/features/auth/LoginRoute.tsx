import { LockKeyhole } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { AppError } from "../../contracts/api-client";
import { Button, Input } from "../../design-system/components";
import { useAuth } from "./AuthProvider";

type LoginState = {
  from?: string;
  expired?: boolean;
};

type FieldErrors = {
  email?: string;
  password?: string;
};

export function LoginRoute() {
  const auth = useAuth();
  const { clearExpired } = auth;
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as LoginState | null;
  const from = useMemo(() => safeRedirectPath(locationState?.from), [locationState?.from]);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [showExpired, setShowExpired] = useState(Boolean(locationState?.expired || auth.expired));

  useEffect(() => {
    if (showExpired) {
      clearExpired();
    }
  }, [clearExpired, showExpired]);

  if (auth.status === "authenticated") {
    return <Navigate to={from} replace />;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextFieldErrors = validateFields(email, password);
    setFieldErrors(nextFieldErrors);
    setFormError("");
    setShowExpired(false);

    if (Object.keys(nextFieldErrors).length > 0) {
      return;
    }

    setSubmitting(true);

    try {
      await auth.login({ email, password });
      navigate(from, { replace: true });
    } catch (error) {
      setFormError(messageForLoginError(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-route">
      <section className="login-panel" aria-labelledby="login-title">
        <span className="brand-mark large" aria-hidden="true" />
        <span className="kicker">Gateway auth</span>
        <h1 id="login-title">LiveLattice</h1>
        <p>Sign in through the Gateway-managed identity flow.</p>

        {showExpired ? (
          <div className="form-alert" role="status">
            Your session expired. Sign in again to continue.
          </div>
        ) : null}

        {formError ? (
          <div className="form-alert form-alert-danger" role="alert" aria-live="assertive">
            {formError}
          </div>
        ) : null}

        <form className="auth-form" onSubmit={submit} noValidate>
          <div className="form-field">
            <label className="field-label" htmlFor="email">
              Email
            </label>
            <Input id="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? "email-error" : undefined} />
            {fieldErrors.email ? (
              <span className="field-error" id="email-error">
                {fieldErrors.email}
              </span>
            ) : null}
          </div>

          <div className="form-field">
            <label className="field-label" htmlFor="password">
              Password
            </label>
            <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} aria-invalid={Boolean(fieldErrors.password)} aria-describedby={fieldErrors.password ? "password-error" : undefined} />
            {fieldErrors.password ? (
              <span className="field-error" id="password-error">
                {fieldErrors.password}
              </span>
            ) : null}
          </div>

          <Button variant="primary" type="submit" disabled={submitting} icon={<LockKeyhole size={17} aria-hidden="true" />}>
            {submitting ? "Signing in" : "Sign in"}
          </Button>
        </form>
      </section>
    </main>
  );
}

function validateFields(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};

  if (!email.trim()) {
    errors.email = "Email is required.";
  }

  if (!password) {
    errors.password = "Password is required.";
  }

  return errors;
}

function messageForLoginError(error: unknown) {
  if (error instanceof AppError && error.status === 401) {
    return "Email or password did not match a Gateway session.";
  }

  if (error instanceof AppError && error.status === 400) {
    return error.message;
  }

  return "The Gateway auth request could not be completed.";
}

function safeRedirectPath(path: string | undefined) {
  if (!path || !path.startsWith("/") || path.startsWith("//") || path === "/login") {
    return "/workspaces";
  }

  return path;
}
