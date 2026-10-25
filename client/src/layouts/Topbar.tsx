import { Link, useNavigate } from "react-router";
import { ChevronDown, KeyRound, LogOut, Menu, Monitor, Moon, Sun } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { useTheme } from "@/hooks/useTheme";
import { useBreadcrumbs } from "@/app/breadcrumbs";
import { Breadcrumbs } from "@/components/navigation/Breadcrumbs";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/DropdownMenu";
import { THEME_OPTIONS, type ThemePreference } from "@/lib/theme";

interface TopbarProps {
  /** Present on phones: opens the navigation drawer. */
  onOpenNavigation?: () => void;
  navigationOpen?: boolean;
}

export function Topbar({ onOpenNavigation, navigationOpen }: TopbarProps) {
  const crumbs = useBreadcrumbs();
  return (
    <header className="no-print sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-border bg-background/90 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/75 sm:px-5">
      {onOpenNavigation ? (
        <Button
          variant="ghost"
          size="icon"
          onClick={onOpenNavigation}
          aria-label="Open navigation"
          aria-expanded={navigationOpen}
          aria-controls="mobile-navigation"
          className="-ml-1"
        >
          <Menu aria-hidden="true" />
        </Button>
      ) : null}
      <Breadcrumbs items={crumbs} className="flex-1" />
      <div className="ml-auto flex items-center gap-1">
        <ThemeMenu />
        <UserMenu />
      </div>
    </header>
  );
}

const THEME_ICONS: Record<ThemePreference, typeof Sun> = { light: Sun, dark: Moon, system: Monitor };

export function ThemeMenu() {
  const { preference, setPreference } = useTheme();
  const Icon = THEME_ICONS[preference];
  const current = THEME_OPTIONS.find((o) => o.value === preference)?.label ?? "System";
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Theme: ${current}`}>
          <Icon aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="min-w-40">
        <DropdownMenuLabel>Theme</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={preference} onValueChange={(value) => setPreference(value as ThemePreference)}>
          {THEME_OPTIONS.map((option) => {
            const OptionIcon = THEME_ICONS[option.value];
            return (
              <DropdownMenuRadioItem key={option.value} value={option.value}>
                <OptionIcon aria-hidden="true" /> {option.label}
              </DropdownMenuRadioItem>
            );
          })}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Account menu: name, email and the role display name from the database (updates when a role is renamed). */
export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  const signOut = async () => {
    await logout().catch(() => undefined);
    toast.success("Signed out.");
    navigate("/login", { replace: true });
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={`Account menu for ${user.fullName}`}
          className="flex h-10 items-center gap-2 rounded-md px-1.5 text-left hover:bg-hover focus-visible:outline-2 focus-visible:outline-ring sm:px-2"
        >
          <Avatar name={user.fullName} size="sm" />
          <span className="hidden min-w-0 leading-tight md:block">
            <span className="block max-w-40 truncate text-sm font-semibold">{user.fullName}</span>
            <span className="block max-w-40 truncate text-xs text-muted-foreground" data-testid="topbar-role">
              {user.role.displayName}
            </span>
          </span>
          <ChevronDown className="hidden size-4 text-muted-foreground md:block" aria-hidden="true" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-64">
        <div className="px-2.5 py-2">
          <div className="truncate text-sm font-semibold">{user.fullName}</div>
          <div className="truncate text-xs text-muted-foreground">{user.email}</div>
          <div className="mt-1.5 inline-flex rounded-full bg-secondary px-2 py-0.5 text-xs font-semibold">{user.role.displayName}</div>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/account/password">
            <KeyRound aria-hidden="true" /> Change password
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem destructive onSelect={() => void signOut()}>
          <LogOut aria-hidden="true" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
