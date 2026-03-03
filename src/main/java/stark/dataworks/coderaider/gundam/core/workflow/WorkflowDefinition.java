package stark.dataworks.coderaider.gundam.core.workflow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WorkflowDefinition implements workflow DAG configuration.
 */
@Getter
@Setter
public class WorkflowDefinition
{
    /**
     * Internal state for id; used while coordinating runtime behavior.
     */
    private String id;

    /**
     * Internal state for name; used while coordinating runtime behavior.
     */
    private String name;

    /**
     * Internal state for start vertex id; used while coordinating runtime behavior.
     */
    private String startVertexId;

    /**
     * Internal state for final output key; used while coordinating runtime behavior.
     */
    private String finalOutputKey = "finalOutput";

    /**
     * Internal state for failure strategy; used while coordinating runtime behavior.
     */
    private WorkflowFailureStrategy failureStrategy = WorkflowFailureStrategy.FAIL_FAST;

    /**
     * Internal state for vertices; used while coordinating runtime behavior.
     */
    private List<WorkflowVertexDefinition> vertices = new ArrayList<>();

    /**
     * Performs validate as part of WorkflowDefinition runtime responsibilities.
     */
    public void validate()
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("Workflow id is required");
        }
        if (startVertexId == null || startVertexId.isBlank())
        {
            throw new IllegalArgumentException("Workflow startVertexId is required");
        }
        if (vertices.isEmpty())
        {
            throw new IllegalArgumentException("Workflow vertices are required");
        }

        Map<String, WorkflowVertexDefinition> index = vertices.stream()
            .collect(Collectors.toMap(WorkflowVertexDefinition::getId, v -> v));
        if (!index.containsKey(startVertexId))
        {
            throw new IllegalArgumentException("Workflow startVertexId must exist in vertices");
        }
        for (WorkflowVertexDefinition vertex : vertices)
        {
            if (vertex.getId() == null || vertex.getId().isBlank())
            {
                throw new IllegalArgumentException("Workflow vertex id is required");
            }
            if (vertex.getProcessorType() == null || vertex.getProcessorType().isBlank())
            {
                throw new IllegalArgumentException("Workflow vertex processorType is required: " + vertex.getId());
            }
            List<String> nextVertexIds = vertex.getNextVertexIds() == null ? List.of() : vertex.getNextVertexIds();
            for (String nextVertexId : nextVertexIds)
            {
                if (!index.containsKey(nextVertexId))
                {
                    throw new IllegalArgumentException("Workflow vertex references unknown nextVertexId: " + nextVertexId);
                }
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        if (hasCycle(startVertexId, index, visited, stack))
        {
            throw new IllegalArgumentException("Workflow must be a DAG: cycle detected");
        }

        boolean hasTerminal = vertices.stream().anyMatch(v -> v.getNextVertexIds() == null || v.getNextVertexIds().isEmpty());
        if (!hasTerminal)
        {
            throw new IllegalArgumentException("Workflow must include at least one ending vertex");
        }
    }

    private boolean hasCycle(String id, Map<String, WorkflowVertexDefinition> index, Set<String> visited, Set<String> stack)
    {
        if (stack.contains(id))
        {
            return true;
        }
        if (visited.contains(id))
        {
            return false;
        }

        visited.add(id);
        stack.add(id);
        WorkflowVertexDefinition vertex = index.get(id);
        for (String nextId : vertex.getNextVertexIds())
        {
            if (hasCycle(nextId, index, visited, stack))
            {
                return true;
            }
        }
        stack.remove(id);
        return false;
    }
}
