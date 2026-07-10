"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function AccountingAssetsPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces.assets} />;
}
