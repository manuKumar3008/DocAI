import {
    AppBar,
    Toolbar,
    IconButton,
    Typography,
    Avatar,
    Box,
} from "@mui/material";

import MenuOpenOutlinedIcon from "@mui/icons-material/MenuOpenOutlined";

import { useSidebar } from "../../../contexts/SidebarContext";

function MobileHeader() {
    const { toggleSidebar } = useSidebar();

    return (
        <AppBar
            position="sticky"
            elevation={1}
            color="inherit"
            sx={{
                display: {
                    xs: "flex",
                    lg: "none",
                },
                bgcolor: "background.paper",
                borderBottom: 1,
                borderColor: "divider",
                zIndex: (theme) => theme.zIndex.appBar,
            }}
        >
            <Toolbar
                sx={{
                    minHeight: 56,
                    px: 2,
                    display: "flex",
                    justifyContent: "space-between",
                }}
            >
                <Box
                    sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: 1,
                    }}
                >
                    <IconButton
                        onClick={toggleSidebar}
                        sx={{
                            color: "text.secondary",
                        }}
                    >
                        <MenuOpenOutlinedIcon
                            sx={{
                                fontSize: 26,
                            }}
                        />
                    </IconButton>
                    <Typography
                        variant="subtitle1"
                        fontWeight={600}
                        noWrap
                    >
                        DocFlow AI
                    </Typography>
                </Box>

                <Avatar
                    sx={{
                        width: 34,
                        height: 34,
                        bgcolor: "primary.main",
                        fontSize: 14,
                    }}
                >
                    AU
                </Avatar>
            </Toolbar>
        </AppBar>
    );
}

export default MobileHeader;