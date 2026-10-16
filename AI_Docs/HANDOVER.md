# DocAI – Handover

Written on 2026-10-16 from reading the repository. Items marked **(verify)** were inferred from code and not run.

## 1. Purpose

DocAI is a document intelligence system for HR-style documents (German and English: contracts, notices, certificates). It does five things:

1. Ingest PDFs.
2. Classify them and extract metadata.
3. Index them in a vector and text store.
4. Answer questions over them with RAG, with citations.
5. Summarise and translate them.

Documents the AI is unsure about go to a human "Indexer" for review.

## 2. System overview

| Layer | Folder | Tech |
|---|---|---|
| Frontend | `docflow-frontend/` | React 19, Vite 8, MUI 9, Redux Toolkit, TanStack Query, react-router 7, react-hook-form, react-markdown |
| Workflow API | `docflow-backend/` | Node 20, Express 5, Mongoose 9, JWT, bcryptjs, multer, pdf-lib |
| AI service | `src/main/java/com/bsg/ai/services` | Spring Boot 3.5.3, Java 17, Spring AI (Mistral, Elasticsearch vector store, RAG advisors), PDFBox 3.0.5, Tess4J, Lucene, OpenFeign, Caffeine |
| Data stores | docker-compose | MongoDB 7, Elasticsearch 8.19.1 (+ Kibana) |
| Model services | docker-compose | Reranker `BAAI/bge-reranker-v2-m3` (HF text-embeddings-inference, port 8010), Ollama `nomic-embed-text-v2-moe` (port 11434) |

Request flow: Browser → Node (`/api/*`, JWT) → Java (`/document-*`) → Elasticsearch / Mistral / reranker. Node stores workflow state in MongoDB. Java stores chunks and embeddings in Elasticsearch.

## 3. Repository layout

```
pom.xml, mvnw*, src/              Java AI service (artifact "spring-ai")
docker-compose.yml                All services
docflow-backend/
  server.js                       Entrypoint
  src/app.js                      Express app and route mounting
  src/config/                     db.js, seed.js
  src/models/                     Mongoose schemas
  src/routes/ controllers/ services/ middleware/ utils/
docflow-frontend/
  src/pages/                      Route-level screens
  src/features/                   Per-feature logic (administration, auth, conversations, dashboard, document-library, indexer, settings, upload, users)
  src/api/, services/, store/, router/, layouts/, components/, theme/
AI_Docs/                          This documentation
```

## 4. Java AI service

Package root: `com.bsg.ai.services`.

### 4.1 Endpoints

| Controller path | Endpoint | Purpose |
|---|---|---|
| `/document-upload` | `POST /documents`, `DELETE /removeDoc` | Ingest and index a PDF; remove from the index |
| `/document-extraction` | `POST /extract` | Metadata extraction |
| `/document-classification` | `POST /` | Classify a document |
| `/process-form` | `POST /` | ICR form extraction |
| `/document-question-answer` | `POST /ask`, `/ask-with-history`, `/ask/stream`, `/ask-with-history/stream`, `/ask-with-history/sync` | RAG Q&A (SSE streaming, or JSON for `/sync`) |
| `/documents` | `GET /{docId}/summary`, `POST /{docId}/query` | Summary and per-document query |
| `/documents` | `GET /{docId}/translate`, `GET /{docId}/translate/stream` | Translation |
| `/documents` | `GET /search/filters` and archive/masterdata proxies | Filters backed by the external archive API |

### 4.2 Modules (`extraction/`)

- `services/` – PDF extraction. `pdfExtraction/` has the PDF stripper, table detection (bordered and borderless) and image or OCR blocks.
- `classification/`, `icrform/`, `summary/`, `translation/` – LLM features. Prompts are in `src/main/resources/prompt.properties`.
- `qa/`, `multiDocQa/` – RAG question answering over one or many documents.
- `filter/` – natural-language to filter conversion using the external archive's document types, subjects and companies.
- `ocr/service` – `TesseractOcrService` and `LlmOcrService`.
- `config/` – Elasticsearch vector store config and dimension resolver, caching, history-pruning advisor, query logging and compression.
- `util/` – PDF processing, hashing for cache keys, stream buffer.

### 4.3 Configuration (`src/main/resources/application.yml`)

- Chat model `mistral-small-latest` (temperature 0.25). Embeddings `mistral-embed`, 1024 dimensions.
- RAG: retriever `top-k 10`, similarity threshold 0.40, reranker `top-n 7`, multi-doc top-k 80, max 20 context chunks of up to 5000 characters. Query expansion and rewrite are off.
- OCR: Tesseract, languages `eng+deu`.
- Upload limit 20 MB.
- Ingestion threads: 4 readers, 6 chunkers.
- `application-test.yml` toggles real or mock OCR and LLM in tests.

## 5. Node backend (`docflow-backend`)

Entry: `server.js` → `src/app.js`. Health check is `GET /health`. CORS is open (`*`).

### 5.1 API

| Mount | Routes |
|---|---|
| `/api/auth` | `POST /login`, `GET /me`, `POST /logout`, `PUT /change-password` |
| `/api/documents` | `GET /`, `POST /` (multipart `file`), `GET /:id`, `GET /:id/file`, `DELETE /:id`, `POST /:id/summarize`, `POST /:id/translate`, `POST /:id/translate/stream` |
| `/api/manual-indexing` | `GET /queue`, `GET /:id`, `POST /:id/submit` |
| `/api/conversations` | `GET /`, `POST /`, `GET /:id`, `POST /:id/ask`, `GET /:id/messages` |
| `/api/admin` (ADMIN) | users CRUD plus `PUT /users/:id/reset-password`; document-types CRUD (INDEXER may read); `GET/PUT /confidence-threshold` |

Static files are served from `/uploads`.

### 5.2 Data model (MongoDB)

- **User** – userId, name, email, password (bcrypt), role `ADMIN | INDEXER | PROCESSOR`, active.
- **Document** – documentId, fileName, fileHash, documentType, source `MANUAL_UPLOAD | SHARED_DRIVE`, state, storagePath, pageCount, assignedIndexerId, statusHistory[].
- **DocumentMetadata** – documentId, documentType, fields (free-form).
- **DocumentType** – documentType, displayName, active, mandatoryFields[], optionalFields[], description.
- **Conversation** – conversationId, title, documentIds[], createdBy, lastActivityAt.
- **Message** – conversationId, sequence, role, content, references (page, section, chunkId, snippet, score).
- **SystemConfig** – key/value (stores the confidence threshold).
- **TokenBlacklist** – revoked JWTs, used on logout.

### 5.3 Document workflow

States: `uploaded → extracting → pending_review | indexed | rejected | failed`.

1. Upload stores the file, hashes it (duplicate check) and sends it to Java for indexing.
2. `extraction.service.js` calls Java for classification and metadata.
3. Routing: if the type is known, confidence ≥ threshold and all mandatory fields are present, the document is saved to DocumentMetadata and set to `indexed`. Otherwise it is set to `pending_review`.
4. An INDEXER reviews pending documents (`/api/manual-indexing`) and submits corrected metadata. Every transition is appended to `statusHistory`.

Threshold precedence: env `CONFIDENCE_THRESHOLD` → `SystemConfig` in the DB → 0.8. Because the env var wins, changing it in Settings has no effect while the env var is set. **(verify)**

### 5.4 Environment variables

`PORT`, `NODE_ENV`, `MONGO_URI`, `JWT_SECRET`, `JWT_EXPIRES_IN`, `JAVA_API_BASE_URL`, `UPLOAD_DIR`, `MAX_FILE_SIZE_MB`, `CONFIDENCE_THRESHOLD`.

The first admin user is created by `npm run seed` (`src/config/seed.js`) with the email `admin@docflow.com`. Its initial password is set in that file. Change it after first login.

## 6. Frontend (`docflow-frontend`)

Routes (`src/router/routeConstants.js`):

| Path | Page |
|---|---|
| `/` | Login |
| `/dashboard` | Dashboard |
| `/upload` | Upload |
| `/document-library` | Document library and processing |
| `/indexer` | Manual indexing queue |
| `/conversation/:conversationId` | Chat with documents (ConversationNavigator in MessageList) |
| `/administration`, `/users`, `/settings` | Admin screens |

The API base URL comes from `VITE_API_BASE_URL`. `.env.development` points at `http://localhost:3000`. Build with `npm run build`. The Docker image serves it through nginx on port 80 (mapped to 5173). The React Compiler is enabled through Babel.

## 7. Running and deploying

See README.md. In compose the Spring app runs the WAR built by `maven-builder`, installs Tesseract at startup and waits for Elasticsearch. Node and the frontend are built from their Dockerfiles.

## 8. Cleanup done in this handover

- Removed from git: `logs/` (rotated log archives), `.continue/` (editor config), `docflow-backend/qc` (stray file).
- Deleted locally: `spring.log`, `logs/`, `target/` (all rebuildable).
- `.gitignore` rewritten: build output, `node_modules`, `.env`, uploads, logs.
- `pythonOCR/` (OCR/LayoutLM/Granite/Docling experiments, own virtualenvs) is not used by the product. It is git-ignored and left on disk. Move or delete it manually (see section 10).
- The hardcoded Mistral API key default was removed from `application.yml`. `MISTRAL_API_KEY` must now be supplied.

## 9. Known issues and risks

1. **Leaked secret in git history.** The old Mistral key default is in earlier commits of `application.yml`. **Rotate that key.** Removing it from the current file does not make it safe.
2. **Hardcoded credentials in `docker-compose.yml`**: MongoDB root user and password, a placeholder `JWT_SECRET`, the external archive API login, and the internal IP `192.168.9.34` in several places. Move them to a `.env` file or secrets before any non-local deployment.
3. The backend README documents the seeded admin login. Change that password on first login and remove it from the README.
4. `docker-compose.yml` has a tab-indented `TESSDATA_PATH` line under `environment:`, which is invalid YAML. Fix before `docker compose up`. **(verify)**
5. CORS is open to all origins in Node and Java.
6. `docflow-backend/README.md` and the frontend README are partly stale. The backend README refers to a missing `.env.example` and `/docs/postman/`. The frontend README is the unchanged Vite template.
7. The Java Q&A tests need live Elasticsearch and a Mistral key.
8. Some test PDFs in `src/test/resources` contain personal-looking German names. Check they are synthetic before making the repo public.
9. `docflow-backend/uploads/` holds runtime uploads and is git-ignored.
10. `docflow-frontend/.env.development` and `.env.production` are tracked. They hold only non-secret settings. Keep it that way.

## 10. Next steps

- Rotate the Mistral key. Move compose secrets into `.env`.
- Delete or archive `D:\DocAI\pythonOCR` once nothing else needs it. Move requests were refused by the OS from this session.
- Push to GitHub: `origin` already points to `https://github.com/manuKumar3008/DocAI.git`. Integrate the remote's initial commit with `git pull --rebase origin main`, then `git push -u origin main`.
- Add a Postman collection or OpenAPI spec for the Node API.
