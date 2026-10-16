// src/features/users/components/UserForm.jsx

import { Box } from "@mui/material";

import {
  TextInput,
  PasswordInput,
  SelectInput,
} from "../../../components/common/form";

function UserForm({
  control,
  mode = "create",
}) {
  const isView = mode === "view";
  const isCreate = mode === "create";
  const isEdit = mode === "edit";

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 3,
        mt: 1,
      }}
    >
      <TextInput
        control={control}
        name="name"
        label="Full Name"
        disabled={isView}
      />

      <TextInput
        control={control}
        name="email"
        label="Email Address"
        disabled={isView || isEdit}
      />

      {isCreate && (
        <PasswordInput
          control={control}
          name="password"
          label="Password"
        />
      )}

      <SelectInput
        control={control}
        name="role"
        label="Role"
        disabled={isView}
        options={[
          {
            label: "Administrator",
            value: "ADMIN",
          },
          {
            label: "Indexer",
            value: "INDEXER",
          },
          {
            label: "Processor",
            value: "PROCESSOR",
          },
        ]}
      />

      {!isCreate && (
        <SelectInput
          control={control}
          name="active"
          label="Status"
          disabled={isView}
          options={[
            {
              label: "Active",
              value: true,
            },
            {
              label: "Inactive",
              value: false,
            },
          ]}
        />
      )}
    </Box>
  );
}

export default UserForm;