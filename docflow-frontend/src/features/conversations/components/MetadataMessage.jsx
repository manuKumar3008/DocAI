import {
    Box,
    Divider,
    Paper,
    Typography,
} from "@mui/material";

function MetadataMessage({ metadata }) {
    if (!metadata) return null;

    const fields = metadata.metadata || {};

    return (
        <Paper
            variant="outlined"
            sx={{
                p: 2,
                borderRadius: 2,
                mt: 1,
            }}
        >
            <Typography
                variant="subtitle1"
                fontWeight={600}
                gutterBottom
            >
                Extracted Metadata
            </Typography>

            {Object.entries(fields).length === 0 ? (
                <Typography color="text.secondary">
                    No metadata available.
                </Typography>
            ) : (
                Object.entries(fields).map(([key, value], index) => (
                    <Box key={key}>
                        <Typography
                            variant="caption"
                            color="text.secondary"
                            fontWeight={600}
                        >
                            {key}
                        </Typography>

                        <Typography
                            variant="body1"
                            sx={{
                                mt: 0.5,
                                mb: 1.5,
                                wordBreak: "break-word",
                            }}
                        >
                            {value || "Not Available"}
                        </Typography>

                        {index !== Object.entries(fields).length - 1 && (
                            <Divider sx={{ mb: 1.5 }} />
                        )}
                    </Box>
                ))
            )}
        </Paper>
    );
}

export default MetadataMessage;