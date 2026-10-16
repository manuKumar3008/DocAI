import { createAsyncThunk } from "@reduxjs/toolkit";

import conversationService from "../services/conversationService";

const safeStorage = {
  set(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      //console.warn("localStorage set failed:", error);
    }
  },

  remove(key) {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      //console.warn("localStorage remove failed:", error);
    }
  },
};

export const createConversation = createAsyncThunk(
  "conversation/createConversation",
  async (payload, { rejectWithValue }) => {
    try {
      const response =
        await conversationService.createConversation(payload);

      safeStorage.set(
        "conversationId",
        response.conversationId
      );

      return response;
    } catch (error) {
      return rejectWithValue(
        error.response?.data?.message ||
          "Failed to create conversation."
      );
    }
  }
);

export const loadConversationPage = createAsyncThunk(
  "conversation/loadConversationPage",
  async (conversationId, { rejectWithValue }) => {
    try {
      const conversation =
        await conversationService.getConversationMessages(
          conversationId
        );

      const documents = await Promise.all(
        (conversation.documentIds || []).map((documentId) =>
          conversationService.getDocument(documentId)
        )
      );

      // Normalize history messages for UI
      const messages = (conversation.messages || [])
        .filter((message) => {
          const type = String(
            message.type || "QUESTION"
          ).toUpperCase();

          // Ignore non-conversational history
          return ![
            "TRANSLATION",
            "SUMMARY",
            "METADATA",
          ].includes(type);
        })
        .map((message) => {
          const role = String(
            message.role || ""
          ).toUpperCase();

          return {
            id:
              message.id ||
              message.messageId ||
              `${message.sequence}-${role}`,

            role,

            type: "QUESTION",

            content: message.content || "",

            loading: false,

            createdAt:
              message.createdAt ||
              new Date().toISOString(),

            metadata:
              role === "ASSISTANT"
                ? {
                    conversationId:
                      conversation.conversationId,

                    sequence:
                      message.sequence,

                    references:
                      message.references ?? [],

                    followUpQuestions:
                      message.suggestedFollowUps ??
                      [],
                  }
                : {},
          };
        });

      return {
        conversationId:
          conversation.conversationId,
        documents,
        messages,
        total: conversation.total,
        page: conversation.page,
        size: conversation.size,
        totalPages:
          conversation.totalPages,
      };
    } catch (error) {
      safeStorage.remove("conversationId");

      return rejectWithValue(
        error.response?.data?.message ||
          "Failed to load conversation."
      );
    }
  }
);

export const fetchConversations = createAsyncThunk(
  "conversation/fetchConversations",
  async (_, { rejectWithValue }) => {
    try {
      const response =
        await conversationService.getConversations();

      const conversations = await Promise.all(
        response.items.map(async (conversation) => {
          try {
            const details =
              await conversationService.getConversationMessages(
                conversation.conversationId
              );

            const firstUserMessage =
              details.messages?.find(
                (message) =>
                  String(message.role).toUpperCase() ===
                  "USER"
              );

            const fullTitle =
              firstUserMessage?.content ||
              conversation.title ||
              "Untitled Conversation";

            const displayTitle =
              fullTitle.length > 45
                ? `${fullTitle
                    .substring(0, 45)
                    .trim()}...`
                : fullTitle;

            return {
              ...conversation,
              displayTitle,
              fullTitle,
            };
          } catch (error) {
            

            return {
              ...conversation,
              displayTitle:
                conversation.title ||
                "Untitled Conversation",
              fullTitle:
                conversation.title ||
                "Untitled Conversation",
            };
          }
        })
      );

      return {
        ...response,
        items: conversations,
      };
    } catch (error) {
      

      return rejectWithValue(
        error.response?.data?.message ||
          "Failed to fetch conversations."
      );
    }
  }
);