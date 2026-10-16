// src/pages/Users/UsersPage.jsx

import { useEffect } from "react";

import {
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Typography,
  Box,
} from "@mui/material";
import ResetPasswordForm from "../../features/users/components/ResetPasswordForm";
import RefreshIcon from "@mui/icons-material/Refresh";
import AddIcon from "@mui/icons-material/Add";

import { useForm } from "react-hook-form";

import {
  useAppDispatch,
  useAppSelector,
} from "../../hooks";

import {
  fetchUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
} from "../../features/users/thunk/userThunk";

import {
  setPage,
  setRowsPerPage,
  setRoleFilter,
  openDialog,
  closeDialog,
} from "../../features/users/slice/userSlice";

import {
  PageLayout,
  PageHeader,
  PageToolbar,
} from "../../components/page";

import {
  AppTable,
  TableLayout,
} from "../../components/table";

import { AppDialog } from "../../components/dialog";

import UserForm from "../../features/users/components/UserForm";

import { getUserColumns } from "../../features/users/columns/userColumns";

function UsersPage() {
  const dispatch = useAppDispatch();

  const {
    users,
    loading,
    total,
    page,
    size,
    filters,
    dialog,
    selectedUser,
  } = useAppSelector(
    (state) => state.users
  );

  const {
    control,
    handleSubmit,
    reset,
  } = useForm({
    defaultValues: {
      name: "",
      email: "",
      password: "",
      newPassword: "",
      confirmPassword: "",
      role: "PROCESSOR",
      active: true,
    },
  });

  useEffect(() => {
    dispatch(
      fetchUsers({
        page,
        size,
        role: filters.role,
      })
    );
  }, [
    dispatch,
    page,
    size,
    filters.role,
  ]);

  const handleRefresh = () => {
    dispatch(
      fetchUsers({
        page,
        size,
        role: filters.role,
      })
    );
  };

  const handleOpenCreate = () => {
    reset({
      name: "",
      email: "",
      password: "",
      role: "PROCESSOR",
      active: true,
    });

    dispatch(
      openDialog({
        mode: "create",
      })
    );
  };

  const handleEdit = (user) => {
    reset({
      name: user.name,
      email: user.email,
      password: "",
      role: user.role,
      active: user.active,
    });

    dispatch(
      openDialog({
        mode: "edit",
        user,
      })
    );
  };

  const handleDelete = (user) => {
    dispatch(
      openDialog({
        mode: "delete",
        user,
      })
    );
  };

  const handleResetPassword = (user) => {

    reset({
      newPassword: "",
    });

    dispatch(
      openDialog({
        mode: "resetPassword",
        user,
      })
    );

  };

  const handleToggleStatus = (user) => {
    //console.log("Toggle Status", user);
  };

  const handleClose = () => {
    dispatch(closeDialog());
  };

  const onSubmit = async (data) => {
    switch (dialog.mode) {
      case "create":
        await dispatch(createUser(data));
        break;

      case "edit":
        await dispatch(
          updateUser({
            userId: selectedUser.userId,
            payload: {
              name: data.name,
              role: data.role,
              active: data.active,
            },
          })
        );
        break;

      case "delete":
        await dispatch(
          deleteUser(selectedUser.userId)
        );
        break;

      case "resetPassword":
        await dispatch(
          resetPassword({
            userId: selectedUser.userId,
            payload: {
              newPassword: data.newPassword,
            },
          })
        );
        break;

      default:
        return;
    }

    dispatch(closeDialog());

    handleRefresh();
  };
  const dialogConfig = {
    create: {
      title: "Add User",
      submitLabel: "Create User",
    },
    edit: {
      title: "Edit User",
      submitLabel: "Save Changes",
    },
    delete: {
      title: "Delete User",
      submitLabel: "Delete User",
    },
    resetPassword: {
      title: "Reset Password",
      submitLabel: "Reset Password",
    },
  };

  const currentDialog =
    dialogConfig[dialog.mode];

  return (
    <PageLayout>
      <PageHeader
        title="Users"
        subtitle="Manage platform users"
      />
      <Box sx={{ mb: 3 }}>
        <PageToolbar
          left={
            <FormControl
              size="small"
              sx={{ minWidth: 180 }}
            >
              <InputLabel>
                Role
              </InputLabel>

              <Select
                label="Role"
                value={filters.role}
                onChange={(event) =>
                  dispatch(
                    setRoleFilter(
                      event.target.value
                    )
                  )
                }
              >
                <MenuItem value="">
                  All Roles
                </MenuItem>

                <MenuItem value="ADMIN">
                  Administrator
                </MenuItem>

                <MenuItem value="INDEXER">
                  Indexer
                </MenuItem>

                <MenuItem value="PROCESSOR">
                  Processor
                </MenuItem>
              </Select>
            </FormControl>
          }
          right={
            <>
              <Button
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={handleRefresh}
                disabled={loading}
              >
                Refresh
              </Button>

              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={handleOpenCreate}
              >
                Add User
              </Button>
            </>
          }
        />
      </Box>

      <TableLayout>
        <AppTable
          columns={getUserColumns({
            onEdit: handleEdit,
            onDelete: handleDelete,
            onResetPassword:
              handleResetPassword,
            onToggleStatus:
              handleToggleStatus,
          })}
          rows={users}
          loading={loading}
          emptyMessage="No users found."
          getRowId={(row) => row.userId}
          pagination={{
            page,
            rowsPerPage: size,
            total,
            onPageChange: (_, value) =>
              dispatch(setPage(value)),
            onRowsPerPageChange: (
              event
            ) =>
              dispatch(
                setRowsPerPage(
                  Number(
                    event.target.value
                  )
                )
              ),
          }}
        />
      </TableLayout>

      <AppDialog
        open={dialog.open}
        title={currentDialog.title}
        onClose={handleClose}
        loading={loading}
        actions={
          <>
            <Button
              variant="outlined"
              onClick={handleClose}
            >
              Cancel
            </Button>

            <Button
              variant="contained"
              color={
                dialog.mode === "delete"
                  ? "error"
                  : "primary"
              }
              onClick={
                dialog.mode === "delete"
                  ? () => onSubmit()
                  : handleSubmit(onSubmit)
              }
              disabled={loading}
            >
              {currentDialog.submitLabel}
            </Button>
          </>
        }
      >
        {dialog.mode === "delete" ? (
          <Typography>
            Are you sure you want to
            delete{" "}
            <strong>
              {selectedUser?.name}
            </strong>
            ?
            <br />
            <br />
            This action cannot be
            undone.
          </Typography>
        ) : dialog.mode === "resetPassword" ? (
          <ResetPasswordForm
            control={control}
          />
        ) : (
          <UserForm
            control={control}
            mode={dialog.mode}
          />
        )}
      </AppDialog>
    </PageLayout>
  );
}

export default UsersPage;