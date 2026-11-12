// Sentry in the browser: unhandled errors and slow page loads of the staff app. No session replay and no personal
// data (sentry.privacy.ts). Without a DSN (local, tests) it stays off.
import * as Sentry from "@sentry/nextjs";
import { sentryOptions } from "./sentry.privacy";

Sentry.init(sentryOptions);

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
