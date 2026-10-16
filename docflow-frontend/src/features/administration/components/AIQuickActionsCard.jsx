import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";

import {
  Card,
  CardContent,
  Typography,
  Stack,
  Divider,
  Switch,
  FormControlLabel,
  Box,
} from "@mui/material";

import {
  DescriptionOutlined,
  TranslateOutlined,
  BadgeOutlined,
} from "@mui/icons-material";

import {
  setQuickActions,
  toggleQuickAction,
} from "../slice/administrationSlice";

const STORAGE_KEY = "quickActions";

export default function AIQuickActionsCard() {
  const dispatch = useDispatch();

  const quickActions = useSelector(
    (state) => state.administration.quickActions
  );

  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY);

    if (saved) {
      dispatch(setQuickActions(JSON.parse(saved)));
    }
  }, [dispatch]);

  const handleToggle = (feature) => {
    const updated = {
      ...quickActions,
      [feature]: !quickActions[feature],
    };

    dispatch(toggleQuickAction(feature));

    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(updated)
    );
  };

  const FeatureItem = ({
    icon,
    title,
    description,
    checked,
    feature,
  }) => (
    <>
      <Box
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        py={2}
      >
        <Stack direction="row" spacing={2}>
          {icon}

          <Box>
            <Typography fontWeight={600}>
              {title}
            </Typography>

            <Typography
              variant="body2"
              color="text.secondary"
            >
              {description}
            </Typography>
          </Box>
        </Stack>

        <FormControlLabel
          control={
            <Switch
              checked={checked}
              onChange={() =>
                handleToggle(feature)
              }
            />
          }
          label=""
        />
      </Box>

      <Divider />
    </>
  );

  return (
    <Card elevation={0}>
      <CardContent>
        <Typography variant="h6" fontWeight={600}>
          AI Quick Actions
        </Typography>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mb: 2 }}
        >
          Enable or disable AI capabilities
          available in the Quick Actions panel.
        </Typography>

        <Divider />

        <FeatureItem
          feature="summary"
          checked={quickActions.summary}
          icon={<DescriptionOutlined color="primary" />}
          title="Document Summary"
          description="Allow users to generate AI-powered summaries."
        />

        <FeatureItem
          feature="translation"
          checked={quickActions.translation}
          icon={<TranslateOutlined color="primary" />}
          title="Document Translation"
          description="Allow users to translate documents."
        />

        <FeatureItem
          feature="metadata"
          checked={quickActions.metadata}
          icon={<BadgeOutlined color="primary" />}
          title="View Metadata"
          description="Allow users to view extracted metadata."
        />
      </CardContent>
    </Card>
  );
}