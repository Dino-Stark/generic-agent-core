package stark.dataworks.coderaider.gundam.core.workflow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import stark.dataworks.coderaider.gundam.core.workflow.processor.JoinFieldsWorkflowProcessor;
import stark.dataworks.coderaider.gundam.core.workflow.processor.TemplateRenderWorkflowProcessor;

import java.util.List;
import java.util.Map;

public class WorkflowExecutorTest
{
    @Test
    public void shouldRunHardcodedDAG()
    {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId("wf-hardcoded");
        definition.setName("Hardcoded");
        definition.setStartVertexId("v1");
        definition.setFinalOutputKey("finalOutput");

        WorkflowVertexDefinition v1 = new WorkflowVertexDefinition();
        v1.setId("v1");
        v1.setProcessorType("template");
        v1.setConfig(Map.of("template", "Task: {{task}}", "outputKey", "taskLine"));
        v1.setNextVertexIds(List.of("v2"));

        WorkflowVertexDefinition v2 = new WorkflowVertexDefinition();
        v2.setId("v2");
        v2.setProcessorType("template");
        v2.setConfig(Map.of("template", "Priority: {{priority}}", "outputKey", "priorityLine"));
        v2.setNextVertexIds(List.of("v3"));

        WorkflowVertexDefinition v3 = new WorkflowVertexDefinition();
        v3.setId("v3");
        v3.setProcessorType("join");
        v3.setConfig(Map.of("fields", List.of("taskLine", "priorityLine"), "outputKey", "finalOutput", "separator", " | "));

        definition.setVertices(List.of(v1, v2, v3));

        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry();
        registry.register("template", new TemplateRenderWorkflowProcessor());
        registry.register("join", new JoinFieldsWorkflowProcessor());

        WorkflowExecutionResult result = new WorkflowExecutor(definition, registry).execute(Map.of("task", "Refactor API", "priority", "P1"));

        Assertions.assertEquals("Task: Refactor API | Priority: P1", result.getFinalOutput());
        Assertions.assertEquals(List.of("v1", "v2", "v3"), result.getCompletedVertexIds());
    }

    @Test
    public void shouldLoadWorkflowFromJson()
    {
        String json = """
            {
              "id": "wf-json",
              "name": "Json workflow",
              "startVertexId": "load",
              "finalOutputKey": "finalOutput",
              "vertices": [
                {
                  "id": "load",
                  "processorType": "template",
                  "config": {"template": "Input={{input}}", "outputKey": "line1"},
                  "nextVertexIds": ["finish"]
                },
                {
                  "id": "finish",
                  "processorType": "join",
                  "config": {"fields": ["line1"], "outputKey": "finalOutput"}
                }
              ]
            }
            """;

        WorkflowDefinition definition = WorkflowDefinitionLoader.fromJson(json);
        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry();
        registry.register("template", new TemplateRenderWorkflowProcessor());
        registry.register("join", new JoinFieldsWorkflowProcessor());

        WorkflowExecutionResult result = new WorkflowExecutor(definition, registry).execute(Map.of("input", "hello"));
        Assertions.assertEquals("Input=hello", result.getFinalOutput());
    }
}
