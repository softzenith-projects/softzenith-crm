"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { ChevronLeft, ChevronRight, Plus, Search, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { TEAM, can, hasFeature, useMe } from "@/components/AppShell";
import { Initials, PageHeader, StatusBadge, humanize } from "@/components/common";
import { LeadForm } from "@/components/LeadForm";
import { api, formatDate, unwrap, type Schemas } from "@/lib/api";

type LeadPage = Schemas["PageResponseLeadResponse"];
type Status = NonNullable<Schemas["LeadResponse"]["status"]>;
type Source = NonNullable<Schemas["LeadResponse"]["sourceType"]>;

const STATUSES: Status[] = ["NEW", "CONTACTED", "ASSIGNED", "CLOSED"];
const SOURCES: Source[] = ["WEBSITE_FORM", "META_LEAD_AD", "WHATSAPP", "WALK_IN", "PHONE", "REFERRAL", "OTHER"];

export default function LeadsPage() {
  return <Suspense><LeadsFromUrl /></Suspense>;
}

/** Re-mounts the list when the URL filters change (e.g. a search from the top bar). */
function LeadsFromUrl() {
  const params = useSearchParams();
  return <Leads key={params.toString()} params={params} />;
}

function Leads({ params }: { params: URLSearchParams }) {
  const me = useMe();
  const router = useRouter();
  const team = hasFeature(me, TEAM);
  const [filters, setFilters] = useState({
    status: params.get("status") ?? "", sourceType: "", q: params.get("q") ?? "",
    unassigned: params.get("unassigned") === "true", page: 0,
  });
  const [adding, setAdding] = useState(params.get("new") === "1" && can(me, "LEAD_CREATE"));

  const { data: leads, mutate: load } = useSWR<LeadPage>(["leads", filters], () =>
    unwrap(api.GET("/api/v1/leads", {
      params: {
        query: {
          status: (filters.status || undefined) as Status | undefined,
          sourceType: (filters.sourceType || undefined) as Source | undefined,
          q: filters.q || undefined,
          unassigned: filters.unassigned || undefined,
          page: filters.page,
          size: 25,
        },
      },
    })), { keepPreviousData: true });

  const set = (patch: Partial<typeof filters>) => setFilters((f) => ({ ...f, page: 0, ...patch }));
  const closeAdd = () => {
    setAdding(false);
    if (params.get("new")) router.replace("/leads");
  };

  return (
    <>
      <PageHeader
        title="Leads"
        description={leads ? `${leads.totalElements} lead${leads.totalElements === 1 ? "" : "s"}` : "Every enquiry, from every source"}
        actions={can(me, "LEAD_CREATE") && <Button onClick={() => setAdding(true)}><Plus /> Add lead</Button>}
      />

      <Dialog open={adding} onOpenChange={(open) => (open ? setAdding(true) : closeAdd())}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Add a lead</DialogTitle>
            <DialogDescription>Walk-in, phone call or referral. The student gets the same welcome message as online enquiries.</DialogDescription>
          </DialogHeader>
          <LeadForm
            tenantSlug={me.tenant!.slug!}
            defaultBranchId={me.branch?.id}
            onSaved={(lead) => {
              closeAdd();
              load();
              // A role limited to its own (or its branch's) leads may not see a lead it just added until it is assigned.
              const visible = me.dataScope === "ALL" || lead.assignedTo?.id === me.userId
                || (me.dataScope === "BRANCH" && !!me.branch && lead.branch?.id === me.branch.id);
              if (visible) toast.success(`Lead ${lead.leadNumber} saved`, { action: { label: "Open", onClick: () => router.push(`/leads/${lead.id}`) } });
              else toast.success(`Lead ${lead.leadNumber} saved. It is in the unassigned queue; you will see it once it is assigned to you.`);
            }}
          />
        </DialogContent>
      </Dialog>

      <Card className="gap-0 p-0">
        <div className="flex flex-wrap items-center gap-3 border-b p-4">
          <div className="relative min-w-60 flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input type="search" placeholder="Name, mobile, email or lead #" value={filters.q}
                   onChange={(e) => set({ q: e.target.value })} className="pl-9" />
          </div>
          <NativeSelect value={filters.status} onChange={(e) => set({ status: e.target.value })}>
            <NativeSelectOption value="">All statuses</NativeSelectOption>
            {STATUSES.filter((s) => team || s !== "ASSIGNED").map((s) => <NativeSelectOption key={s} value={s}>{humanize(s)}</NativeSelectOption>)}
          </NativeSelect>
          <NativeSelect value={filters.sourceType} onChange={(e) => set({ sourceType: e.target.value })}>
            <NativeSelectOption value="">All sources</NativeSelectOption>
            {SOURCES.map((s) => <NativeSelectOption key={s} value={s}>{humanize(s)}</NativeSelectOption>)}
          </NativeSelect>
          {team && (
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={filters.unassigned} onCheckedChange={(v) => set({ unassigned: v === true })} />
              Unassigned only
            </label>
          )}
        </div>

        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 text-xs uppercase">
              <TableHead className="pl-4">Lead</TableHead>
              <TableHead>Interest</TableHead>
              <TableHead>Source</TableHead>
              <TableHead>Status</TableHead>
              {team && <TableHead>Assigned to</TableHead>}
              {team && <TableHead>Branch</TableHead>}
              <TableHead className="pr-4">Last enquiry</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {leads?.items?.map((l) => (
              <TableRow key={l.id} className="cursor-pointer" onClick={() => router.push(`/leads/${l.id}`)}>
                <TableCell className="pl-4">
                  <div className="flex items-center gap-3">
                    <Initials name={l.fullName} className="size-8" />
                    <div>
                      <Link href={`/leads/${l.id}`} className="font-medium hover:text-primary" onClick={(e) => e.stopPropagation()}>
                        {l.fullName}
                      </Link>
                      {(l.enquiryCount ?? 1) > 1 && (
                        <span className="ml-2 rounded-full bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-700 ring-1 ring-amber-200 ring-inset">
                          Repeat ×{l.enquiryCount}
                        </span>
                      )}
                      <p className="text-xs text-muted-foreground">{l.leadNumber} · {l.phone}</p>
                    </div>
                  </div>
                </TableCell>
                <TableCell>{[l.serviceInterest, l.preferredCountry].filter(Boolean).join(" · ") || "—"}</TableCell>
                <TableCell>{humanize(l.sourceType)}</TableCell>
                <TableCell><StatusBadge status={l.status} /></TableCell>
                {team && <TableCell>{l.assignedTo?.name ?? <span className="text-muted-foreground">Unassigned</span>}</TableCell>}
                {team && <TableCell>{l.branch?.name ?? "—"}</TableCell>}
                <TableCell className="pr-4 text-muted-foreground">{formatDate(l.lastEnquiryAt ?? l.createdAt)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {leads && leads.items?.length === 0 && (
          <div className="flex flex-col items-center gap-2 py-12 text-sm text-muted-foreground">
            <UserRound className="size-8" /> No leads match these filters.
          </div>
        )}

        {leads && (leads.totalPages ?? 0) > 1 && (
          <div className="flex items-center justify-end gap-3 border-t p-3 text-sm">
            <span className="text-muted-foreground">Page {filters.page + 1} of {leads.totalPages}</span>
            <Button variant="outline" size="icon-sm" disabled={filters.page === 0} aria-label="Previous page"
                    onClick={() => setFilters((f) => ({ ...f, page: f.page - 1 }))}><ChevronLeft /></Button>
            <Button variant="outline" size="icon-sm" disabled={filters.page + 1 >= (leads.totalPages ?? 0)} aria-label="Next page"
                    onClick={() => setFilters((f) => ({ ...f, page: f.page + 1 }))}><ChevronRight /></Button>
          </div>
        )}
      </Card>
    </>
  );
}
