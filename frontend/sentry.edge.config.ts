// Sentry in the edge runtime (not used today; covers an edge route when one appears). See sentry.privacy.ts.
import * as Sentry from "@sentry/nextjs";
import { sentryOptions } from "./sentry.privacy";

Sentry.init(sentryOptions);
