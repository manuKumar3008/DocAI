// src/layouts/components/Sidebar/menuConfig.js

import DashboardOutlinedIcon from "@mui/icons-material/DashboardOutlined";
import UploadFileOutlinedIcon from "@mui/icons-material/UploadFileOutlined";
import Inventory2OutlinedIcon from "@mui/icons-material/Inventory2Outlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import AdminPanelSettingsOutlinedIcon from "@mui/icons-material/AdminPanelSettingsOutlined";
import GroupOutlinedIcon from "@mui/icons-material/GroupOutlined";
import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";

const menuConfig = [
  {
    section: "PROCESSOR",
    items: [
      {
        id: "upload",
        label: "Upload",
        path: "/upload",
        icon: UploadFileOutlinedIcon,
        roles: ["ADMIN", "PROCESSOR"],
      },
      {
        id: "document-library",
        label: "Document Library",
        path: "/document-library",
        icon: Inventory2OutlinedIcon,
        roles: ["ADMIN", "PROCESSOR"],
      },
    ],
  },
  {
    section: "INDEXER",
    items: [
      {
        id: "indexer",
        label: "Indexer",
        path: "/indexer",
        icon: FactCheckOutlinedIcon,
        roles: ["ADMIN", "INDEXER"],
      },
    ],
  },
  {
    section: "ADMIN",
    items: [
      {
        id: "dashboard",
        label: "Dashboard",
        path: "/dashboard",
        icon: DashboardOutlinedIcon,
        roles: ["ADMIN"],
      },
      {
        id: "administration",
        label: "Administration",
        path: "/administration",
        icon: AdminPanelSettingsOutlinedIcon,
        roles: ["ADMIN"],
      },
      {
        id: "users",
        label: "Users",
        path: "/users",
        icon: GroupOutlinedIcon,
        roles: ["ADMIN"],
      },
     
    ],
  },
];

export default menuConfig;