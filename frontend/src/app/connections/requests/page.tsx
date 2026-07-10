"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function ConnectionRequestsPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces["connection-requests"]} />;
}
