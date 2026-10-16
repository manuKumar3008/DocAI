import { Paper } from "@mui/material";
import PageHeader from "../../components/common/PageHeader";

function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Conversations"
        description="View previous AI conversations."
      />

      <Paper sx={{ p: 3 }}>
        Setting page 
      </Paper>
    </>
  );
}

export default SettingsPage;