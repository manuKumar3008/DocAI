import { configureStore } from "@reduxjs/toolkit";

import authReducer from "../features/auth/slices/authSlice";
import dashboardReducer from "../features/dashboard/slice/dashboardSlice";
import usersReducer from "../features/users/slice/userSlice";
import uploadReducer from "../features/upload/slice/uploadSlice";
import indexerReducer from "../features/indexer/slice/indexerSlice";
import documentLibraryReducer from "../features/document-library/slice/documentLibrarySlice";
import conversationReducer from "../features/conversations/slice/conversationSlice";
import administrationReducer from "../features/administration/slice/administrationSlice";
export const store = configureStore({
  reducer: {
    auth: authReducer,
    dashboard: dashboardReducer,
    users: usersReducer,
    upload: uploadReducer,
    indexer: indexerReducer,
    documentLibrary: documentLibraryReducer,
    conversation: conversationReducer,
    administration: administrationReducer,


  },
});