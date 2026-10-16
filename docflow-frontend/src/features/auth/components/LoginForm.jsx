import { Alert, Box } from "@mui/material";
import { useForm } from "react-hook-form";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import {
  CheckboxInput,
  PasswordInput,
  SubmitButton,
  TextInput,
} from "../../../components/common/form";

import {
  useAppDispatch,
  useAppSelector,
} from "../../../hooks";

import { ROUTES } from "../../../router";
import { login } from "../thunk/authThunk";

function LoginForm() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const {
    loading,
    error,
    isAuthenticated,
    user,
  } = useAppSelector((state) => state.auth);

  const { control, handleSubmit } = useForm({
    defaultValues: {
      email: "",
      password: "",
      rememberMe: false,
    },
  });

  const getHomeRoute = (role) => {
    switch (role) {
      case "ADMIN":
        return ROUTES.DASHBOARD;

      case "INDEXER":
        return ROUTES.INDEXER;

      case "PROCESSOR":
        return ROUTES.UPLOAD;

      default:
        return ROUTES.LOGIN;
    }
  };

  useEffect(() => {
    if (isAuthenticated && user?.role) {
      navigate(
        getHomeRoute(user.role),
        {
          replace: true,
        }
      );
    }
  }, [
    isAuthenticated,
    user,
    navigate,
  ]);

  const onSubmit = (data) => {
    dispatch(
      login({
        email: data.email,
        password: data.password,
      })
    );
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit(onSubmit)}
    >
      {error && (
        <Alert
          severity="error"
          sx={{
            mb: 3,
            borderRadius: 2,
          }}
        >
          {error}
        </Alert>
      )}

      <TextInput
        control={control}
        name="email"
        label="Email Address"
      />

      <PasswordInput
        control={control}
        name="password"
        label="Password"
      />

      <Box
        sx={{
          mt: 1,
          mb: 2,
        }}
      >
        <CheckboxInput
          control={control}
          name="rememberMe"
          label="Remember Me"
        />
      </Box>

      <SubmitButton
        loading={loading}
        label={
          loading
            ? "Signing In..."
            : "Sign In"
        }
      />
    </Box>
  );
}

export default LoginForm;