import {
  Box,
  Divider,
  Typography,
} from "@mui/material";

import FieldRenderer from "./FieldRenderer";

function DynamicMetadataForm({
  control,
  mandatoryFields = [],
  optionalFields = [],
}) {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 3,
      }}
    >
      {!!mandatoryFields.length && (
        <>
          <Typography
            variant="subtitle1"
            fontWeight={600}
          >
            Mandatory Fields
          </Typography>

          {mandatoryFields.map(
            (field) => (
              <FieldRenderer
                key={field}
                control={control}
                field={field}
              />
            )
          )}
        </>
      )}

      {!!optionalFields.length && (
        <>
          <Divider />

          <Typography
            variant="subtitle1"
            fontWeight={600}
          >
            Optional Fields
          </Typography>

          {optionalFields.map(
            (field) => (
              <FieldRenderer
                key={field}
                control={control}
                field={field}
              />
            )
          )}
        </>
      )}
    </Box>
  );
}

export default DynamicMetadataForm;