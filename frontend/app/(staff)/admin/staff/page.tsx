"use client";

import { useState, type FormEvent } from "react";
import useSWR from "swr";
import { toast } from "sonner";
import { Copy, KeyRound, MoreHorizontal, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { can, useMe } from "@/components/AppShell";
import { ErrorText, Field, Initials, PageHeader, StatusBadge } from "@/components/common";
import { api, errorMessage, unwrap, type Schemas } from "@/lib/api";
import { generateSigninCode, signinCodeProblem } from "@/lib/signin-code";

type Staff = Schemas["StaffResponse"];

/** Onboard staff (counsellors, receptionists, managers...) and keep basic employee records. */
export default function StaffPage() {
  const me = useMe();
  const { data: staff = [], mutate: load } = useSWR("staff", () => unwrap(api.GET("/api/v1/users")));
  const { data: roles = [] } = useSWR("roles", () => unwrap(api.GET("/api/v1/roles")));
  const { data: allBranches = [] } = useSWR("branches", () => unwrap(api.GET("/api/v1/branches")));
  const branches = allBranches.filter((x) => x.active);
  const [editing, setEditing] = useState<Staff | "new" | null>(null);
  const [settingCode, setSettingCode] = useState<Staff | null>(null);
  const manage = can(me, "USER_MANAGE");

  const toggle = async (u: Staff) => {
    const path = u.status === "DISABLED" ? "/api/v1/users/{id}/enable" : "/api/v1/users/{id}/disable";
    const { error } = await api.POST(path, { params: { path: { id: u.id! } } });
    if (error) toast.error(errorMessage(error));
    load();
  };

  // For when someone's sign-in account was recreated or their number changed: their next OTP sign-in links again.
  const resetSignIn = async (u: Staff) => {
    if (!window.confirm(`Reset sign-in for ${u.fullName}? They will be Invited again until they next sign in with ${u.phone}.`)) return;
    const { error } = await api.POST("/api/v1/users/{id}/reset-sign-in", { params: { path: { id: u.id! } } });
    if (error) toast.error(errorMessage(error));
    else toast.success("Sign-in reset; the phone number can be edited again");
    load();
  };

  return (
    <>
      <PageHeader
        title="Staff"
        description="New staff are Invited until they first sign in with their mobile number."
        actions={manage && <Button onClick={() => setEditing("new")}><Plus /> Add staff member</Button>}
      />

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-2xl">
          {editing && (
            <StaffForm
              staff={editing === "new" ? null : editing}
              roles={roles}
              branches={branches}
              onSaved={() => {
                setEditing(null);
                load();
                toast.success("Staff member saved");
              }}
            />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={settingCode !== null} onOpenChange={(open) => !open && setSettingCode(null)}>
        <DialogContent className="sm:max-w-md">
          {settingCode && <SigninCodeForm staff={settingCode} onSaved={load} onDone={() => setSettingCode(null)} />}
        </DialogContent>
      </Dialog>

      <Card className="p-0">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 text-xs uppercase">
              <TableHead className="pl-4">Name</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Branch</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Emp. code</TableHead>
              <TableHead>Joined</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Sign-in code</TableHead>
              {manage && <TableHead className="pr-4" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.map((u) => (
              <TableRow key={u.id}>
                <TableCell className="pl-4">
                  <div className="flex items-center gap-3">
                    <Initials name={u.fullName} className="size-8" />
                    <div>
                      <p className="font-medium">{u.fullName}</p>
                      <p className="text-xs text-muted-foreground">{u.phone}{u.designation && ` · ${u.designation}`}</p>
                    </div>
                  </div>
                </TableCell>
                <TableCell>{u.role?.name}</TableCell>
                <TableCell>{u.branch?.name ?? "—"}</TableCell>
                <TableCell>{u.email ?? "—"}</TableCell>
                <TableCell>{u.employeeCode ?? "—"}</TableCell>
                <TableCell>{u.joinedOn ?? "—"}</TableCell>
                <TableCell><StatusBadge status={u.status} /></TableCell>
                <TableCell>
                  {u.hasSigninCode
                    ? <span className="text-xs text-muted-foreground">Set{u.signinCodeSetAt && ` ${new Date(u.signinCodeSetAt).toLocaleDateString()}`}</span>
                    : <span className="text-xs font-medium text-amber-600">Not set</span>}
                </TableCell>
                {manage && (
                  <TableCell className="pr-4 text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" aria-label="Actions" />}>
                        <MoreHorizontal />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-44">
                        <DropdownMenuItem onClick={() => setEditing(u)}>Edit</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => setSettingCode(u)}>
                          {u.hasSigninCode ? "Change sign-in code" : "Set sign-in code"}
                        </DropdownMenuItem>
                        {u.id !== me.userId && (
                          <DropdownMenuItem onClick={() => toggle(u)} variant={u.status === "DISABLED" ? "default" : "destructive"}>
                            {u.status === "DISABLED" ? "Enable" : "Disable"}
                          </DropdownMenuItem>
                        )}
                        {u.id !== me.userId && u.status === "ACTIVE" && (
                          <DropdownMenuItem onClick={() => resetSignIn(u)}>Reset sign-in</DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </>
  );
}

function StaffForm({ staff, roles, branches, onSaved }: {
  staff: Staff | null;
  roles: Schemas["RoleResponse"][];
  branches: Schemas["BranchResponse"][];
  onSaved: () => void;
}) {
  const [error, setError] = useState("");

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
    const body = {
      fullName: f.fullName, phone: f.phone, email: f.email || undefined, roleId: f.roleId,
      branchId: f.branchId || undefined, employeeCode: f.employeeCode || undefined,
      designation: f.designation || undefined, joinedOn: f.joinedOn || undefined,
    };
    const { error } = staff
      ? await api.PUT("/api/v1/users/{id}", { params: { path: { id: staff.id! } }, body })
      : await api.POST("/api/v1/users", { body });
    if (error) setError(errorMessage(error));
    else onSaved();
  };

  return (
    <>
      <DialogHeader>
        <DialogTitle>{staff ? `Edit ${staff.fullName}` : "New staff member"}</DialogTitle>
        <DialogDescription>They sign in with this mobile number; alerts go to the email.</DialogDescription>
      </DialogHeader>
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name *"><Input name="fullName" required defaultValue={staff?.fullName} /></Field>
        <Field label="Mobile *">
          <Input name="phone" type="tel" required defaultValue={staff?.phone} disabled={staff?.status === "ACTIVE"} />
        </Field>
        <Field label="Email"><Input name="email" type="email" defaultValue={staff?.email} /></Field>
        <Field label="Role *">
          <NativeSelect name="roleId" required defaultValue={staff?.role?.id ?? ""} className="w-full">
            <NativeSelectOption value="" disabled>Choose…</NativeSelectOption>
            {roles.map((r) => <NativeSelectOption key={r.id} value={r.id}>{r.name}</NativeSelectOption>)}
          </NativeSelect>
        </Field>
        <Field label="Branch">
          <NativeSelect name="branchId" defaultValue={staff?.branch?.id ?? ""} className="w-full">
            <NativeSelectOption value="">—</NativeSelectOption>
            {branches.map((b) => <NativeSelectOption key={b.id} value={b.id}>{b.name}</NativeSelectOption>)}
          </NativeSelect>
        </Field>
        <Field label="Designation"><Input name="designation" defaultValue={staff?.designation} /></Field>
        <Field label="Employee code"><Input name="employeeCode" defaultValue={staff?.employeeCode} /></Field>
        <Field label="Joined on"><Input name="joinedOn" type="date" defaultValue={staff?.joinedOn} /></Field>
        {/* A disabled input is not submitted; keep the phone for the update call. */}
        {staff?.status === "ACTIVE" && <input type="hidden" name="phone" value={staff.phone} />}
        <div className="flex items-center justify-end gap-3 sm:col-span-2">
          <ErrorText>{error}</ErrorText>
          <Button type="submit">Save</Button>
        </div>
      </form>
    </>
  );
}

/**
 * The Admin chooses (or generates) the code this person signs in with, then hands it over. Only a hash is stored, so
 * the code is shown here once and never again; a forgotten code is replaced, which also ends their open sessions.
 */
function SigninCodeForm({ staff, onSaved, onDone }: { staff: Staff; onSaved: () => void; onDone: () => void }) {
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const problem = signinCodeProblem(code);
    if (problem) return setError(problem);
    const { error } = await api.PUT("/api/v1/users/{id}/signin-code", { params: { path: { id: staff.id! } }, body: { code } });
    if (error) return setError(errorMessage(error));
    setSaved(true);
    onSaved();
  };

  if (saved) {
    const details = `Mobile: ${staff.phone}\nSign-in code: ${code}`;
    return (
      <>
        <DialogHeader>
          <DialogTitle>Sign-in code saved</DialogTitle>
          <DialogDescription>
            Give these to {staff.fullName} privately. The code cannot be shown again; if it is forgotten, set a new one.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1 rounded-lg border bg-muted/40 p-4 font-mono text-sm">
          <p>Mobile: {staff.phone}</p>
          <p>Sign-in code: <span className="font-semibold tracking-widest">{code}</span></p>
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => navigator.clipboard?.writeText(details).then(() => toast.success("Copied"))}>
            <Copy /> Copy
          </Button>
          <Button onClick={onDone}>Done</Button>
        </div>
      </>
    );
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{staff.hasSigninCode ? "Change" : "Set"} sign-in code for {staff.fullName}</DialogTitle>
        <DialogDescription>
          They sign in with {staff.phone} and this code. {staff.hasSigninCode && "The old code stops working and open sessions end."}
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Sign-in code (6 to 8 digits)">
          <div className="flex gap-2">
            <Input inputMode="numeric" autoComplete="off" required maxLength={8} value={code} autoFocus
                   onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} className="font-mono tracking-widest" />
            <Button type="button" variant="outline" onClick={() => { setCode(generateSigninCode()); setError(""); }}>
              <KeyRound /> Generate
            </Button>
          </div>
        </Field>
        <div className="flex items-center justify-end gap-3">
          <ErrorText>{error}</ErrorText>
          <Button type="submit">Save code</Button>
        </div>
      </form>
    </>
  );
}
