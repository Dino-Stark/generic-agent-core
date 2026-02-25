package stark.dataworks.coderaider.gundam.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SpringAiToolAdapters provides high-level compatibility bridges from Spring AI tool APIs to GUNDAM tools.
 */
public final class SpringAiToolAdapters
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private SpringAiToolAdapters()
    {
    }

    /**
     * Adapts tool objects that use Spring AI {@code @Tool} annotations into GUNDAM {@link ITool}s.
     * @param toolObjects Objects containing methods annotated with Spring AI {@code @Tool}.
     * @return Adapted tool list.
     */
    public static List<ITool> fromToolObjects(Object... toolObjects)
    {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
            .toolObjects(toolObjects)
            .build()
            .getToolCallbacks();
        return fromToolCallbacks(callbacks);
    }

    /**
     * Adapts Spring AI {@link ToolCallback} instances into GUNDAM {@link ITool}s.
     * @param callbacks Spring AI callbacks.
     * @return Adapted tool list.
     */
    public static List<ITool> fromToolCallbacks(ToolCallback... callbacks)
    {
        List<ITool> tools = new ArrayList<>();
        if (callbacks == null)
        {
            return tools;
        }

        for (ToolCallback callback : callbacks)
        {
            tools.add(new SpringAiToolCallbackAdapter(callback));
        }
        return tools;
    }

    private static class SpringAiToolCallbackAdapter implements ITool
    {
        private final ToolCallback callback;
        private final ToolDefinition definition;

        private SpringAiToolCallbackAdapter(ToolCallback callback)
        {
            this.callback = callback;
            this.definition = toDefinition(callback);
        }

        @Override
        public ToolDefinition definition()
        {
            return definition;
        }

        @Override
        public String execute(Map<String, Object> input)
        {
            try
            {
                String jsonArguments = MAPPER.writeValueAsString(input == null ? Map.of() : input);
                String value = callback.call(jsonArguments);
                try
                {
                    JsonNode resultNode = MAPPER.readTree(value);
                    return resultNode.isTextual() ? resultNode.asText() : value;
                }
                catch (Exception ignored)
                {
                    return value;
                }
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("Failed to execute Spring AI tool callback: " + definition.getName(), ex);
            }
        }

        private static ToolDefinition toDefinition(ToolCallback callback)
        {
            org.springframework.ai.tool.definition.ToolDefinition springDefinition = callback.getToolDefinition();
            return new ToolDefinition(
                springDefinition.name(),
                springDefinition.description(),
                parseParameters(springDefinition.inputSchema()));
        }

        private static List<ToolParameterSchema> parseParameters(String inputSchema)
        {
            try
            {
                JsonNode schema = MAPPER.readTree(inputSchema == null ? "{}" : inputSchema);
                JsonNode properties = schema.path("properties");
                Set<String> requiredNames = new HashSet<>();
                JsonNode required = schema.path("required");
                if (required.isArray())
                {
                    for (JsonNode requiredName : required)
                    {
                        requiredNames.add(requiredName.asText());
                    }
                }

                List<ToolParameterSchema> parameters = new ArrayList<>();
                properties.fields().forEachRemaining(entry ->
                {
                    JsonNode node = entry.getValue();
                    parameters.add(new ToolParameterSchema(
                        entry.getKey(),
                        node.path("type").asText("string"),
                        requiredNames.contains(entry.getKey()),
                        node.path("description").asText("")));
                });
                return parameters;
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("Failed to parse Spring AI tool input schema", ex);
            }
        }
    }
}
