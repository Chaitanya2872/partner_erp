import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

export function MainLayout() {
  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <Header />
        <Outlet />
      </main>
    </div>
  );
}
