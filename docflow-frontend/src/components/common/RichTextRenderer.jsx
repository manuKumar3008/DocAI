import Box from "@mui/material/Box";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeRaw from "rehype-raw";

function RichTextRenderer({
    content = "",
    format = "auto",
}) {
    const isHtml =
        format === "html" ||
        (
            format === "auto" &&
            typeof content === "string" &&
            (
                content.includes("<html") ||
                content.includes("<body") ||
                content.includes("<table") ||
                content.includes("<thead") ||
                content.includes("<tbody") ||
                content.includes("<h1") ||
                content.includes("<h2") ||
                content.includes("<h3") ||
                content.includes("<ul") ||
                content.includes("<ol")
            )
        );

    const cleanHtml = (html) => {
        if (!html) return "";

        return html
            .replace(/<!DOCTYPE[^>]*>/gi, "")
            .replace(/<html[^>]*>/gi, "")
            .replace(/<\/html>/gi, "")
            .replace(/<head[\s\S]*?<\/head>/gi, "")
            .replace(/<body[^>]*>/gi, "")
            .replace(/<\/body>/gi, "")
            .trim();
    };

    return (
        <Box
            sx={{
                "& h1": {
                    mt: 2,
                    mb: 1,
                    fontWeight: 700,
                },

                "& h2": {
                    mt: 2,
                    mb: 1,
                    fontWeight: 700,
                },

                "& h3": {
                    mt: 2,
                    mb: 1,
                    fontWeight: 700,
                },

                "& p": {
                    lineHeight: 1.8,
                    mb: 1.5,
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                    overflowWrap: "anywhere",
                },

                "& ul, & ol": {
                    pl: 3,
                    mb: 2,
                },

                "& li": {
                    mb: .75,
                },

                "& table": {
                    width: "100%",
                    tableLayout: "fixed",
                    borderCollapse: "collapse",
                    display: "block",
                    overflowX: "auto",
                    maxWidth: "100%",
                    mt: 2,
                    mb: 2,
                },

                "& th": {
                    border: "1px solid",
                    borderColor: "divider",
                    bgcolor: "grey.100",
                    p: 1,
                    fontWeight: 700,
                    textAlign: "left",
                    whiteSpace: "normal",
                    wordBreak: "break-word",
                },

                "& td": {
                    border: "1px solid",
                    borderColor: "divider",
                    p: 1,
                    whiteSpace: "normal",
                    wordBreak: "break-word",
                },

                "& code": {
                    bgcolor: "grey.100",
                    px: .5,
                    borderRadius: 1,
                    fontFamily: "monospace",
                },

                "& pre": {
                    bgcolor: "grey.100",
                    p: 2,
                    borderRadius: 2,
                    overflowX: "auto",
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                },

                "& blockquote": {
                    borderLeft: "4px solid",
                    borderColor: "primary.main",
                    pl: 2,
                    ml: 0,
                    color: "text.secondary",
                    fontStyle: "italic",
                },

                "& img": {
                    maxWidth: "100%",
                    borderRadius: 2,
                    display: "block",
                    my: 2,
                },
            }}
        >
            {isHtml ? (
                <Box
                    dangerouslySetInnerHTML={{
                        __html: cleanHtml(content),
                    }}
                />
            ) : (
                <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    rehypePlugins={[rehypeRaw]}
                >
                    {content}
                </ReactMarkdown>
            )}
        </Box>
    );
}

export default RichTextRenderer;