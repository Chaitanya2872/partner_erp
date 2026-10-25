import { useContext } from "react";
import { AuthContext, type AuthContextValue } from "@/context/AuthContext";

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return ctx;
}

/** `canRead("LEADS")`, `canWrite("ESTIMATES")`, `canPerform("PAYOUTS", "MARK_PAYOUT_PAID")`. */
export function usePermissions() {
  const { canRead, canWrite, canPerform } = useAuth();
  return { canRead, canWrite, canPerform };
}
