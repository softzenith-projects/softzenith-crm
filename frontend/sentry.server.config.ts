// Sentry in the Next.js server (the staff app's server side and its /api proxy). See sentry.privacy.ts.
import * as Sentry from "@sentry/nextjs";
import { sentryOptions } from "./sentry.privacy";

Sentry.init(sentryOptions);
