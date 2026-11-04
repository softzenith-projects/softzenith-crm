"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import useSWR from "swr";
import { ArrowRight } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { TEAM, can, hasFeature, useMe } from "@/components/AppShell";
import { Initials, PageHeader, humanize } from "@/components/common";
import { api, unwrap } from "@/lib/api";

const PERIODS = {
  all: "All time",
  today: "Today",
  week: "Last 7 days",
  month: "Last 30 days",
} as const;
type Period = keyof typeof PERIODS;

function fromFor(period: Period): string | undefined {
  if (period === "all") return undefined;
  const d = new Date();
  if (period === "today") d.setHours(0, 0, 0, 0);
  else d.setDate(d.getDate() - (period === "week" ? 7 : 30));
  return d.toISOString();
}

const STATUSES = [
  ["NEW", "bg-blue-500"],
  ["CONTACTED", "bg-violet-500"],
  ["ASSIGNED", "bg-emerald-500"],
  ["CLOSED", "bg-zinc-400"],
] as const;

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

/** How many leads came in and where they stand. Needs REPORTS_VIEW (Admin by default); others go to their leads. */
export default function DashboardPage() {
  const me = useMe();
  const router = useRouter();
  const allowed = can(me, "REPORTS_VIEW");
  const team = hasFeature(me, TEAM);
  const [period, setPeriod] = useState<Period>("all");
  const { data: stats } = useSWR(allowed ? ["lead-stats", period] : null, () =>
    unwrap(api.GET("/api/v1/leads/stats", { params: { query: { from: fromFor(period) } } })), { refreshInterval: 30000 });

  useEffect(() => {
    if (!allowed) router.replace("/leads");
  }, [allowed, router]);
  if (!allowed) return null;

  const total = stats?.total ?? 0;
  const sources = Object.entries(stats?.bySource ?? {}).filter(([, n]) => n > 0).sort((a, b) => b[1] - a[1]);

  return (
    <>
      <PageHeader
        eyebrow={!team ? undefined : me.dataScope === "ALL" ? "All branches" : me.branch?.name}
        title={`${greeting()}, ${me.fullName?.split(" ")[0]}`}
        description="Where your enquiries stand."
        actions={
          <NativeSelect value={period} onChange={(e) => setPeriod(e.target.value as Period)}>
            {Object.entries(PERIODS).map(([k, label]) => <NativeSelectOption key={k} value={k}>{label}</NativeSelectOption>)}
          </NativeSelect>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Leads received" value={stats?.total} href="/leads" note={PERIODS[period]} />
        {team && (
          <Stat label="Open & unassigned" value={stats?.openUnassigned} href="/leads?unassigned=true"
                note="Waiting for a counsellor" tone={stats?.openUnassigned ? "warn" : undefined} />
        )}
        <Stat label="New" value={stats?.byStatus?.NEW} href="/leads?status=NEW" note="Not contacted yet" />
        {team
          ? <Stat label="Assigned" value={stats?.byStatus?.ASSIGNED} href="/leads?status=ASSIGNED" note="With a counsellor" />
          : (
            <>
              <Stat label="Contacted" value={stats?.byStatus?.CONTACTED} href="/leads?status=CONTACTED" note="In conversation" />
              <Stat label="Closed" value={stats?.byStatus?.CLOSED} href="/leads?status=CLOSED" note="Finished" />
            </>
          )}
      </div>

      <div className={`mt-6 grid gap-6 ${team ? "xl:grid-cols-3" : "xl:grid-cols-2"}`}>
        <Card>
          <CardHeader><CardTitle>Pipeline</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {STATUSES.filter(([s]) => team || s !== "ASSIGNED").map(([s, color]) => (
              <Bar key={s} label={humanize(s)} value={stats?.byStatus?.[s] ?? 0} total={total} color={color} href={`/leads?status=${s}`} />
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>By source</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {sources.map(([source, n]) => <Bar key={source} label={humanize(source)} value={n} total={total} color="bg-sky-500" />)}
            {stats && sources.length === 0 && <p className="text-sm text-muted-foreground">No leads in this period.</p>}
          </CardContent>
        </Card>

        {team && (
          <Card>
            <CardHeader><CardTitle>Team workload</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {stats?.byAssignee?.map((a) => (
                <div key={a.userId} className="flex items-center gap-3">
                  <Initials name={a.name} />
                  <span className="flex-1 text-sm font-medium">{a.name}</span>
                  <span className="text-sm text-muted-foreground">{a.count} assigned</span>
                </div>
              ))}
              {stats && stats.byAssignee?.length === 0 && <p className="text-sm text-muted-foreground">No assigned leads in this period.</p>}
            </CardContent>
          </Card>
        )}
      </div>
      <p className="mt-4 text-xs text-muted-foreground">Counts leads created in the selected period, by their current status.</p>
    </>
  );
}

function Stat({ label, value, note, href, tone }: {
  label: string; value?: number; note?: ReactNode; href: string; tone?: "warn";
}) {
  return (
    <Link href={href} className="group">
      <Card className="h-full px-5 transition group-hover:ring-primary/40">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={tone === "warn" ? "text-3xl font-semibold text-amber-600" : "text-3xl font-semibold"}>{value ?? "…"}</p>
        <p className="flex items-center justify-between text-xs text-muted-foreground">
          {note}
          <ArrowRight className="size-3.5 opacity-0 transition group-hover:opacity-100" />
        </p>
      </Card>
    </Link>
  );
}

function Bar({ label, value, total, color, href }: { label: string; value: number; total: number; color: string; href?: string }) {
  const pct = total ? Math.round((value / total) * 100) : 0;
  const text = <span className="w-28 shrink-0 text-sm">{label}</span>;
  return (
    <div className="flex items-center gap-3">
      {href ? <Link href={href} className="hover:text-primary">{text}</Link> : text}
      <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="w-10 text-right text-sm font-semibold tabular-nums">{value}</span>
    </div>
  );
}
