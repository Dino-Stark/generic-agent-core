package stark.dataworks.coderaider.gundam.core.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WorkflowExecutor implements workflow DAG execution.
 */
public class WorkflowExecutor
{
    /**
     * Internal state for definition; used while coordinating runtime behavior.
     */
    private final WorkflowDefinition definition;

    /**
     * Internal state for processor registry; used while coordinating runtime behavior.
     */
    private final WorkflowProcessorRegistry processorRegistry;

    /**
     * Performs workflow executor as part of WorkflowExecutor runtime responsibilities.
     * @param definition The definition used by this operation.
     * @param processorRegistry The processor registry used by this operation.
     */
    public WorkflowExecutor(WorkflowDefinition definition, WorkflowProcessorRegistry processorRegistry)
    {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.definition.validate();
        this.processorRegistry = Objects.requireNonNull(processorRegistry, "processorRegistry");
    }

    /**
     * Runs the primary execution flow, coordinating model/tool work and runtime policies.
     * @param input The input used by this operation.
     * @return The value produced by this operation.
     */
    public WorkflowExecutionResult execute(Map<String, Object> input)
    {
        Map<String, WorkflowVertexDefinition> vertexIndex = definition.getVertices().stream()
            .collect(Collectors.toMap(WorkflowVertexDefinition::getId, v -> v));

        Map<String, Object> state = new HashMap<>(Objects.requireNonNullElse(input, Map.of()));
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(definition.getStartVertexId());
        Set<String> enqueued = new LinkedHashSet<>();
        enqueued.add(definition.getStartVertexId());
        List<String> completedVertices = new ArrayList<>();

        while (!queue.isEmpty())
        {
            String vertexId = queue.removeFirst();
            WorkflowVertexDefinition vertex = vertexIndex.get(vertexId);
            if (vertex == null)
            {
                throw new IllegalStateException("Unknown workflow vertex: " + vertexId);
            }

            WorkflowVertexResult result = executeVertexWithRetry(vertex, state);
            completedVertices.add(vertexId);
            state.putAll(result.getOutputs());

            List<String> nextVertices = result.getNextVertexIds().isEmpty() ? vertex.getNextVertexIds() : result.getNextVertexIds();
            for (String nextVertexId : nextVertices)
            {
                if (enqueued.add(nextVertexId))
                {
                    queue.addLast(nextVertexId);
                }
            }
        }

        Object finalOutputValue = state.getOrDefault(definition.getFinalOutputKey(), "");
        return new WorkflowExecutionResult(String.valueOf(finalOutputValue), state, completedVertices);
    }

    private WorkflowVertexResult executeVertexWithRetry(WorkflowVertexDefinition vertex, Map<String, Object> state)
    {
        int maxAttempts = Math.max(1, vertex.getMaxRetries() + 1);
        RuntimeException latestFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++)
        {
            try
            {
                IWorkflowVertexProcessor processor = processorRegistry.get(vertex.getProcessorType())
                    .orElseThrow(() -> new IllegalStateException("Workflow processor not found: " + vertex.getProcessorType()));
                return processor.process(vertex, state);
            }
            catch (RuntimeException ex)
            {
                latestFailure = ex;
                if (attempt >= maxAttempts)
                {
                    boolean continueOnFailure = vertex.isContinueOnFailure() || definition.getFailureStrategy() == WorkflowFailureStrategy.CONTINUE;
                    if (continueOnFailure)
                    {
                        state.put("workflowError_" + vertex.getId(), ex.getMessage());
                        return WorkflowVertexResult.ofOutputs(Map.of());
                    }
                }
            }
        }

        throw new IllegalStateException("Unexpected workflow execution state", latestFailure);
    }
}
