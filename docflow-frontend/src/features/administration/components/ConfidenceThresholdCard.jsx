import { useEffect, useMemo, useState } from "react";
import {
  Box,
  Button,
  Card,
  CardContent,
  Slider,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

const MIN = 0;
const MAX = 1;
const STEP = 0.01;

export default function ConfidenceThresholdCard({
  threshold = 0,
  loading = false,
  saving = false,
  onUpdate,
}) {
  const [value, setValue] = useState(threshold);

  useEffect(() => {
    setValue(threshold ?? 0);
  }, [threshold]);

  const isValid = useMemo(() => {
    return (
      !Number.isNaN(Number(value)) &&
      Number(value) >= MIN &&
      Number(value) <= MAX
    );
  }, [value]);

  const hasChanged = useMemo(() => {
    return Number(value) !== Number(threshold);
  }, [value, threshold]);

  const handleSliderChange = (_, newValue) => {
    setValue(Number(newValue));
  };

  const handleInputChange = (event) => {
    setValue(event.target.value);
  };

  const handleBlur = () => {
    let next = Number(value);

    if (Number.isNaN(next)) {
      next = threshold;
    }

    if (next < MIN) next = MIN;
    if (next > MAX) next = MAX;

    setValue(Number(next.toFixed(2)));
  };

  const handleUpdate = () => {
    if (!isValid || !hasChanged) return;

    onUpdate?.({
      threshold: Number(value),
    });
  };

  return (
    <Card>
      <CardContent>

        <Stack spacing={3}>

          <Box>
            <Typography variant="h6">
              Confidence Threshold
            </Typography>

            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.5 }}
            >
              Set the minimum confidence score required before
              a document is automatically processed.
            </Typography>
          </Box>

          <TextField
            label="Threshold"
            type="number"
            value={value}
            onChange={handleInputChange}
            onBlur={handleBlur}
            inputProps={{
              min: MIN,
              max: MAX,
              step: STEP,
            }}
            disabled={loading || saving}
            sx={{ maxWidth: 180 }}
            error={!isValid}
            helperText={!isValid ? "Value must be between 0 and 1." : " "}
          />

          <Box px={1}>
            <Slider
              value={Number(value)}
              min={MIN}
              max={MAX}
              step={STEP}
              onChange={handleSliderChange}
              disabled={loading || saving}
              valueLabelDisplay="auto"
            />

            <Stack
              direction="row"
              justifyContent="space-between"
            >
              <Typography variant="caption">
                0.00
              </Typography>

              <Typography variant="caption">
                1.00
              </Typography>
            </Stack>
          </Box>

          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="contained"
              onClick={handleUpdate}
              disabled={
                loading ||
                saving ||
                !isValid ||
                !hasChanged
              }
            >
              {saving ? "Updating..." : "Update Threshold"}
            </Button>
          </Box>

        </Stack>

      </CardContent>
    </Card>
  );
}