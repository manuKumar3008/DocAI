import { Outlet } from "react-router-dom";
import AppShell from "./components/AppShell/AppShell";

function DashboardLayout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}

export default DashboardLayout;