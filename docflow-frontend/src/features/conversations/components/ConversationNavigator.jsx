import { useMemo } from "react";

import {
  Box,
  Fade,
  Paper,
  Typography,
} from "@mui/material";

function ConversationNavigator({
  open,
  messages = [],
  activeMessageId,
  onNavigate,
  onMouseEnter,
  onMouseLeave,
  style = {},
}) {
  const questions = useMemo(
    () =>
      messages.filter(
        (message) =>
          message.role === "USER" &&
          message.type === "QUESTION" &&
          message.content?.trim()
      ),
    [messages]
  );

  return (
    <Fade in={open} timeout={180}>
      <Paper
        elevation={8}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        sx={{
          position: "absolute",
          right: 16,
          width: 360,
          maxHeight: 420,
          overflowY: "auto",
          borderRadius: 3,
          border: "1px solid",
          borderColor: "divider",
          bgcolor: "background.paper",
          zIndex: 30,
          boxShadow: 6,
          transition: "top .15s linear",

          "&::-webkit-scrollbar": {
            width: 6,
          },

          "&::-webkit-scrollbar-thumb": {
            bgcolor: "action.disabled",
            borderRadius: 99,
          },

          ...style,
        }}
      >
        {questions.map((message) => {
          const active =
            activeMessageId === message.id;

          return (
            <Box
              key={message.id}
              onClick={() =>
                onNavigate?.(message.id)
              }
              sx={{
                px: 2,
                py: 1.5,
                cursor: "pointer",
                transition: "all .15s ease",

                bgcolor: active
                  ? "action.selected"
                  : "transparent",

                "&:hover": {
                  bgcolor: "action.hover",
                },

                "&:not(:last-child)": {
                  borderBottom: "1px solid",
                  borderColor: "divider",
                },
              }}
            >
              <Typography
                variant="body2"
                sx={{
                  overflow: "hidden",
                  whiteSpace: "nowrap",
                  textOverflow: "ellipsis",
                  color: active
                    ? "primary.main"
                    : "text.primary",
                  fontWeight: active
                    ? 600
                    : 400,
                }}
              >
                {message.content}
              </Typography>
            </Box>
          );
        })}
      </Paper>
    </Fade>
  );
}

export default ConversationNavigator;