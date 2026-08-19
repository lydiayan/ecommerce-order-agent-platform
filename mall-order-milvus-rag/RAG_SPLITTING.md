# RAG document splitting

The module exposes seven strategies through `AbstractRagDocumentSplitter` and
`DocumentSplitterRegistry`:

- `FIXED_SIZE`: strict estimated-token windows.
- `SLIDING_WINDOW`: fixed windows with token overlap.
- `RECURSIVE`: paragraph, line, sentence, clause, then whitespace boundaries.
- `STRUCTURE_AWARE`: Chinese chapter/article headings, Markdown, numbered headings, and HTML DOM blocks.
- `SEMANTIC`: adjacent-sentence embedding distance with min/target/max token constraints.
- `PARENT_CHILD`: content-aware parent and child chunks in one Milvus collection.
- `CONTENT_TYPE_AWARE`: routes plain text, PDF, Markdown, HTML, FAQ, table, and code content.

The default is `CONTENT_TYPE_AWARE`. Configure server-managed limits under
`rag.chunk` in `rag.yml`. Import requests may override only `documentId`,
`strategy`, and `contentType`. The PDF multipart endpoint accepts optional
`documentId` and `strategy` parameters; its content type is always `PDF`.

## Strategy configuration

Chunk settings are grouped by the strategy that consumes them. The common
`strategy` and `max-num-chunks` settings apply to the registry and every
splitter. `STRUCTURE_AWARE` and `CONTENT_TYPE_AWARE` reuse the recursive
settings because both delegate oversized regions to recursive splitting.

```yaml
rag:
  chunk:
    strategy: CONTENT_TYPE_AWARE
    max-num-chunks: 10000
    fixed-size:
      max-tokens: 250
    sliding-window:
      max-tokens: 250
      overlap-tokens: 50
    recursive:
      min-tokens: 80
      max-tokens: 400
      overlap-tokens: 40
    semantic:
      min-tokens: 100
      target-tokens: 250
      max-tokens: 500
      batch-size: 64
      max-sentences: 2000
      boundary-percentile: 0.85
    parent-child:
      parent-tokens: 800
      child-tokens: 200
      child-overlap-tokens: 40
```

Invalid size relationships fail application startup. Removed flat properties
such as `chunk-size`, `recursive-max-tokens`, and `semantic-target-tokens` are
also rejected instead of being silently ignored.

## Milvus v3 migration

The custom schema is owned by `RagService`; generic Spring AI schema
initialization is disabled. The default collection is `mall_rag_v3`, and
`RAG_COLLECTION_NAME` may override it. The service never changes or deletes
the legacy `mall_rag_collection`.

Existing chunks cannot be upgraded reliably because they do not contain the
original document structure or parent-child relationships. Re-submit the
original text or PDF through an import endpoint. Supplying a stable
`documentId` makes an import idempotent and removes stale chunks when that
document is replaced.

## Verification

Normal tests use a deterministic fake embedding model:

```bash
./mvnw -pl mall-order-milvus-rag -am test
```

Start local infrastructure and run the isolated Milvus test:

```bash
./scripts/dev-up.sh
RUN_MILVUS_IT=true ./mvnw -pl mall-order-milvus-rag -am \
  -Dtest=RagMilvusIntegrationTest \
  -Dsurefire.excludes= \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The integration test creates and deletes only a uniquely named temporary
collection. It does not use DashScope or alter business collections.
