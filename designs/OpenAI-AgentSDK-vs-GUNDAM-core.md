# OpenAI Agents SDK vs GUNDAM-core: feature comparison, current progress, and next steps

This document compares the current capabilities of **OpenAI Agents SDK** (reference implementation in `references/openai-agents-python-main`) and **GUNDAM-core**.

## Scope

- OpenAI side: Python SDK in `references/openai-agents-python-main`.
- GUNDAM-core side: Java implementation in `src/main/java` and executable examples in `src/test/java/.../examples`.

## Feature comparison matrix

| Capability | OpenAI Agents SDK | GUNDAM-core status | Notes |
|---|---|---|---|
| Core agent loop (model -> tools -> continue) | ✅ | ✅ | `AgentRunner` implements loop, retries, hooks, guardrails, handoff flow. |
| Streaming token events | ✅ | ✅ | `runStreamed` and run-event publication available. |
| Reasoning stream handling | ✅ | ✅ | Reasoning delta events exposed and consumed in examples. |
| Tool calling (local tools) | ✅ | ✅ | Mature in both projects. |
| MCP tool integration | ✅ | ✅ | Supports stdio, HTTP, and streamable-http MCP usage. |
| Handoffs / multi-agent orchestration | ✅ | ✅ | `HandoffRouter` + handoff examples implemented. |
| Guardrails | ✅ | ✅ | Input/output guardrail evaluation in runner flow. |
| Structured output | ✅ | ✅ | Prompt and class-schema based structured output supported. |
| Tracing / observability | ✅ | ✅ | Trace provider abstractions, distributed tracing, and processors exist. |
| Session persistence | ✅ | ✅ | Session store abstraction and in-memory implementation available. |
| Memory backend pluggability | ✅ | ✅ | Caller-provided `IAgentMemory` supported per run. |
| Context-service memory backend | ✅ | ✅ | `ContextServiceAgentMemory` with `IContextServiceMemoryStore` implemented. |
| Memory lifecycle policies | ✅ | ✅ | `MemoryLifecyclePolicy`, sliding window, summarization, composite policies. |
| Human-in-the-loop approvals | ✅ | ✅ | Tool approval policy and requests implemented. |
| Multi-provider handoffs | ✅ | ✅ | `LlmClientRegistry` supports multiple providers in single session. |
| RAG / Vector store | ✅ | ✅ | `RagService`, `InMemoryVectorStore`, `EmbeddingModel` implemented. |
| Tool output trimming | ✅ | ✅ | `ToolOutputTrimmer` extension implemented. |
| Handoff history filters | ✅ | ✅ | `HandoffHistoryFilters` utilities implemented. |
| Computer tool | ✅ | 🟡 Partial | `ComputerTool` stub exists, full browser automation not implemented. |
| Voice pipeline | ✅ | 🟡 Partial | `IVoicePipeline` interface exists, full workflow not implemented. |
| Multimodal generation (audio/image/video) | ✅ | 🟡 Partial | Interfaces exist (`IAudioGenerator`, `IImageGenerator`, `IVideoGenerator`), no providers. |
| Agent tool state tracking | ✅ | ⚪ Not implemented | OpenAI has `AgentToolUseTracker` for model_settings resets. |
| Editor (file diff/patch) | ✅ | ⚪ Not implemented | OpenAI has `ApplyPatchEditor` for file operations. |
| Realtime voice/webhook workflows | ✅ | ⚪ Partial | Interfaces exist (`IRealtimeClient`, `IRealtimeSession`), no full implementation. |
| Codex/experimental features | ✅ | ⚪ Not implemented | OpenAI has experimental codex module. |
| Error handling registry | ✅ | ✅ | `RunErrorHandlers`, `IRunErrorHandler`, `RunErrorKind` implemented. |
| Spring AI compatibility | ⚪ | ✅ | `SpringAiToolAdapters`, `SpringAiChatClientLlmClient` unique to GUNDAM. |

Legend: ✅ implemented, 🟡 partial/in progress, ⚪ not implemented.

## Progress summary

1. **Memory pluggability completed**: `RunConfiguration` accepts optional caller-provided `IAgentMemory`; `ContextServiceAgentMemory` with `IContextServiceMemoryStore` implemented.
2. **Memory lifecycle policies implemented**: `MemoryLifecyclePolicy` interface with `SlidingWindowMemoryPolicy`, `SummarizingMemoryPolicy`, and `CompositeMemoryLifecyclePolicy` for compaction/retention/isolation.
3. **Example streaming hooks consolidated**: Duplicated event listeners centralized via shared helpers for text-only, tool-lifecycle, and reasoning-aware streaming output.
4. **Multi-provider handoffs**: `LlmClientRegistry` enables cross-provider handoffs (Example19 demonstrates ModelScope + Seed/Doubao in single session).
5. **RAG foundation**: `RagService`, `InMemoryVectorStore`, `EmbeddingModel`, and `SimpleHashEmbeddingModel` implemented (Example20 demonstrates retrieval-augmented generation).
6. **Tool output trimming**: `ToolOutputTrimmer` extension matches OpenAI's `ToolOutputTrimmer` for reducing token usage from large tool outputs.
7. **Handoff history filters**: `HandoffHistoryFilters` utilities for filtering conversation history during agent handoffs.
8. **Distributed tracing**: `DistributedTraceProvider`, `DistributedTraceCollector`, and tracing processors (Example13, Example14) provide observability.
9. **Error handling registry**: `RunErrorHandlers`, `IRunErrorHandler`, and `RunErrorKind` provide typed error classification and handler dispatch.
10. **Spring AI compatibility**: `SpringAiToolAdapters` and `SpringAiChatClientLlmClient` enable Spring AI `@Tool` annotation compatibility.
11. **Multimodal interfaces**: `IAudioGenerator`, `IImageGenerator`, `IVideoGenerator`, `IMultimodalLlmClient` interfaces defined for future provider implementations.
12. **Realtime interfaces**: `IRealtimeClient`, `IRealtimeSession`, `IRealtimeEventListener`, and transport abstractions (SSE, webhook) defined.
13. **Existing strengths retained**: lifecycle hooks, retries, guardrails, tracing, handoffs, MCP, and structured output remain aligned with design goals.

## New examples demonstrating capabilities

The following examples have been added to demonstrate new features:

- **Example13TracingSingleTurnTest**: Demonstrates distributed tracing with `DistributedTraceProvider` collecting span events.
- **Example14TracingMultiRoundTest**: Multi-turn distributed tracing with span relationships.
- **Example15IntegralImageQuestionTest**: Multimodal image understanding with `IMultimodalLlmClient`.
- **Example16KiteImageDescriptionTest**: Image description generation.
- **Example17FlyingDragonTextToImageTest**: Text-to-image generation with `IImageGenerator`.
- **Example18FlyingCatStyleTransferTest**: Style transfer image generation.
- **Example19MultiRoundMultiProviderHandoffStreamingTest**: Cross-provider handoffs (ModelScope + Seed/Doubao) in single session.
- **Example20RagVectorStoreStreamingTest**: RAG with `RagService`, `InMemoryVectorStore`, and `SimpleHashEmbeddingModel`.

## What’s next (recommended roadmap)

### 1) Agent tool state tracking (immediate priority)

- Implement `AgentToolUseTracker` equivalent to track which tools each agent has used.
- Enable model_settings resets based on tool usage history (OpenAI uses this for gpt-5 reasoning models).
- Add serialization/hydration for tool state across session persistence.

### 2) Editor / file operations (high priority)

- Implement `ApplyPatchEditor` interface for file diff/patch operations.
- Support create_file, update_file, delete_file operations with diff application.
- Integrate with tool registry for code editing workflows.

### 3) Computer tool completion (medium priority)

- Complete `ComputerTool` implementation with full browser automation.
- Implement screenshot, click, double_click, scroll, type, wait, move operations.
- Support multiple environments (mac, windows, ubuntu, browser).

### 4) Realtime workflow implementation (medium priority)

- Complete `IRealtimeClient` and `IRealtimeSession` implementations.
- Implement SSE and webhook transport adapters.
- Add voice-to-text and text-to-voice pipeline integration.

### 5) Multimodal provider implementations (medium priority)

- Implement providers for `IAudioGenerator`, `IImageGenerator`, `IVideoGenerator`.
- Integrate with `IMultimodalLlmClient` for multimodal message handling.
- Add examples for audio/image/video generation workflows.

### 6) Codex/experimental features (lower priority)

- Evaluate OpenAI's experimental codex module for relevant patterns.
- Consider implementing codex tool interfaces if applicable to use cases.

### 7) Production hardening

- Concurrency tests for multi-session + multi-agent runs on non-memory backends.
- Fault-injection tests for network partitions/timeouts on context-service memory.
- Idempotency and exactly-once semantics for session persistence APIs.
- Performance benchmarks for memory lifecycle policies and RAG operations.

## References used

- `README.md`
- `designs/GUNDAM-core-Architecture.md`
- `references/openai-agents-python-main`
