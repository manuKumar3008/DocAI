import { nanoid } from "@reduxjs/toolkit";

import {
    addMessage,
    updateMessage,
} from "../slice/conversationSlice";

import { askQuestion } from "../services/conversationApi";

export const askQuestionThunk =
    (conversationId, question) =>
    async (dispatch) => {
        if (!conversationId || !question?.trim()) {
            return;
        }

        // Add user message
        dispatch(
            addMessage({
                id: nanoid(),
                role: "USER",
                type: "QUESTION",
                content: question,
                createdAt: new Date().toISOString(),
            })
        );

        const aiMessageId = nanoid();

        // Add loading assistant message
        dispatch(
            addMessage({
                id: aiMessageId,
                role: "ASSISTANT",
                type: "QUESTION",
                content: "",
                loading: true,
                createdAt: new Date().toISOString(),
            })
        );

        try {
            const response = await askQuestion(conversationId, {
                question,
            });

            if (!response.success) {
                throw new Error("Question answering failed");
            }

            dispatch(
                updateMessage({
                    id: aiMessageId,
                    type: "QUESTION",
                    loading: false,
                    content: response.data.answer,
                    metadata: {
                        conversationId: response.data.conversationId,
                        question: response.data.question,
                        sequence: response.data.sequence,
                        references:
                            response.data.references ?? [],
                        followUpQuestions:
                            response.data.suggestedFollowUps ?? [],
                    },
                })
            );
        } catch (error) {
            //console.error("Ask Question API Error:", error);

            dispatch(
                updateMessage({
                    id: aiMessageId,
                    type: "QUESTION",
                    loading: false,
                    content:
                        "Sorry, I couldn't answer your question.",
                    metadata: {
                        references: [],
                        followUpQuestions: [],
                    },
                })
            );
        }
    };