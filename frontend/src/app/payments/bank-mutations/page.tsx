"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function PaymentBankMutationsPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces["bank-mutations"]} />;
}
