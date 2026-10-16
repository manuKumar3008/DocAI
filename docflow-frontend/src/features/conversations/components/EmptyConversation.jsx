import {
  Box,
  Typography,
} from "@mui/material";

import ChatOutlinedIcon from "@mui/icons-material/ChatOutlined";

function EmptyConversation() {
  return (
    <Box
      sx={{
        flex: 1,
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: 2,
        px: 4,
        textAlign: "center",
      }}
    >
      <ChatOutlinedIcon
        sx={{
          fontSize: 72,
          color: "primary.main",
        }}
      />

      <Typography
        variant="h5"
        fontWeight={700}
      >
        Start a Conversation
      </Typography>

      <Typography
        color="text.secondary"
        maxWidth={450}
      >
        Ask questions, summarize documents,
        or translate content using your
        selected documents.
      </Typography>
    </Box>
  );
}

export default EmptyConversation;