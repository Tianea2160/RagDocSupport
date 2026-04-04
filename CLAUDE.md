# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Gradle 특화 로컬 RAG 문서 시스템. 라이브러리 공식문서를 크롤링하여 Qdrant에 벡터 저장하고, MCP 서버를 통해 Claude Code에서 검색할 수 있게 하는 Spring Boot 애플리케이션.

## Build & Run

```bash
# 인프라 (Qdrant + Ollama)
docker-compose up -d
ollama pull nomic-embed-text   # 최초 1회

# 빌드
./gradlew build

# 실행
./gradlew bootRun              # http://localhost:8080, MCP SSE: /sse

# 테스트
./gradlew test
./gradlew test --tests "org.tianea.ragdocsupport.SomeTest"

# 컴파일만
./gradlew compileKotlin

# 린트
./gradlew ktlintCheck            # 검사만
./gradlew ktlintFormat           # 자동 수정
```

## Code Style

ktlint (intellij_idea 코드 스타일). `.editorconfig`에 설정됨. Git pre-commit hook으로 커밋 시 자동 검사.
wildcard import 금지 (`standard:no-wildcard-imports`).

## Architecture

Hexagonal architecture (Ports & Adapters) 기반. Spring AI MCP Server로 Claude Code와 연동.

### Data Flow

```
docs-register 호출
  → DocSyncService: doc-sources.yml에서 URL 결정
  → DocCrawler: HTML 크롤링 (Jsoup.connect(), 리다이렉트 자동 follow)
  → HtmlToMarkdownConverter: 마크다운 변환
  → DocChunker: 헤더 기반 청킹 (max 1000자, 120자 overlap)
  → EmbeddingModel (Ollama nomic-embed-text, 768차원)
  → QdrantVectorStore: 벡터 + payload 저장
```

### Key Packages

- **`core/model`** — 도메인 모델 (Dependency, DocChunk, DocSource). DocType enum: REFERENCE, MIGRATION, CHANGELOG, GUIDE
- **`core/port`** — 인터페이스 (VectorStore, DocSourceRepository). 구현체 교체 용이
- **`crawler`** — 크롤링 파이프라인. DocCrawler → HtmlToMarkdownConverter → DocChunker
- **`store`** — QdrantVectorStore: Qdrant Java 클라이언트(gRPC) 직접 사용. Spring AI VectorStore 미사용 (하이브리드 검색, payload 업데이트 등 기능 제한으로 인해)
- **`sync`** — DocSyncService: 전체 파이프라인 오케스트레이션
- **`mcp`** — 4개 MCP 도구 (`@McpTool` 어노테이션 기반): docs-register, docs-search, docs-compare, docs-list
- **`config`** — Spring Bean 설정, QdrantProperties (@ConfigurationProperties)

### Qdrant Integration

`io.qdrant:client` gRPC 클라이언트 직접 사용. factory 헬퍼 활용:
- `ConditionFactory.matchKeyword()` — 필터 조건
- `PointIdFactory.id()` — UUID 기반 포인트 ID
- `ValueFactory.value()` — payload 값
- `VectorsFactory.vectors()` — 벡터 데이터
- Filter, PointId 등 protobuf 타입은 `io.qdrant.client.grpc.Common` 패키지에 위치

## Tech Stack

- Kotlin 2.3 / Java 25 / Spring Boot 4.0.5
- Spring AI 2.0.0-M4 (milestone repo 필요)
- Qdrant (gRPC port 6334) + Ollama (port 11434)
- Jsoup, Jackson YAML

## Configuration

- `gradle/libs.versions.toml` — 모든 의존성/플러그인 버전 관리 (Gradle Version Catalog)
- `application.yaml` — 서버 포트, MCP 서버, Qdrant, Ollama 설정
- `doc-sources.yml` — 라이브러리별 문서 URL 템플릿 (`{version}`, `{major}`, `{majorMinor}` 플레이스홀더). `urls` 배열로 fallback URL 지원

## Debugging

Qdrant REST API (port 6333)로 저장된 데이터 직접 조회 가능:
- `curl localhost:6333/collections/doc-chunks` — 컬렉션 상태 확인
- `POST localhost:6333/collections/doc-chunks/points/scroll` — payload 필터 조회. 필드명은 snake_case (`doc_type`, `source_url`, `section_path`)
