import { useMemo, useState } from "react";

import {
    Chip,
    Stack,
} from "@mui/material";

import { useDispatch, useSelector } from "react-redux";

import SummarizeOutlinedIcon from "@mui/icons-material/SummarizeOutlined";
import TranslateOutlinedIcon from "@mui/icons-material/TranslateOutlined";
import BadgeOutlinedIcon from "@mui/icons-material/BadgeOutlined";

import { summarizeThunk } from "../thunk/summarizeThunk";
import { translateThunk } from "../thunk/translateThunk";

import TranslateDialog from "./TranslationDialogs";
import SummaryDialog from "./SummaryDialog";
import { fetchDocumentMetadataThunk } from "../thunk/metadataThunk";
import { addMessage } from "../slice/conversationSlice";

const PAGE_RANGE_PATTERN =
    /^\d+(?:-\d+)?(?:,\s*\d+(?:-\d+)?)*$/;

const isValidPageSelection = (value) => {
    const normalized = value?.trim();

    if (!normalized) return false;
    if (!PAGE_RANGE_PATTERN.test(normalized)) return false;

    const parts = normalized.split(",");

    for (const part of parts) {
        const item = part.trim();

        if (item.includes("-")) {
            const [start, end] = item
                .split("-")
                .map(Number);

            if (
                !Number.isInteger(start) ||
                !Number.isInteger(end) ||
                start < 1 ||
                end < 1 ||
                start > end
            ) {
                return false;
            }
        } else {
            const page = Number(item);

            if (
                !Number.isInteger(page) ||
                page < 1
            ) {
                return false;
            }
        }
    }

    return true;
};

function QuickActions({ selectedDocument }) {
    const dispatch = useDispatch();

    const {
        summary,
        translation,
        metadata,
    } = useSelector(
        (state) => state.administration.quickActions
    );

    const documentId = selectedDocument?.documentId;
    const pageCount = selectedDocument?.pageCount ?? 0;

    // Translation State
    const [translateOpen, setTranslateOpen] = useState(false);
    const [language, setLanguage] = useState("German");
    const [pageMode, setPageMode] = useState("all");
    const [pageSelection, setPageSelection] = useState("");
    const [translating, setTranslating] = useState(false);

    // Summary State
    const [summaryOpen, setSummaryOpen] = useState(false);
    const [summaryLanguage, setSummaryLanguage] = useState("English");
    const [summarizing, setSummarizing] = useState(false);

    const isCustomInvalid = useMemo(() => {
        if (pageMode !== "custom") return false;
        return !isValidPageSelection(pageSelection);
    }, [pageMode, pageSelection]);

    const handleSummaryClick = () => {
        if (!documentId || summarizing) return;

        setSummaryLanguage("English");
        setSummaryOpen(true);
    };

    const handleGenerateSummary = async () => {
        if (!documentId) return;

        try {
            setSummarizing(true);

            setSummaryOpen(false);

            await dispatch(
                summarizeThunk(documentId, {
                    language: summaryLanguage.toUpperCase(),
                    length: "SHORT",
                    forceRefresh: true,
                })
            );
        } finally {
            setSummarizing(false);
        }
    };

    const handleTranslateClick = () => {
        if (!documentId || translating) return;

        setLanguage("German");
        setPageMode("all");
        setPageSelection("");
        setTranslateOpen(true);
    };

    const handleMetadataClick = async () => {
        if (!documentId) return;

        try {
            const metadata = await dispatch(
                fetchDocumentMetadataThunk(documentId)
            ).unwrap();

            dispatch(
                addMessage({
                    id: `${Date.now()}-${Math.random()}`,
                    role: "ASSISTANT",
                    type: "METADATA",
                    metadata,
                })
            );
        } catch (error) {
            //console.error("Metadata Error:", error);
        }
    };

    const handlePageModeChange = (value) => {
        setPageMode(value);

        if (value === "all") {
            setPageSelection("");
        }
    };

    const handleTranslate = async () => {
        if (!documentId) return;

        if (pageMode === "custom" && isCustomInvalid) {
            return;
        }

        const payload = {
            targetLanguage: language,
            forceRefresh: true,
            pageCount,
        };

        if (pageMode === "custom") {
            payload.pages = pageSelection.trim();
        }

        try {
            setTranslating(true);

            setTranslateOpen(false);

            await dispatch(
                translateThunk(documentId, payload)
            );
        } finally {
            setTranslating(false);
        }
    };

    return (
        <>
            <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                useFlexGap
            >
                {summary && (
                    <Chip
                        icon={<SummarizeOutlinedIcon />}
                        label={
                            summarizing
                                ? "Generating..."
                                : "Summarize"
                        }
                        variant="outlined"
                        disabled={
                            !documentId ||
                            translating ||
                            summarizing
                        }
                        onClick={handleSummaryClick}
                    />
                )}

                {translation && (
                    <Chip
                        icon={<TranslateOutlinedIcon />}
                        label={
                            translating
                                ? "Translating..."
                                : "Translate"
                        }
                        variant="outlined"
                        disabled={
                            !documentId ||
                            translating ||
                            summarizing
                        }
                        onClick={handleTranslateClick}
                    />
                )}

                {metadata && (
                    <Chip
                        icon={<BadgeOutlinedIcon />}
                        label="View Metadata"
                        variant="outlined"
                        disabled={
                            !documentId ||
                            translating ||
                            summarizing
                        }
                        onClick={handleMetadataClick}
                    />
                )}
            </Stack>

            <TranslateDialog
                open={translateOpen}
                language={language}
                pageMode={pageMode}
                pageSelection={pageSelection}
                loading={translating}
                onLanguageChange={setLanguage}
                onPageModeChange={handlePageModeChange}
                onPageSelectionChange={setPageSelection}
                onClose={() => setTranslateOpen(false)}
                onTranslate={handleTranslate}
            />

            <SummaryDialog
                open={summaryOpen}
                language={summaryLanguage}
                loading={summarizing}
                onLanguageChange={setSummaryLanguage}
                onClose={() => setSummaryOpen(false)}
                onGenerate={handleGenerateSummary}
            />
        </>
    );
}

export default QuickActions;