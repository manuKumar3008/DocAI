import { FEATURES } from "../../../config/features";
import { translateDocumentStream } from "./conversationApi";
import { startMockTranslation } from "../../../mocks/mockTranslationStream";

export const translationStream = (options) => {
  if (FEATURES.MOCK_TRANSLATION) {
    return startMockTranslation(options);
  }

  return translateDocumentStream(options);
};