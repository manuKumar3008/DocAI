import { useState } from "react";
import { useDispatch } from "react-redux";

import {
    IconButton,
    Paper,
    TextField,
} from "@mui/material";

import SendRoundedIcon from "@mui/icons-material/SendRounded";

import { askQuestionThunk } from "../thunk/askQuestionThunk";

function ChatInput({ conversationId }) {
    const dispatch = useDispatch();

    const [question, setQuestion] = useState("");

    const handleSend = () => {
        //console.log("Send clicked");
        //console.log("Question:", question);
        //console.log("Conversation:", conversationId);

        const text = question.trim();

        if (!text || !conversationId) {
            return;
        }

        dispatch(
            askQuestionThunk(
                conversationId,
                text
            )
        );

        setQuestion("");
    };

    const handleKeyDown = (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            handleSend();
        }
    };

    return (
        <Paper
            elevation={0}
            sx={{
                display: "flex",
                alignItems: "flex-end",
                gap: 1,
                px: 2,
                py: 1.5,
                borderRadius: 3,
                border: "1px solid",
                borderColor: "divider",
                transition: "all .2s ease",

                "&:hover": {
                    borderColor: "primary.main",
                },

                "&:focus-within": {
                    borderColor: "primary.main",
                    boxShadow: (theme) =>
                        `0 0 0 3px ${theme.palette.primary.main}15`,
                },
            }}
        >
            <TextField
                fullWidth
                multiline
                maxRows={5}
                variant="standard"
                placeholder="Ask anything about your document..."
                value={question}
                onChange={(e) =>
                    setQuestion(e.target.value)
                }
                onKeyDown={handleKeyDown}
                InputProps={{
                    disableUnderline: true,
                }}
            />

            <IconButton
                color="primary"
                disabled={!question.trim()}
                onClick={handleSend}
                sx={{
                    width: 44,
                    height: 44,
                    bgcolor: "primary.main",
                    color: "white",

                    "&:hover": {
                        bgcolor: "primary.dark",
                    },

                    "&.Mui-disabled": {
                        bgcolor: "action.disabledBackground",
                        color: "action.disabled",
                    },
                }}
            >
                <SendRoundedIcon />
            </IconButton>
        </Paper>
    );
}

export default ChatInput;