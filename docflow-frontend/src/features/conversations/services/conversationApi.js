import apiClient from "../../../api";

export const summarizeDocument = async (
  documentId,
  payload
) => {
  const { data } = await apiClient.post(
    `/api/documents/${documentId}/summarize`,
    payload
  );

  return data;
};

export const translateDocument = async (
  documentId,
  payload
) => {
  const { data } = await apiClient.post(
    `/api/documents/${documentId}/translate`,
    payload
  );

  return data;
};

export const translateDocumentStream = async ({
  documentId,
  payload,
  token,
  onChunk,
  onDone,
  onError,
}) => {
  const baseURL =
    apiClient.defaults.baseURL || window.location.origin;

  const streamUrl = new URL(
    `/api/documents/${documentId}/translate/stream`,
    baseURL
  ).toString();

  const response = await fetch(streamUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token
        ? {
          Authorization: `Bearer ${token}`,
        }
        : {}),
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(
      `Streaming request failed (${response.status})`
    );
  }

  if (!response.body) {
    throw new Error("Readable stream not available");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  let buffer = "";
  let completed = false;

  const processEvents = (text) => {
    const events = text.split(/\r?\n\r?\n/);

    // Keep incomplete event in buffer
    buffer = events.pop() || "";

    for (const event of events) {
      const dataLines = event
        .split(/\r?\n/)
        .filter((line) => line.startsWith("data:"));

      if (!dataLines.length) continue;

      const data = dataLines
        .map((line) =>
          line.replace(/^data:\s*/, "")
        )
        .join("");

      //console.log("DATA >>>", data);

      if (!data) continue;

      if (data === "[DONE]") {
        completed = true;
        onDone?.();
        return;
      }

      try {
        const parsed = JSON.parse(data);

        //console.log("PARSED >>>", parsed);

        onChunk?.(parsed);
      } catch (err) {

      }
    }
  };

  try {
    while (true) {
      const { value, done } = await reader.read();


      if (done) {
        //console.log("STREAM CLOSED");

        // Flush decoder
        buffer += decoder.decode();

        if (buffer.trim()) {
          processEvents(buffer);

          // Processed everything
          buffer = "";
        }

        break;
      }

      const chunk = decoder.decode(value, {
        stream: true,
      });

      //console.log("RAW CHUNK >>>");
      //console.log(JSON.stringify(chunk));

      buffer += chunk;

      processEvents(buffer);
    }

    if (!completed) {
      onDone?.();
    }
  } catch (err) {
    //console.error("Translation stream error:", err);
    onError?.(err);
    throw err;
  } finally {
    reader.releaseLock();
  }
};

export const askQuestion = async (
  conversationId,
  payload
) => {
  const { data } = await apiClient.post(
    `/api/conversations/${conversationId}/ask`,
    payload
  );

  return data;
};
export const getDocumentMetadata = async (documentId) => {
  const { data } = await apiClient.get(
    `/api/documents/${documentId}`
  );

  return data;
};