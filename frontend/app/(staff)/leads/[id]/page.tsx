"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { ChevronRight, Mail, MessageCircle, Phone } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { TEAM, can, hasFeature, useMe } from "@/components/AppShell";
import { ErrorText, Field, Initials, StatusBadge, humanize } from "@/components/common";
import { api, errorMessage, formatDate, unwrap, type Schemas } from "@/lib/api";

type Lead = Schemas["LeadResponse"];

export default function LeadDetailPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const { data: lead, error, mutate: reloadLead } = useSWR(["lead", id], () =>
    unwrap(api.GET("/api/v1/leads/{id}", { params: { path: { id } } })));
  const { data: activities = [], mutate: reloadActivities } = useSWR(["lead-activities", id], () =>
    unwrap(api.GET("/api/v1/leads/{id}/activities", { params: { path: { id } } })));
  // Notifications go out asynchronously just after the lead is saved, so poll briefly.
  const { data: notifications = [] } = useSWR(["lead-notifications", id], () =>
    unwrap(api.GET("/api/v1/leads/{leadId}/notifications", { params: { path: { leadId: id } } })), { refreshInterval: 5000 });
  const load = () => {
    reloadLead();
    reloadActivities();
  };

  if (error) return <ErrorText>{error.message}</ErrorText>;
  if (!lead) return <p className="text-sm text-muted-foreground">Loading…</p>;

  const team = hasFeature(me, TEAM);
  const canAssign = team && can(me, "LEAD_ASSIGN") && lead.status !== "CLOSED";
  // Leaving CLOSED is a reopen, which also needs LEAD_REOPEN (Admin); don't offer a button that can only fail.
  const canStatus = can(me, "LEAD_CHANGE_STATUS") && (lead.status !== "CLOSED" || can(me, "LEAD_REOPEN"));

  return (
    <div className="space-y-6">
      <nav className="flex items-center gap-1 text-sm text-muted-foreground">
        <Link href="/leads" className="hover:text-foreground">Leads</Link>
        <ChevronRight className="size-4" />
        <span className="font-medium text-foreground">{lead.leadNumber}</span>
      </nav>

      <Card className="gap-0 p-0">
        <div className="flex flex-wrap items-start gap-4 p-6">
          <Initials name={lead.fullName} className="size-14 rounded-xl text-lg" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight">{lead.fullName}</h1>
              <StatusBadge status={lead.status} />
              {lead.preferredCountry && <StatusBadge status="INVITED">{lead.preferredCountry}</StatusBadge>}
            </div>
            <p className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
              <span>{lead.leadNumber}</span>
              <a href={`tel:${lead.phone}`} className="flex items-center gap-1 hover:text-foreground"><Phone className="size-3.5" />{lead.phone}</a>
              {lead.email && <a href={`mailto:${lead.email}`} className="flex items-center gap-1 hover:text-foreground"><Mail className="size-3.5" />{lead.email}</a>}
            </p>
          </div>
          <a href={`tel:${lead.phone}`} className={buttonVariants({ variant: "outline", size: "lg" })}><Phone /> Call</a>
        </div>
        <dl className={`grid grid-cols-2 border-t ${team ? "md:grid-cols-5" : "md:grid-cols-3"} [&>div]:border-r [&>div]:px-6 [&>div]:py-4 [&>div:last-child]:border-r-0`}>
          {team && <Info label="Assigned to">{lead.assignedTo?.name ?? <span className="text-amber-600">Unassigned</span>}</Info>}
          {team && <Info label="Branch">{lead.branch?.name ?? "—"}</Info>}
          <Info label="Interested in">{lead.serviceInterest ?? "—"}</Info>
          <Info label="Source">{[humanize(lead.sourceType), lead.sourceDetail].filter(Boolean).join(" · ")}</Info>
          <Info label={(lead.enquiryCount ?? 1) > 1 ? `Enquired ${lead.enquiryCount}×, last` : "Enquired"}>
            {formatDate(lead.lastEnquiryAt ?? lead.createdAt)}
          </Info>
        </dl>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          {(lead.message || lead.utmCampaign || lead.status === "CLOSED") && (
            <Card>
              <CardHeader><CardTitle>Enquiry</CardTitle></CardHeader>
              <CardContent className="space-y-2 text-sm">
                {lead.message && <p className="whitespace-pre-wrap">{lead.message}</p>}
                {lead.utmCampaign && (
                  <p className="text-muted-foreground">Campaign: {[lead.utmSource, lead.utmMedium, lead.utmCampaign].filter(Boolean).join(" / ")}</p>
                )}
                {lead.status === "CLOSED" && (
                  <p className="text-muted-foreground">Closed {formatDate(lead.closedAt)}: {lead.closedReason}</p>
                )}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle>Activity</CardTitle></CardHeader>
            <CardContent>
              <ol className="relative space-y-5 border-l pl-6">
                {activities.map((a) => (
                  <li key={a.id} className="relative">
                    <span className="absolute top-1 -left-[29px] size-2.5 rounded-full bg-primary ring-4 ring-card" />
                    <p className="text-sm font-medium">{humanize(a.type)}</p>
                    <p className="text-sm text-muted-foreground">
                      {[a.fromValue && `${code(a.fromValue)} →`, code(a.toValue), a.note && `(${a.note})`].filter(Boolean).join(" ")}
                    </p>
                    <p className="text-xs text-muted-foreground">{a.actor?.name ?? "System"} · {formatDate(a.at)}</p>
                  </li>
                ))}
              </ol>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          {(canAssign || canStatus) && (
            <Card>
              <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
              <CardContent className="space-y-6">
                {/* Keyed on the lead's state so each form starts fresh after a change (e.g. close, then reopen). */}
                {canAssign && <Assign key={lead.assignedTo?.id ?? "none"} lead={lead} onDone={load} />}
                {canStatus && <ChangeStatus key={lead.status} lead={lead} onDone={load} />}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle>Messages sent</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {notifications.map((n) => (
                <details key={n.id} className="group rounded-lg border p-3 text-sm">
                  <summary className="flex cursor-pointer list-none items-start gap-3">
                    {n.channel === "WHATSAPP"
                      ? <MessageCircle className="mt-0.5 size-4 text-emerald-600" />
                      : <Mail className="mt-0.5 size-4 text-primary" />}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{n.subject ?? humanize(n.kind)}</span>
                      <span className="block truncate text-xs text-muted-foreground">{n.recipient} · {formatDate(n.at)}</span>
                    </span>
                    <StatusBadge status={n.status} />
                  </summary>
                  <pre className="mt-3 font-sans text-xs whitespace-pre-wrap text-muted-foreground">{n.body}</pre>
                  {n.error && <ErrorText>{n.error}</ErrorText>}
                </details>
              ))}
              {notifications.length === 0 && (
                <p className="text-sm text-muted-foreground">None yet. They go out a moment after the lead is created.</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

/** Activity values are either codes (WALK_IN, CONTACTED) or free text (a name). */
const code = (v?: string) => (v && /^[A-Z_]+$/.test(v) ? humanize(v) : v);

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] font-semibold tracking-wider text-muted-foreground uppercase">{label}</dt>
      <dd className="mt-1 truncate text-sm font-medium">{children}</dd>
    </div>
  );
}

function Assign({ lead, onDone }: { lead: Lead; onDone: () => void }) {
  const { data: candidates = [] } = useSWR("assignable-users", () =>
    unwrap(api.GET("/api/v1/users", { params: { query: { assignable: true } } })));
  const users = candidates.filter((u) => u.status !== "DISABLED");
  const [userId, setUserId] = useState(lead.assignedTo?.id ?? "");
  const [error, setError] = useState("");

  const assign = async () => {
    const { error } = await api.PUT("/api/v1/leads/{id}/assignment", { params: { path: { id: lead.id! } }, body: { userId } });
    if (error) setError(errorMessage(error));
    else {
      setError("");
      toast.success("Lead assigned");
      onDone();
    }
  };

  return (
    <div className="space-y-2">
      <Field label="Assign to">
        <NativeSelect value={userId} onChange={(e) => setUserId(e.target.value)} className="w-full">
          <NativeSelectOption value="" disabled>Choose a counsellor…</NativeSelectOption>
          {users.map((u) => (
            <NativeSelectOption key={u.id} value={u.id}>
              {u.fullName} · {u.role?.name}{u.branch ? ` · ${u.branch.name}` : ""}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
      <ErrorText>{error}</ErrorText>
      <Button onClick={assign} disabled={!userId || userId === lead.assignedTo?.id} className="w-full">
        {lead.assignedTo ? "Reassign" : "Assign"}
      </Button>
    </div>
  );
}

function ChangeStatus({ lead, onDone }: { lead: Lead; onDone: () => void }) {
  const [status, setStatus] = useState<"NEW" | "CONTACTED" | "CLOSED">(lead.status === "CLOSED" ? "NEW" : "CONTACTED");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");

  const save = async () => {
    const { error } = await api.POST("/api/v1/leads/{id}/status", {
      params: { path: { id: lead.id! } },
      body: { status, reason: reason || undefined },
    });
    if (error) setError(errorMessage(error));
    else {
      setError("");
      setReason("");
      toast.success(lead.status === "CLOSED" ? "Lead reopened" : "Status updated");
      onDone();
    }
  };

  return (
    <div className="space-y-2">
      <Field label={lead.status === "CLOSED" ? "Reopen as" : "Change status to"}>
        <NativeSelect value={status} onChange={(e) => setStatus(e.target.value as typeof status)} className="w-full">
          {(["NEW", "CONTACTED", "CLOSED"] as const).filter((s) => s !== lead.status).map((s) => (
            <NativeSelectOption key={s} value={s}>{humanize(s)}</NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
      {status === "CLOSED" && (
        <Field label="Reason *"><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Not interested" /></Field>
      )}
      <ErrorText>{error}</ErrorText>
      <Button variant="outline" onClick={save} className="w-full">{lead.status === "CLOSED" ? "Reopen" : "Update status"}</Button>
    </div>
  );
}
