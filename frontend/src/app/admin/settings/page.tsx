"use client";

import { BlueprintListWorkspace, blueprintWorkspaces } from "@/features/blueprint/blueprint-workspaces";

export default function AdminSettingsPage() {
  return <BlueprintListWorkspace config={blueprintWorkspaces.settings} />;
}
