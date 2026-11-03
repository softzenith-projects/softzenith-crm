"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useState, type FormEvent, type ReactNode } from "react";
import {
  Building2, ChevronsUpDown, ExternalLink, KeyRound, LayoutDashboard, LogOut, Menu, Plus, Search, Users, UserRound, X,
  type LucideIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Initials, initials } from "@/components/common";
import { MySigninCodeForm } from "@/components/MySigninCodeForm";
import { api, type Schemas } from "@/lib/api";
import { signOut } from "@/lib/auth";
import { session } from "@/lib/session";
import { cn } from "@/lib/utils";

type Me = Schemas["MeResponse"];

const MeContext = createContext<Me | null>(null);

/** The signed-in staff user. Only available inside the staff area. */
export function useMe(): Me {
  const me = useContext(MeContext);
  if (!me) throw new Error("useMe outside AppShell");
  return me;
}

/** UI hints only; the backend enforces every permission. */
export const can = (me: Me, permission: string) => me.permissions?.includes(permission) ?? false;

/** Staff, roles, branches and assignment. Without it a business is run by its Admins alone and none of that is shown. */
export const TEAM = "TEAM_AND_STUDENTS";

/** Whether the business's plan includes a feature. UI hint only, like `can`. */
export const hasFeature = (me: Me, feature: string) => me.features?.includes(feature) ?? false;

type NavItem = { href: string; label: string; icon: LucideIcon; permission: string; feature?: string };

/** Only modules that exist are listed; each is shown when the user holds its permission and the plan includes it. */
const NAV: { title?: string; items: NavItem[] }[] = [
  {
    items: [
      { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, permission: "REPORTS_VIEW" },
      { href: "/leads", label: "Leads", icon: UserRound, permission: "LEAD_VIEW" },
    ],
  },
  {
    title: "Administration",
    items: [
      { href: "/admin/staff", label: "Staff", icon: Users, permission: "USER_VIEW", feature: TEAM },
      { href: "/admin/branches", label: "Branches", icon: Building2, permission: "BRANCH_MANAGE", feature: TEAM },
    ],
  },
];

export default function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [me, setMe] = useState<Me | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [changingCode, setChangingCode] = useState(false);

  useEffect(() => {
    if (!session.token()) {
      router.replace("/login");
      return;
    }
    api.GET("/api/v1/me").then(({ data }) => {
      if (data) setMe(data);
      else router.replace("/login");
    });
  }, [router]);

  if (!me) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading…</div>
    );
  }

  const logout = async () => {
    await signOut();
    session.clear();
    router.replace("/login");
  };

  const search = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const q = String(new FormData(e.currentTarget).get("q") ?? "").trim();
    router.push(q ? `/leads?q=${encodeURIComponent(q)}` : "/leads");
  };

  const sidebar = (
    <aside className="flex h-full w-64 flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex items-center gap-3 px-5 py-5">
        <span className="flex size-10 items-center justify-center rounded-xl bg-gradient-to-br from-sky-400 to-blue-600 text-lg font-bold text-white">
          {initials(me.tenant?.name).charAt(0)}
        </span>
        <div className="min-w-0">
          <p className="truncate font-bold tracking-wide uppercase">{me.tenant?.name}</p>
          <p className="text-xs text-sidebar-foreground/60">Operations</p>
        </div>
      </div>

      <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-2">
        {NAV.map((group, i) => {
          const items = group.items.filter((item) => can(me, item.permission) && (!item.feature || hasFeature(me, item.feature)));
          if (items.length === 0) return null;
          return (
            <div key={i}>
              {group.title && (
                <p className="mb-1 px-3 text-[11px] font-semibold tracking-wider text-sidebar-foreground/50 uppercase">
                  {group.title}
                </p>
              )}
              <ul className="space-y-1">
                {items.map(({ href, label, icon: Icon }) => {
                  const active = path.startsWith(href);
                  return (
                    <li key={href}>
                      <Link
                        href={href}
                        onClick={() => setMenuOpen(false)}
                        aria-current={active ? "page" : undefined}
                        className={cn(
                          "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                          active
                            ? "bg-sidebar-primary text-sidebar-primary-foreground shadow-sm"
                            : "text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                        )}
                      >
                        <Icon className="size-[18px]" />
                        {label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
      </nav>

      {hasFeature(me, TEAM) && (
        <div className="border-t border-sidebar-border px-5 py-3 text-xs">
          <p className="text-sidebar-foreground/60">Branch</p>
          <p className="flex items-center gap-2 font-semibold">
            <span className="size-2 rounded-full bg-emerald-400" />
            {me.dataScope === "ALL" ? "All branches" : me.branch?.name ?? "No branch"}
          </p>
        </div>
      )}

      <DropdownMenu>
        <DropdownMenuTrigger className="m-3 flex items-center gap-3 rounded-lg p-2 text-left outline-none hover:bg-sidebar-accent focus-visible:ring-2 focus-visible:ring-sidebar-ring">
          <Initials name={me.fullName} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{me.fullName}</p>
            <p className="truncate text-xs text-sidebar-foreground/60">{me.role?.name}</p>
          </div>
          <ChevronsUpDown className="size-4 text-sidebar-foreground/60" />
        </DropdownMenuTrigger>
        <DropdownMenuContent side="top" className="w-56">
          <DropdownMenuGroup><DropdownMenuLabel>{me.phone}</DropdownMenuLabel></DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => window.open(`/enquiry/${me.tenant?.slug}`, "_blank")}>
            <ExternalLink /> Public enquiry form
          </DropdownMenuItem>
          {can(me, "USER_MANAGE") && (
            <DropdownMenuItem onClick={() => setChangingCode(true)}>
              <KeyRound /> Change my sign-in code
            </DropdownMenuItem>
          )}
          <DropdownMenuItem onClick={logout} variant="destructive">
            <LogOut /> Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </aside>
  );

  return (
    <MeContext.Provider value={me}>
      <Dialog open={changingCode} onOpenChange={setChangingCode}>
        <DialogContent className="sm:max-w-md">
          {changingCode && <MySigninCodeForm userId={me.userId!} phone={me.phone} onSaved={logout} />}
        </DialogContent>
      </Dialog>
      <div className="flex min-h-screen">
        <div className="sticky top-0 hidden h-screen lg:block">{sidebar}</div>
        {menuOpen && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <div className="absolute inset-0 bg-black/40" onClick={() => setMenuOpen(false)} />
            <div className="relative h-full w-fit">{sidebar}</div>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b bg-background/90 px-4 backdrop-blur lg:px-8">
            <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu">
              {menuOpen ? <X /> : <Menu />}
            </Button>
            {can(me, "LEAD_VIEW") && (
              <form onSubmit={search} className="relative mx-auto w-full max-w-xl">
                <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input name="q" placeholder="Search leads by name, mobile, email or lead #" className="h-10 bg-card pl-9" />
              </form>
            )}
            {can(me, "LEAD_CREATE") && (
              <Button size="lg" className="h-10 px-4" onClick={() => router.push("/leads?new=1")}>
                <Plus /> <span className="hidden sm:inline">Add lead</span>
              </Button>
            )}
          </header>
          <main className="flex-1 px-4 py-6 lg:px-8">{children}</main>
        </div>
      </div>
    </MeContext.Provider>
  );
}
