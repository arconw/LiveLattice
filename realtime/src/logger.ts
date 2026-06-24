import pino from "pino";

export function createLogger(service: string) {
  return pino({
    level: process.env.LOG_LEVEL ?? "info",
    base: { service },
    messageKey: "message",
    timestamp: pino.stdTimeFunctions.isoTime,
    redact: {
      paths: [
        "password",
        "token",
        "secret",
        "authorization",
        "cookie",
        "req.headers.authorization",
        "req.headers.cookie",
        "body.password",
        "body.token",
        "body.secret"
      ],
      censor: "[REDACTED]"
    }
  });
}
