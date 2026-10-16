import { createSlice } from "@reduxjs/toolkit";

import {
  createConversation,
  loadConversationPage,
  fetchConversations,
} from "../thunk/conversationThunk";

const initialState = {
  conversationId: null,
  documents: [],
  messages: [],
  total: 0,
  page: 1,
  size: 20,
  totalPages: 0,
  conversations: [],
  conversationsLoading: false,
  loading: false,
  error: null,
};

const conversationSlice = createSlice({
  name: "conversation",

  initialState,

  reducers: {
    clearConversation(state) {
      state.conversationId = null;
      state.documents = [];
      state.messages = [];
      state.total = 0;
      state.page = 1;
      state.size = 20;
      state.totalPages = 0;
      state.error = null;
    },

    addMessage(state, action) {
      state.messages.push(action.payload);
    },

    updateMessage(state, action) {
      const { id, ...changes } = action.payload;

      const message = state.messages.find((m) => m.id === id);

      if (!message) return;

      Object.keys(changes).forEach((key) => {
        if (key !== "metadata") {
          message[key] = changes[key];
        }
      });

      if (changes.metadata) {
        message.metadata = {
          ...(message.metadata || {}),
          ...changes.metadata,
          translatedPages: {
            ...(message.metadata?.translatedPages || {}),
            ...(changes.metadata?.translatedPages || {}),
          },
        };

        
        

        
      }
    },

    removeMessage(state, action) {
      state.messages = state.messages.filter(
        (m) => m.id !== action.payload
      );
    },

    updateConversationTitle(state, action) {
      const {
        conversationId,
        displayTitle,
        fullTitle,
      } = action.payload;

      const conversation = state.conversations.find(
        (c) => c.conversationId === conversationId
      );

      if (conversation) {
        conversation.displayTitle = displayTitle;
        conversation.fullTitle = fullTitle;
      }
    },
  },

  extraReducers: (builder) => {
    builder
      .addCase(fetchConversations.pending, (state) => {
        state.conversationsLoading = true;
        state.error = null;
      })

      .addCase(fetchConversations.fulfilled, (state, action) => {
        state.conversationsLoading = false;
        state.conversations = action.payload.items;
      })

      .addCase(fetchConversations.rejected, (state, action) => {
        state.conversationsLoading = false;
        state.error =
          action.payload || "Failed to fetch conversations.";
      })

      .addCase(createConversation.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(createConversation.fulfilled, (state, action) => {
        state.loading = false;
        state.conversationId =
          action.payload.conversationId;
      })

      .addCase(createConversation.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      })

      .addCase(loadConversationPage.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(loadConversationPage.fulfilled, (state, action) => {
        state.loading = false;
        state.conversationId =
          action.payload.conversationId;
        state.documents =
          action.payload.documents;
        state.messages =
          action.payload.messages;
        state.total =
          action.payload.total;
        state.page =
          action.payload.page;
        state.size =
          action.payload.size;
        state.totalPages =
          action.payload.totalPages;
      })

      .addCase(loadConversationPage.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload;
      });
  },
});

export const {
  clearConversation,
  addMessage,
  updateMessage,
  removeMessage,
  updateConversationTitle,
} = conversationSlice.actions;

export default conversationSlice.reducer;