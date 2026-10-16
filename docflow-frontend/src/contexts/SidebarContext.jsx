import {
  createContext,
  useContext,
  useState,
} from "react";

const SidebarContext = createContext(null);

export function SidebarProvider({
  children,
}) {
  /**
   * Mobile / Tablet Drawer
   */
  const [open, setOpen] =
    useState(false);

  /**
   * Desktop Sidebar
   */
  const [collapsed, setCollapsed] =
    useState(false);

  return (
    <SidebarContext.Provider
      value={{
        /**
         * Drawer
         */
        open,
        openSidebar: () =>
          setOpen(true),
        closeSidebar: () =>
          setOpen(false),
        toggleSidebar: () =>
          setOpen((prev) => !prev),

        /**
         * Desktop Collapse
         */
        collapsed,
        expandSidebar: () =>
          setCollapsed(false),
        collapseSidebar: () =>
          setCollapsed(true),
        toggleCollapsed: () =>
          setCollapsed((prev) => !prev),
      }}
    >
      {children}
    </SidebarContext.Provider>
  );
}

export function useSidebar() {
  const context =
    useContext(SidebarContext);

  if (!context) {
    throw new Error(
      "useSidebar must be used inside SidebarProvider"
    );
  }

  return context;
}