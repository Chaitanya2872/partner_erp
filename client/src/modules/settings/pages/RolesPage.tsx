import { PageContainer, PageHeader } from "@/components/layout/Page";
import { RoleManagement } from "../components/RoleManagement";

export function RolesPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Role management"
        description="The three system roles cannot be added or removed. Renaming a role changes its label everywhere; users and permissions are unaffected."
      />
      <RoleManagement />
    </PageContainer>
  );
}
