"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function ReceivableInstallmentsPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces.installments} />;
}
