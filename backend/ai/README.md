# AI module

This module contains AI provider adapters. `StubAiProvider` performs deterministic local analysis without an API key. Provider contracts and result validation live in `backend/core`; job scheduling, retries, and transaction orchestration live in `backend/app`; PostgreSQL job and result storage lives in `backend/persistence`.
