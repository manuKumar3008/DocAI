import { createAsyncThunk } from "@reduxjs/toolkit";

import userService from "../services/userService";

/**
 * Fetch Users
 */
export const fetchUsers = createAsyncThunk(
  "users/fetchUsers",
  async (
    {
      page = 0,
      size = 10,
      role = "",
    },
    thunkAPI
  ) => {
    try {
      return await userService.getUsers({
        page,
        size,
        role,
      });
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
          "Unable to fetch users."
      );
    }
  }
);

/**
 * Create User
 */
export const createUser = createAsyncThunk(
  "users/createUser",
  async (payload, thunkAPI) => {
    try {
      return await userService.createUser(payload);
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
          "Unable to create user."
      );
    }
  }
);

/**
 * Update User
 */
export const updateUser = createAsyncThunk(
  "users/updateUser",
  async (
    {
      userId,
      payload,
    },
    thunkAPI
  ) => {
    try {
      return await userService.updateUser(
        userId,
        payload
      );
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
          "Unable to update user."
      );
    }
  }
);

/**
 * Delete User
 */
export const deleteUser = createAsyncThunk(
  "users/deleteUser",
  async (userId, thunkAPI) => {
    try {
      await userService.deleteUser(userId);
      return userId;
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
          "Unable to delete user."
      );
    }
  }
);

/**
 * Reset Password
 */
export const resetPassword = createAsyncThunk(
  "users/resetPassword",
  async (
    {
      userId,
      newPassword,
    },
    thunkAPI
  ) => {
    try {
      return await userService.resetPassword(
        userId,
        newPassword
      );
    } catch (error) {
      return thunkAPI.rejectWithValue(
        error.response?.data?.message ||
          "Unable to reset password."
      );
    }
  }
);