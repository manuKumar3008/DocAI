import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  Box,
  Fab,
} from "@mui/material";

import KeyboardArrowDownRoundedIcon from "@mui/icons-material/KeyboardArrowDownRounded";

import ChatMessage from "./ChatMessage";
import ConversationNavigator from "./ConversationNavigator";

function MessageList({
  messages = [],
  onFollowUpClick,
}) {
  const scrollContainerRef = useRef(null);
  const bottomRef = useRef(null);
  const messageRefs = useRef({});

  const [showScrollButton, setShowScrollButton] =
    useState(false);

  const [hoverChat, setHoverChat] =
    useState(false);

  const [hoverNavigator, setHoverNavigator] =
    useState(false);

  const [navigatorTop, setNavigatorTop] =
    useState(16);

  const [activeMessageId, setActiveMessageId] =
    useState(null);

  const userQuestions = useMemo(
    () =>
      messages.filter(
        (message) =>
          message.role === "USER" &&
          message.type === "QUESTION"
      ),
    [messages]
  );

  const showNavigator =
    hoverChat || hoverNavigator;

  const scrollToBottom = (
    behavior = "smooth"
  ) => {
    bottomRef.current?.scrollIntoView({
      behavior,
      block: "end",
    });
  };

  const scrollToMessage = (id) => {
    messageRefs.current[id]?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  // Auto-scroll whenever a new message arrives

  useEffect(() => {
    scrollToBottom();
  }, [messages.length]);

  // Scroll handling

  useEffect(() => {
    const container =
      scrollContainerRef.current;

    if (!container) return;

    const handleScroll = () => {
      const {
        scrollTop,
        scrollHeight,
        clientHeight,
      } = container;

      // Existing FAB logic

      const distanceFromBottom =
        scrollHeight -
        scrollTop -
        clientHeight;

      setShowScrollButton(
        distanceFromBottom > 150
      );

      // --------------------------
      // Navigator Position
      // --------------------------

      const maxScroll =
        scrollHeight - clientHeight;

      const scrollPercentage =
        maxScroll <= 0
          ? 0
          : scrollTop / maxScroll;

      const navigatorHeight = 320;

      const availableHeight =
        clientHeight -
        navigatorHeight -
        32;

      setNavigatorTop(
        16 +
          scrollPercentage *
            Math.max(
              availableHeight,
              0
            )
      );

      // --------------------------
      // Active Question
      // --------------------------

      let current = null;

      for (const question of userQuestions) {
        const element =
          messageRefs.current[
            question.id
          ];

        if (!element) continue;

        const rect =
          element.getBoundingClientRect();

        const containerRect =
          container.getBoundingClientRect();

        if (
          rect.top <=
          containerRect.top + 140
        ) {
          current = question.id;
        }
      }

      setActiveMessageId(current);
    };

    container.addEventListener(
      "scroll",
      handleScroll
    );

    handleScroll();

    return () => {
      container.removeEventListener(
        "scroll",
        handleScroll
      );
    };
  }, [userQuestions]);
  return (
  <Box
    sx={{
      flex: 1,
      position: "relative",
      overflow: "hidden",
    }}
  >
    <ConversationNavigator
      open={showNavigator}
      messages={messages}
      activeMessageId={activeMessageId}
      onNavigate={scrollToMessage}
      onMouseEnter={() =>
        setHoverNavigator(true)
      }
      onMouseLeave={() =>
        setHoverNavigator(false)
      }
      style={{
        top: navigatorTop,
      }}
    />

    <Box
      ref={scrollContainerRef}
      onMouseMove={(event) => {
        const width =
          scrollContainerRef.current?.clientWidth ??
          0;

        // Hover only near the scrollbar
        setHoverChat(
          event.nativeEvent.offsetX >
            width - 28
        );
      }}
      onMouseLeave={() =>
        setHoverChat(false)
      }
      sx={{
        height: "100%",
        overflowY: "auto",
        p: 3,
        display: "flex",
        flexDirection: "column",
        gap: 3,

        "&::-webkit-scrollbar": {
          width: 10,
        },

        "&::-webkit-scrollbar-thumb": {
          borderRadius: 999,
        },
      }}
    >
      {messages.map((message, index) => (
        <Box
          key={message.id ?? index}
          ref={(element) => {
            if (message.id) {
              messageRefs.current[
                message.id
              ] = element;
            }
          }}
        >
          <ChatMessage
            message={message}
            onFollowUpClick={
              onFollowUpClick
            }
          />
        </Box>
      ))}
            <div ref={bottomRef} />

      {showScrollButton && (
        <Fab
          size="small"
          color="primary"
          onClick={() =>
            scrollToBottom()
          }
          sx={{
            position: "sticky",
            alignSelf: "flex-end",
            bottom: 16,
            mr: 1,
            zIndex: 25,
            boxShadow: 3,
          }}
        >
          <KeyboardArrowDownRoundedIcon />
        </Fab>
      )}
    </Box>
  </Box>
);

}

export default MessageList;