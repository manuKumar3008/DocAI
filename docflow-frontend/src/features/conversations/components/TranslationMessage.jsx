import { useEffect, useState } from "react";

import {
    Box,
    Chip,
    Divider,
    Paper,
    Stack,
    Typography,
    Skeleton,
    LinearProgress,
} from "@mui/material";
import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";
import RichTextRenderer from "../../../components/common/RichTextRenderer";

function TranslationMessage({ message }) {
    const {
        loading,
        metadata = {},
    } = message;

    const {
        language,
        status,
        requestedPages = [],
        translatedPages = {},
    } = metadata;

    const [selectedPage, setSelectedPage] = useState(
        requestedPages[0] ?? 1
    );

    /*
     * Reset selection only when a NEW
     * translation request starts.
     *
     * Do NOT switch pages while streaming.
     */
    useEffect(() => {
        if (
            requestedPages.length &&
            !requestedPages.includes(selectedPage)
        ) {
            setSelectedPage(requestedPages[0]);
        }
    }, [requestedPages]);

    const getStatusColor = () => {
        switch (status) {
            case "COMPLETED":
                return "success";

            case "FAILED":
                return "error";

            default:
                return "warning";
        }
    };
    const currentTranslation =
        translatedPages[selectedPage];

    const translatingPage = loading
        ? requestedPages.find(
            (page) => !translatedPages[page]
        )
        : null;
    return (
        <Box
            display="flex"
            justifyContent="flex-start"
        >
            <Paper
                elevation={0}
                sx={{
                    maxWidth: "85%",
                    minWidth: 500,
                    px: 2.5,
                    py: 2,
                    borderRadius: 3,
                    border: "1px solid",
                    borderColor: "divider",
                    bgcolor: "background.paper",
                }}
            >
                {/* ==========================
                    HEADER
                ========================== */}

                <Stack
                    direction="row"
                    spacing={1}
                    alignItems="center"
                    flexWrap="wrap"
                    useFlexGap
                    sx={{
                        mb: 3,
                    }}
                >
                    <SmartToyOutlinedIcon
                        fontSize="small"
                    />

                    <Typography
                        variant="subtitle2"
                        fontWeight={700}
                    >
                        AI Assistant
                    </Typography>

                    <Chip
                        size="small"
                        label="TRANSLATION"
                        variant="outlined"
                    />

                    <Chip
                        size="small"
                        label={language}
                        color="primary"
                        variant="outlined"
                    />

                    <Chip
                        size="small"
                        label={
                            status === "COMPLETED"
                                ? "Completed"
                                : status === "FAILED"
                                    ? "Failed"
                                    : "Translating"
                        }
                        color={getStatusColor()}
                    />
                </Stack>

                <Box
                    sx={{
                        mt: 3,
                        mb: 3,
                    }}
                >
                    <Divider />
                </Box>

                {/* ==========================
                    PAGE NAVIGATION
                ========================== */}

                <Box
                    sx={{
                        overflowX: "auto",
                        pb: 1,
                        mb: 2,
                    }}
                >
                    <Stack
                        direction="row"
                        spacing={1}
                        sx={{
                            width: "max-content",
                        }}
                    >
                        {requestedPages.map((page) => {
                           

                            const translated =
                                Boolean(
                                    translatedPages[page]
                                );

                            return (
                                <Chip
                                    key={page}
                                    label={`Page ${page}`}
                                    clickable={translated}
                                    disabled={!translated}
                                    color={
                                        selectedPage === page
                                            ? "primary"
                                            : "default"
                                    }
                                    variant={
                                        selectedPage === page
                                            ? "filled"
                                            : "outlined"
                                    }
                                    sx={{
                                        fontWeight: 500,
                                        transition: "all .3s ease",

                                        ...(translated && {
                                            color: "text.primary",
                                            borderColor: "divider",
                                        }),

                                        ...(!translated && {
                                            color: "text.secondary",
                                            borderColor: "divider",
                                        }),

                                        ...(page === translatingPage && {
                                            border: "2px solid",
                                            borderColor: "primary.dark",
                                            color: "primary.dark",

                                            animation: "pulseBorder 1.4s infinite",

                                            "@keyframes pulseBorder": {
                                                "0%": {
                                                    boxShadow: "0 0 0 0 rgba(21,101,192,.45)",
                                                },
                                                "70%": {
                                                    boxShadow: "0 0 0 6px rgba(21,101,192,0)",
                                                },
                                                "100%": {
                                                    boxShadow: "0 0 0 0 rgba(21,101,192,0)",
                                                },
                                            },
                                        }),
                                    }}
                                    onClick={() => {
                                        if (translated) {
                                            setSelectedPage(page);
                                        }
                                    }}
                                />
                            );
                        })}
                    </Stack>
                </Box>

                <Divider sx={{ mb: 2 }} />

                {/* ==========================
                    PAGE TITLE
                ========================== */}


                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="center"
                    mb={2}
                >
                    <Typography
                        variant="h6"
                        fontWeight={700}
                    >
                        📄 Page {selectedPage}
                    </Typography>

                </Stack>

                <Divider sx={{ mb: 3 }} />

                {/* ==========================
    TRANSLATION CONTENT
========================== */}

                {currentTranslation ? (
                    <RichTextRenderer
                        content={currentTranslation}
                    />
                ) : loading ? (
                    <Box>
                        <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            mb={2}
                        >
                            <Typography
                                variant="subtitle2"
                                color="primary"
                            >
                                Translating...
                            </Typography>
                        </Stack>

                        <LinearProgress
                            sx={{
                                mb: 3,
                                borderRadius: 2,
                                height: 6,
                            }}
                        />

                        <Skeleton
                            variant="text"
                            height={32}
                            width="70%"
                        />

                        <Skeleton
                            variant="text"
                            height={24}
                            width="100%"
                        />

                        <Skeleton
                            variant="text"
                            height={24}
                            width="95%"
                        />

                        <Skeleton
                            variant="text"
                            height={24}
                            width="90%"
                        />

                        <Skeleton
                            variant="text"
                            height={24}
                            width="98%"
                        />

                        <Skeleton
                            variant="text"
                            height={24}
                            width="80%"
                        />

                        <Typography
                            variant="body2"
                            color="text.secondary"
                            mt={2}
                        >
                            Translating page {selectedPage}...
                        </Typography>
                    </Box>
                ) : (
                    <Paper
                        variant="outlined"
                        sx={{
                            p: 3,
                            borderRadius: 2,
                            bgcolor: "grey.50",
                        }}
                    >
                        <Typography
                            color="text.secondary"
                        >
                            No translation available for this page.
                        </Typography>
                    </Paper>
                )}


            </Paper>
        </Box>
    );
}

export default TranslationMessage;