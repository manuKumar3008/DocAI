import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    TextField,
    Box,
} from "@mui/material";

const LANGUAGES = [
    "English",
    "Turkish",
    "Polish",
    "Arabic",
    "Russian",
    "German",
    "French",
    "Spanish",
    "Italian",
];

function TranslateDialog({
    open,
    language,
    pageMode,
    pageSelection,
    loading = false,
    onLanguageChange,
    onPageModeChange,
    onPageSelectionChange,
    onClose,
    onTranslate,
}) {
    const isCustomMode = pageMode === "custom";

    const isCustomInvalid =
        isCustomMode && !pageSelection?.trim();

    const handleSubmit = (e) => {
        e.preventDefault();

        if (loading) return;

        if (isCustomInvalid) return;

        onTranslate();
    };

    return (
        <Dialog
            open={open}
            onClose={loading ? undefined : onClose}
            maxWidth="xs"
            fullWidth
        >
            <Box
                component="form"
                onSubmit={handleSubmit}
            >
                <DialogTitle>
                    Translate Document
                </DialogTitle>

                <DialogContent>
                    <FormControl
                        fullWidth
                        sx={{ mt: 1 }}
                        disabled={loading}
                    >
                        <InputLabel id="translate-language-label">
                            Language
                        </InputLabel>

                        <Select
                            labelId="translate-language-label"
                            value={language}
                            label="Language"
                            onChange={(e) =>
                                onLanguageChange(e.target.value)
                            }
                        >
                            {LANGUAGES.map((lang) => (
                                <MenuItem
                                    key={lang}
                                    value={lang}
                                >
                                    {lang}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    <Box sx={{ mt: 3 }}>
                        <FormControl
                            fullWidth
                            disabled={loading}
                        >
                            <InputLabel id="translate-pages-label">
                                Pages
                            </InputLabel>

                            <Select
                                labelId="translate-pages-label"
                                value={pageMode}
                                label="Pages"
                                onChange={(e) =>
                                    onPageModeChange(e.target.value)
                                }
                            >
                                <MenuItem value="all">
                                    All Pages
                                </MenuItem>

                                <MenuItem value="custom">
                                    Custom
                                </MenuItem>
                            </Select>
                        </FormControl>

                        {isCustomMode && (
                            <TextField
                                fullWidth
                                required
                                margin="normal"
                                label="Custom Pages"
                                placeholder="e.g. 1-5, 8, 11-13"
                                value={pageSelection}
                                disabled={loading}
                                onChange={(e) =>
                                    onPageSelectionChange(
                                        e.target.value
                                    )
                                }
                                error={isCustomInvalid}
                                helperText={
                                    isCustomInvalid
                                        ? "Enter page numbers or ranges."
                                        : "Examples: 1-5, 8, 11-13"
                                }
                            />
                        )}
                    </Box>
                </DialogContent>

                <DialogActions>
                    <Button
                        onClick={onClose}
                        disabled={loading}
                    >
                        Cancel
                    </Button>

                    <Button
                        type="submit"
                        variant="contained"
                        disabled={
                            loading || isCustomInvalid
                        }
                    >
                        {loading
                            ? "Translating..."
                            : "Translate"}
                    </Button>
                </DialogActions>
            </Box>
        </Dialog>
    );
}

export default TranslateDialog;