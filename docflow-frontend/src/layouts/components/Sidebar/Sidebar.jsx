// src/layouts/components/Sidebar/Sidebar.jsx

import { useEffect, useState } from "react";

import {
  Box,
  List,
  Typography,
  Collapse,
} from "@mui/material";

import ChatOutlinedIcon from "@mui/icons-material/ChatOutlined";

import {
  useLocation,
  useNavigate,
} from "react-router-dom";

import SidebarHeader from "./SidebarHeader";
import SidebarFooter from "./SidebarFooter";
import SidebarItem from "./SidebarItem";
import menuConfig from "./menuConfig";

import { useSidebar } from "../../../contexts/SidebarContext";

import {
  useAppDispatch,
  useAppSelector,
} from "../../../hooks";

import { fetchConversations } from "../../../features/conversations/thunk/conversationThunk";

function Sidebar() {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();
  const location = useLocation();

  const { collapsed } = useSidebar();

  const { user } = useAppSelector(
    (state) => state.auth
  );

  const { conversations } =
    useAppSelector(
      (state) => state.conversation
    );

  const [openConversations, setOpenConversations] =
    useState(true);

  useEffect(() => {
    if (conversations.length === 0) {
      dispatch(fetchConversations());
    }
  }, [
    dispatch,
    conversations.length,
  ]);

  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        bgcolor: "background.paper",
        borderRight: 1,
        borderColor: "divider",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <SidebarHeader />

      <List
        sx={{
          flex: 1,
          p: collapsed ? 1 : 2,
          overflowY: collapsed
            ? "hidden"
            : "auto",
        }}
      >
        {menuConfig.map((section) => {
          const visibleItems =
            section.items.filter((item) =>
              item.roles.includes(user?.role)
            );

          if (
            visibleItems.length === 0
          ) {
            return null;
          }

          return (
            <Box
              key={section.section}
              sx={{
                mb: collapsed
                  ? 1
                  : 2,
              }}
            >
              {!collapsed && (
                <Typography
                  variant="caption"
                  sx={{
                    px: 2,
                    mb: 1,
                    display: "block",
                    color:
                      "text.secondary",
                    fontWeight: 700,
                    letterSpacing: 1,
                    textTransform:
                      "uppercase",
                  }}
                >
                  {section.section}
                </Typography>
              )}

              {visibleItems.map(
                (item) => (
                  <SidebarItem
                    key={item.id}
                    icon={item.icon}
                    label={item.label}
                    active={
                      location.pathname ===
                      item.path
                    }
                    onClick={() =>
                      navigate(
                        item.path
                      )
                    }
                  />
                )
              )}

              {/* Conversations under PROCESSOR */}
              {section.section ===
                "PROCESSOR" && (
                <>
                  <SidebarItem
                    icon={
                      ChatOutlinedIcon
                    }
                    label="Conversations"
                    expandable
                    expanded={
                      openConversations
                    }
                    active={location.pathname.startsWith(
                      "/conversation"
                    )}
                    onClick={() =>
                      setOpenConversations(
                        (
                          prev
                        ) =>
                          !prev
                      )
                    }
                  />

                  {!collapsed && (
                    <Collapse
                      in={
                        openConversations
                      }
                    >
                      <Box
                        sx={{
                          ml: 3,
                          mt: 0.5,
                        }}
                      >
                        {conversations.length >
                        0 ? (
                          conversations.map(
                            (
                              conversation
                            ) => (
                              <SidebarItem
                                key={
                                  conversation.conversationId
                                }
                                icon={
                                  ChatOutlinedIcon
                                }
                                label={
                                  conversation.displayTitle
                                }
                                active={
                                  location.pathname ===
                                  `/conversation/${conversation.conversationId}`
                                }
                                sx={{
                                  pl: 2,
                                }}
                                onClick={() =>
                                  navigate(
                                    `/conversation/${conversation.conversationId}`
                                  )
                                }
                              />
                            )
                          )
                        ) : (
                          <Typography
                            variant="body2"
                            color="text.secondary"
                            sx={{
                              ml: 3,
                              mt: 1,
                              mb: 1,
                            }}
                          >
                            No conversations yet
                          </Typography>
                        )}
                      </Box>
                    </Collapse>
                  )}
                </>
              )}
            </Box>
          );
        })}
      </List>

      <SidebarFooter />
    </Box>
  );
}

export default Sidebar;