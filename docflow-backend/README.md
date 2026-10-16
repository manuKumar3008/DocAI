# DocFlow Backend

Node.js + Express + MongoDB backend for the DocFlow document management system.

## Prerequisites

- Node.js v20+
- Access to the shared development server

## Setup

1. Clone the repo
   git clone https://github.com/staunchsys/DocAI.git
   cd DocAI/docflow-backend

2. Install dependencies
   npm install

3. Configure environment
   copy .env.example .env
   Update .env with the server IP

4. Run seed (first time only — creates Admin user)
   npm run seed

5. Start development server
   npm run dev

6. Test
   http://localhost:3000/health

## Default Admin credentials

Email: admin@docflow.com
Password: Admin@1234
(Change after first login)

## API Documentation

See /docs/postman/ for Postman collection and environment.
Import both files into Postman to test all APIs.

## Project Structure

src/
config/ - database connection
models/ - MongoDB schemas
services/ - business logic
controllers/ - HTTP request handling
routes/ - API endpoints
middleware/ - auth, upload, error handling
utils/ - helpers
