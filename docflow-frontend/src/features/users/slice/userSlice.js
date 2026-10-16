import { createSlice } from "@reduxjs/toolkit";

import {
  fetchUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
} from "../thunk/userThunk";

const initialState = {
  users: [],

  loading: false,
  error: null,

  page: 0,
  size: 10,
  total: 0,
  totalPages: 0,

  filters: {
    role: "",
  },

  selectedUser: null,

  dialog: {
    open: false,
    mode: "create", // create | edit | view
  },
};

const userSlice = createSlice({
  name: "users",

  initialState,

  reducers: {
    setPage(state, action) {
      state.page = action.payload;
    },

    setRowsPerPage(state, action) {
      state.size = action.payload;
      state.page = 0;
    },

    setRoleFilter(state, action) {
      state.filters.role = action.payload;
      state.page = 0;
    },

    clearFilters(state) {
      state.filters.role = "";
      state.page = 0;
    },

    openDialog(state, action) {
      state.dialog.open = true;
      state.dialog.mode = action.payload.mode;
      state.selectedUser = action.payload.user || null;
    },

    closeDialog(state) {
      state.dialog.open = false;
      state.selectedUser = null;
    },
  },

  extraReducers: (builder) => {
    builder

      /**
       * Fetch Users
       */
      .addCase(fetchUsers.pending, (state) => {
        state.loading = true;
        state.error = null;
      })

      .addCase(fetchUsers.fulfilled, (state, action) => {
        state.loading = false;

        state.users = action.payload.items;

        state.total = action.payload.total;

        state.page = action.payload.page;

        state.size = action.payload.rowsPerPage;

        state.totalPages = action.payload.totalPages;
      })

      .addCase(fetchUsers.rejected, (state, action) => {
        state.loading = false;

        state.error = action.payload;
      })

      /**
       * Create User
       */
      .addCase(createUser.fulfilled, (state, action) => {
        state.users.unshift(action.payload);

        state.total += 1;
      })

      /**
       * Update User
       */
      .addCase(updateUser.fulfilled, (state, action) => {
        const index = state.users.findIndex(
          (user) => user.userId === action.payload.userId
        );

        if (index !== -1) {
          state.users[index] = action.payload;
        }
      })

      /**
       * Delete User
       */
      .addCase(deleteUser.fulfilled, (state, action) => {
        state.users = state.users.filter(
          (user) => user.userId !== action.payload
        );

        state.total -= 1;
      })

      /**
       * Reset Password
       */
      .addCase(resetPassword.fulfilled, () => {
        // Nothing to update in state.
      });
  },
});

export const {
  setPage,
  setRowsPerPage,
  setRoleFilter,
  clearFilters,
  openDialog,
  closeDialog,
} = userSlice.actions;

export default userSlice.reducer;