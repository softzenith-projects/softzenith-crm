// What Sentry may collect from the staff app: nothing personal. Staff screens and requests carry students' names,
// phones and emails (DPDP), so no user info, cookies, headers, request/response bodies or query strings; errors and
// timings only. Shared by instrumentation-client.ts, sentry.server.config.ts and sentry.edge.config.ts.
import type { BrowserOptions } from "@sentry/nextjs";

export const sentryOptions = {
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN || undefined,
  environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT || process.env.NODE_ENV,
  tracesSampleRate: 0.1,
  dataCollection: {
    userInfo: false,
    cookies: false,
    httpHeaders: false,
    httpBodies: [],
    urlQueryParams: false,
    graphQL: { document: false, variables: false },
    genAI: { inputs: false, outputs: false },
  },
} satisfies BrowserOptions;
