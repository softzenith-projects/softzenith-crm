"use client";

// Last-resort error page: an error in the root layout. Reports it to Sentry and shows a plain message.
import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";

export default function GlobalError({ error }: { error: Error & { digest?: string } }) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", padding: "2rem" }}>
        <h1>Something went wrong</h1>
        <p>Please reload the page. If it keeps happening, tell SoftZenith support.</p>
      </body>
    </html>
  );
}
