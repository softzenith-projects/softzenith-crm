"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { TEAM, hasFeature, useMe } from "@/components/AppShell";

/** Staff and Branches belong to the Team & branches feature; without it even a typed-in address goes to the leads. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const me = useMe();
  const router = useRouter();
  const allowed = hasFeature(me, TEAM);

  useEffect(() => {
    if (!allowed) router.replace("/leads");
  }, [allowed, router]);

  return allowed ? children : null;
}
