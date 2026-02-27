# OpenAI Agents SDK vs GUNDAM-core: feature comparison, current progress, and next steps

This document compares the current capabilities of **OpenAI Agents SDK** (reference implementation in `references/openai-agents-python-main`) and **GUNDAM-core**.

## Scope

- OpenAI side: Python SDK in `references/openai-agents-python-main`.
- GUNDAM-core side: Java implementation in `src/main/java` and executable examples in `src/test/java/.../examples`.

## Feature comparison matrix

| Capability | OpenAI Agents SDK | GUNDAM-core status | Notes |
|---|---|---|---|
| Core agent loop (model -> tools -> continue) | ✅ | ✅ | `AgentRunner` already implements loop, retries, hooks, guardrails, handoff flow. |
| Streaming token events | ✅ | ✅ | `runStreamed` and run-event publication are available. |
| Reasoning stream handling | ✅ | ✅ | Reasoning delta events are exposed and consumed in examples. |
| Tool calling (local tools) | ✅ | ✅ | Mature in both projects. |
| MCP tool integration | ✅ | ✅ | GUNDAM-core supports stdio and streamable-http MCP usage in examples. |
| Handoffs / multi-agent orchestration | ✅ | ✅ | `HandoffRouter` + handoff examples are implemented. |
| Guardrails | ✅ | ✅ | Input/output guardrail evaluation exists in runner flow. |
| Structured output | ✅ | ✅ | Prompt and class-schema based structured output are both supported. |
| Tracing / observability | ✅ | ✅ | Trace provider abstractions and tests exist. |
| Session persistence | ✅ | ✅ | Session store abstraction and in-memory implementation available. |
| Memory backend pluggability | ✅ | 🟡 In progress | Now caller-provided `IAgentMemory` is supported per run. |
| Human-in-the-loop approvals | ✅ | ✅ | Tool approval policy and requests are implemented. |
| Realtime voice/webhook workflows | ✅ | ⚪ Not implemented | No equivalent realtime transport layer in core yet. |
| Hosted state / managed context service | ✅ (platform side) | ⚪ Not implemented | Next major integration target. |

Legend: ✅ implemented, 🟡 partial/in progress, ⚪ not implemented.

## Progress summary

1. **Memory pluggability moved forward**: `RunConfiguration` now accepts an optional caller-provided `IAgentMemory`; if omitted, `AgentRunner` defaults to `InMemoryAgentMemory`.
2. **Example streaming hooks were consolidated**: duplicated event listeners in example tests are now centralized via shared helpers for text-only, tool-lifecycle, and reasoning-aware streaming output.
3. **Existing strengths retained**: lifecycle hooks, retries, guardrails, tracing, handoffs, MCP, and structured output remain aligned with the project design goals.

## What’s next (recommended roadmap)

### 1) Context-service integration (next immediate milestone)

- Implement a new `IAgentMemory` backend backed by your context service.
- Keep memory lookup/update semantics consistent with current `IAgentMemory` contract to avoid runner changes.
- Add integration tests that run the same scenarios using:
  - `InMemoryAgentMemory` (baseline)
  - Context-service memory backend (new)

### 2) Memory lifecycle + retention policies

- Add optional policies for:
  - summarization/compaction
  - retention windows
  - per-agent and per-session namespace isolation
- Expose policy hooks in runtime configuration where needed.

### 3) Observability parity improvements

- Expand event taxonomy to include context-store read/write timing and cache-hit metadata.
- Add trace spans around memory backend calls.

### 4) Realtime and webhook-oriented orchestration (longer-term)

- Introduce transport-neutral eventing interfaces (WebSocket/SSE/webhook adapters).
- Keep `AgentRunner` transport-agnostic; place realtime orchestration in adapter modules.

### 5) Production hardening

- Concurrency tests for multi-session + multi-agent runs on non-memory backends.
- Fault-injection tests for network partitions/timeouts on context-service memory.
- Idempotency and exactly-once semantics for session persistence APIs.

## References used

- `README.md`
- `designs/GUNDAM-core-Architecture.md`
- `references/openai-agents-python-main`
