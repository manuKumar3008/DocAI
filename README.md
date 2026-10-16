# DocAI

DocAI is a document intelligence platform. It ingests PDF documents, classifies them, extracts structured metadata, indexes them for hybrid semantic search, and lets users chat with, summarise and translate their documents. Low-confidence extractions go to a human review queue.

It has three parts:

| Component | Folder | Stack | Port |
|---|---|---|---|
| AI engine | `src/` (repo root) | Java 17, Spring Boot 3.5, Spring AI, Elasticsearch, Mistral AI | 8080 |
| API / workflow backend | `docflow-backend/` | Node 20, Express 5, MongoDB (Mongoose) | 3000 |
| Web app | `docflow-frontend/` | React 19, Vite, MUI, Redux Toolkit, React Query | 5173 (Docker) |

Supporting services (Docker): Elasticsearch 8.19 + Kibana, a BGE reranker (HuggingFace TEI), Ollama (embedding model), MongoDB 7.

## Features

- PDF upload with duplicate detection by file hash.
- Text extraction (PDFBox) with table handling and OCR fallback (Tesseract / LLM OCR). German and English are supported.
- Document classification and metadata (ICR) extraction against configurable document types.
- Confidence-based routing: high confidence documents are auto-indexed. Others go to the manual Indexer queue.
- RAG question answering with streaming answers and conversation history across one or many documents.
- Document summary and translation (streaming).
- Role-based access: `ADMIN`, `INDEXER`, `PROCESSOR`.
- Administration: users, document types, confidence threshold.

## Architecture

```
React (5173) --> Node API (3000) --> MongoDB
                      |
                      v
             Spring AI service (8080) --> Elasticsearch (vectors + text)
                      |                    Mistral API (chat + embeddings)
                      |                    Reranker (8010), Tesseract OCR
                      v
           External document archive API (optional)
```

The Node backend owns users, documents, workflow state and conversations. The Java service owns AI work: extraction, classification, embeddings, retrieval and answering.

## Repository layout

```
src/main/java/com/bsg/ai/services/   Spring Boot AI service
docflow-backend/                     Node/Express API
docflow-frontend/                    React SPA
docker-compose.yml                   Full stack
AI_Docs/HANDOVER.md                  Detailed handover documentation
```

## Prerequisites

- Docker + Docker Compose (full stack), or:
- JDK 17, Maven (wrapper included), Node 20+, MongoDB, Elasticsearch 8, Tesseract OCR
- A Mistral AI API key

## Configuration

Nothing secret is committed. Provide these yourself.

| Variable | Used by | Purpose |
|---|---|---|
| `MISTRAL_API_KEY` | Java | Chat and embedding models (required) |
| `SPRING_ELASTICSEARCH_URIS` | Java | Elasticsearch URL |
| `RERANKER_BASE_URL`, `RERANKER_MODEL` | Java | Reranker service |
| `TESSDATA_PATH` | Java | Tesseract language data |
| `PROJECT_BASE_URL`, `PROJECT_USERNAME`, `PROJECT_PASSWORD` | Java | External archive API |
| `MONGO_URI` | Node | MongoDB connection |
| `JWT_SECRET`, `JWT_EXPIRES_IN` | Node | Auth tokens |
| `JAVA_API_BASE_URL` | Node | URL of the Java service |
| `UPLOAD_DIR`, `MAX_FILE_SIZE_MB`, `CONFIDENCE_THRESHOLD` | Node | Uploads and routing |
| `VITE_API_BASE_URL` | Frontend | Node API URL |

Copy `docflow-frontend/.env.example` for the frontend. Create `docflow-backend/.env` for the backend.

## Running

### Docker (full stack)

```bash
export MISTRAL_API_KEY=...        # PowerShell: $env:MISTRAL_API_KEY="..."
docker compose up -d --build
```

Open http://localhost:5173. The first start downloads models (reranker, Ollama embedding model) and can take several minutes.

### Local development

```bash
# 1. Java AI service
./mvnw spring-boot:run            # http://localhost:8080

# 2. Node backend
cd docflow-backend
npm install
npm run seed                      # creates the first admin user (admin@docflow.com)
npm run dev                       # http://localhost:3000/health

# 3. Frontend
cd docflow-frontend
npm install
npm run dev
```

Change the seeded admin password after first login.

## Tests

```bash
./mvnw test                       # Java tests (some need live Elasticsearch / Mistral)
```

Sample PDFs and expected outputs are in `src/test/resources/static/`.

## More

See [AI_Docs/HANDOVER.md](AI_Docs/HANDOVER.md) for architecture details, API reference, data model, workflows and known issues.
