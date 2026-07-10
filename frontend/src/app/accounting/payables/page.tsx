"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function AccountingPayablesPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces.payables} />;
}
