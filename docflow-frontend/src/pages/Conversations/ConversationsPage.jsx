import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { useParams } from "react-router-dom";

import { PageLayout } from "../../components/page";
import ConversationLayout from "../../features/conversations/components/ConversationLayout";

import { loadConversationPage } from "../../features/conversations/thunk/conversationThunk";

function ConversationsPage() {
  const { conversationId } = useParams();

  const dispatch = useDispatch();

  useEffect(() => {
    if (!conversationId) return;

    dispatch(loadConversationPage(conversationId));
  }, [conversationId, dispatch]);

  return (
    <PageLayout
      sx={{
        height: "calc(100vh - 64px)",
        overflow: "hidden",
      }}
    >
      <ConversationLayout
        conversationId={conversationId}
      />
    </PageLayout>
  );
}

export default ConversationsPage;