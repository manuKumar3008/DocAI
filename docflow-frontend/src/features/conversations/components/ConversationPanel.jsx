import { useDispatch } from "react-redux";

import {
    Box,
    Divider,
    Paper,
    Stack,
} from "@mui/material";

import ChatHeader from "./ChatHeader";
import EmptyConversation from "./EmptyConversation";
import MessageList from "./MessageList";
import QuickActions from "./QuickActions";
import ChatInput from "./ChatInput";

import { askQuestionThunk } from "../thunk/askQuestionThunk";

function ConversationPanel({
    conversationId,
    loading,
    documents,
    messages,
    selectedDocument,
    onDocumentSelect,
}) {
    const dispatch = useDispatch();

    const handleFollowUpClick = (question) => {
        if (!conversationId || !question?.trim()) {
            return;
        }

        dispatch(
            askQuestionThunk(
                conversationId,
                question
            )
        );
    };

    return (
        <Paper
            elevation={0}
            sx={{
                flex: 1,
                height: "100%",
                minHeight: 0,
                display: "flex",
                flexDirection: "column",
                borderRadius: 4,
                overflow: "hidden",
                border: "1px solid",
                borderColor: "divider",
                bgcolor: "background.paper",
            }}
        >
            <ChatHeader
                conversationId={conversationId}
                documents={documents}
                selectedDocument={selectedDocument}
                onDocumentSelect={onDocumentSelect}
            />

            <Box
                sx={{
                    flex: 1,
                    minHeight: 0,
                    display: "flex",
                    flexDirection: "column",
                    overflow: "hidden",
                }}
            >
                {messages.length === 0 ? (
                    <EmptyConversation />
                ) : (
                    <MessageList
                        loading={loading}
                        messages={messages}
                        onFollowUpClick={handleFollowUpClick}
                    />
                )}
            </Box>

            <Divider />

            <Box
                sx={{
                    flexShrink: 0,
                    p: 2,
                    bgcolor: "background.paper",
                }}
            >
                <Stack spacing={1.5}>
                    <QuickActions
                        selectedDocument={selectedDocument}
                    />

                    <ChatInput
                        conversationId={conversationId}
                    />
                </Stack>
            </Box>
        </Paper>
    );
}

export default ConversationPanel;