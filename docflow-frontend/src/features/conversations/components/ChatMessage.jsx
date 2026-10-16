import {
    Box,
    Typography,
    Paper,
    Chip,
    Stack,
    Divider,
} from "@mui/material";
import KeyboardReturnRoundedIcon from "@mui/icons-material/KeyboardReturnRounded";
import ArrowForwardRoundedIcon from "@mui/icons-material/ArrowForwardRounded";
import {
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
} from "@mui/material";

import SmartToyOutlinedIcon from "@mui/icons-material/SmartToyOutlined";
import PersonOutlineOutlinedIcon from "@mui/icons-material/PersonOutlineOutlined";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";

import MetadataMessage from "./MetadataMessage";
import TranslationMessage from "./TranslationMessage";
import RichTextRenderer from "../../../components/common/RichTextRenderer";

function ChatMessage({ message,
    onFollowUpClick, }) {
    const isUser = message.role === "USER";

    const followUpQuestions =
        message.metadata?.followUpQuestions ?? [];

    const hasFollowUps =
        !isUser && followUpQuestions.length > 0;

    // Handle Metadata Message separately
    if (!isUser && message.type === "METADATA") {
        return (
            <MetadataMessage
                metadata={message.metadata}
            />
        );
    }

    // Handle Translation Message separately
    if (!isUser && message.type === "TRANSLATION") {
        return (
            <TranslationMessage
                message={message}
            />
        );
    }

    return (
        <Box
            sx={{
                width: "100%",
                display: "flex",
                justifyContent: isUser ? "flex-end" : "flex-start",
            }}
        >
            <Paper
                elevation={0}
                sx={{
                    maxWidth: isUser ? "75%" : "85%",
                    px: 2.5,
                    py: 2,
                    borderRadius: 3,
                    border: "1px solid",
                    borderColor: "divider",
                    bgcolor: isUser
                        ? "primary.main"
                        : "background.paper",
                    color: isUser
                        ? "primary.contrastText"
                        : "text.primary",
                }}
            >
                <Stack
                    direction="row"
                    spacing={1}
                    alignItems="center"
                    mb={1.5}
                    flexWrap="wrap"
                    useFlexGap
                >
                    {!isUser && (
                        <>
                            <SmartToyOutlinedIcon fontSize="small" />

                            <Typography
                                variant="subtitle2"
                                fontWeight={700}
                            >
                                AI Assistant
                            </Typography>

                            {message.type && (
                                <Chip
                                    size="small"
                                    label={message.type}
                                    variant="outlined"
                                />
                            )}

                            {message.metadata?.language && (
                                <Chip
                                    size="small"
                                    label={message.metadata.language}
                                    color="primary"
                                    variant="outlined"
                                />
                            )}

                            {message.metadata?.cached && (
                                <Chip
                                    size="small"
                                    color="success"
                                    label="Cached"
                                />
                            )}
                        </>
                    )}



                    {!isUser && message.metadata?.language && (
                        <Chip
                            size="small"
                            label={message.metadata.language}
                            color="primary"
                            variant="outlined"
                        />
                    )}

                    {!isUser && message.metadata?.cached && (
                        <Chip
                            size="small"
                            color="success"
                            label="Cached"
                        />
                    )}
                </Stack>

                {/* USER MESSAGE */}

                {isUser && (
                    <Box
                        sx={{
                            display: "flex",
                            alignItems: "flex-start",
                            gap: 1,
                        }}
                    >
                        <PersonOutlineOutlinedIcon
                            sx={{
                                fontSize: 18,
                                mt: "2px",
                                flexShrink: 0,
                            }}
                        />

                        <Typography
                            variant="body1"
                            sx={{
                                whiteSpace: "pre-wrap",
                                wordBreak: "break-word",
                                flex: 1,
                            }}
                        >
                            <Typography
                                component="span"
                                sx={{
                                    fontWeight: 600,
                                }}
                            >
                                You:
                            </Typography>{" "}
                            {message.content}
                        </Typography>
                    </Box>
                )}

                {/* AI MESSAGE */}
                {!isUser && (
                    <>

                        {hasFollowUps ? (
                            <>
                                <Typography
                                    variant="body2"
                                    color="text.secondary"
                                    sx={{
                                        mb: 2,
                                        lineHeight: 1.6,
                                    }}
                                >
                                    I couldn't confidently answer your question based on the
                                    available document content. These follow-up questions may
                                    help narrow down what you're looking for.
                                </Typography>

                                <List
                                    disablePadding
                                    sx={{
                                        mt: 1,
                                    }}
                                >
                                    {followUpQuestions.map((question, index) => (
                                        <ListItemButton
                                            key={question}
                                            onClick={() =>
                                                onFollowUpClick?.(question)
                                            }
                                            divider={
                                                index !==
                                                followUpQuestions.length - 1
                                            }
                                            sx={{
                                                px: 0,
                                                py: 1.75,
                                                minHeight: 56,
                                                bgcolor: "transparent",

                                                "&:hover": {
                                                    bgcolor: "transparent",
                                                },

                                                "&.Mui-focusVisible": {
                                                    bgcolor: "transparent",
                                                },
                                            }}
                                        >
                                            <ListItemText
                                                primary={question}
                                                primaryTypographyProps={{
                                                    variant: "body1",
                                                    sx: {
                                                        fontWeight: 400,
                                                        whiteSpace: "normal",
                                                        wordBreak: "break-word",
                                                        lineHeight: 1.6,
                                                        color: "text.primary",
                                                    },
                                                }}
                                            />

                                            <ListItemIcon
                                                sx={{
                                                    minWidth: 32,
                                                    ml: 2,
                                                    justifyContent: "flex-end",
                                                    color: "text.secondary",
                                                }}
                                            >
                                                <ArrowForwardRoundedIcon />
                                            </ListItemIcon>
                                        </ListItemButton>
                                    ))}
                                </List>
                            </>
                        ) : (
                            <RichTextRenderer
                                content={message.content}
                            />
                        )}

                        {!message.loading &&
                            !hasFollowUps &&
                            message.metadata?.references?.length > 0 && (
                                <>
                                    <Divider sx={{ my: 2 }} />

                                    <Typography
                                        variant="subtitle2"
                                        fontWeight={700}
                                        gutterBottom
                                    >
                                        References
                                    </Typography>

                                    <Stack spacing={1.5}>
                                        {message.metadata.references.map((ref, index) => (
                                            <Paper
                                                key={index}
                                                variant="outlined"
                                                sx={{
                                                    p: 2,
                                                    borderRadius: 2,
                                                }}
                                            >
                                                <Stack
                                                    direction="row"
                                                    spacing={1}
                                                    alignItems="center"
                                                    sx={{
                                                        mb: 2,
                                                    }}
                                                >
                                                    <DescriptionOutlinedIcon
                                                        fontSize="small"
                                                        color="primary"
                                                    />

                                                    <Typography
                                                        variant="body2"
                                                        fontWeight={600}
                                                    >
                                                        {ref.sourceFile}
                                                    </Typography>
                                                </Stack>

                                                <Typography
                                                    variant="caption"
                                                    color="text.secondary"
                                                    display="block"
                                                    sx={{
                                                        mb: 0.75,
                                                    }}
                                                >
                                                    Section: {ref.section}
                                                </Typography>

                                                <Typography
                                                    variant="caption"
                                                    color="text.secondary"
                                                    display="block"
                                                    sx={{
                                                        mb: 1.5,
                                                    }}
                                                >
                                                    Pages {ref.startPage}
                                                    {ref.endPage !== ref.startPage
                                                        ? ` - ${ref.endPage}`
                                                        : ""}
                                                </Typography>

                                                <Divider sx={{ mb: 1.5 }} />

                                                <Typography
                                                    variant="body2"
                                                    sx={{
                                                        color: "text.secondary",
                                                        fontStyle: "italic",
                                                        lineHeight: 1.7,
                                                    }}
                                                >
                                                    {ref.snippet}
                                                </Typography>
                                            </Paper>
                                        ))}
                                    </Stack>
                                </>
                            )}
                    </>
                )}
            </Paper>
        </Box>
    );
}

export default ChatMessage;