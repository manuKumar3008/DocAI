import {
  IconButton,
  Tooltip,
} from "@mui/material";

import VisibilityOutlinedIcon from "@mui/icons-material/VisibilityOutlined";

function IndexerActions({
  document,
  onReview,
}) {
  return (
    <Tooltip title="Review">
      <IconButton
        size="small"
        onClick={() =>
          onReview(document)
        }
      >
        <VisibilityOutlinedIcon
          fontSize="small"
        />
      </IconButton>
    </Tooltip>
  );
}

export default IndexerActions;