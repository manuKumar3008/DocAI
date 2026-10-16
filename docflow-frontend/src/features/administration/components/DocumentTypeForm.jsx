import {
  Box,
  Paper,
  TextField,
  Typography,
  Grid,
} from "@mui/material";
import ChipInput from "./ChipInput";

export default function DocumentTypeForm({
  mode = "create",
  values,
  errors = {},
  onChange,
}) {
  const handleFieldChange = (field) => (event) => {
    onChange((prev) => ({
      ...prev,
      [field]: event.target.value,
    }));
  };

  const handleChipChange = (field) => (newValue) => {
    onChange((prev) => ({
      ...prev,
      [field]: newValue,
    }));
  };

  return (
    <>
      <Paper
      >


        <Grid container spacing={3} sx={{ mb: 3 }}>
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              required
              label="Document Type"
              placeholder="e.g. CLAIM_DOCUMENT"
              value={values.documentType}
              onChange={handleFieldChange("documentType")}
              disabled={mode === "edit"}
              error={!!errors.documentType}
             
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              required
              label="Display Name"
              placeholder="e.g. Claim Document"
              value={values.displayName}
              onChange={handleFieldChange("displayName")}
              error={!!errors.displayName}
              
            />
          </Grid>
        </Grid>



        <TextField
          fullWidth
          multiline
          minRows={4}
          label="Description"
          placeholder="Provide a brief description of this document type..."
          value={values.description}
          onChange={handleFieldChange("description")}
        />
      </Paper>

      <Paper

      >
        <Box sx={{ mb: 4 }}>
          <Typography
            variant="subtitle1"
            fontWeight={600}
            sx={{ mb: 1.5 }}
          >

          </Typography>

          <ChipInput
            required
            value={values.mandatoryFields}
            onChange={handleChipChange("mandatoryFields")}
            placeholder="Mandatory Fields*"
          />
        </Box>

        <Box>
          <Typography
            variant="subtitle1"
            fontWeight={600}
            sx={{ mb: 1.5 }}
          >

          </Typography>

          <ChipInput
            required
            value={values.optionalFields}
            onChange={handleChipChange("optionalFields")}
            placeholder="Optional Fields"
          />
        </Box>
      </Paper>
    </>

  );
}