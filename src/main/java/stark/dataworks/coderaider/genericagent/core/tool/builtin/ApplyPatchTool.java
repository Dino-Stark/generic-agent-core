package stark.dataworks.coderaider.genericagent.core.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchOperation;
import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchResult;
import stark.dataworks.coderaider.genericagent.core.editor.DiffApplier;
import stark.dataworks.coderaider.genericagent.core.editor.IApplyPatchEditor;
import stark.dataworks.coderaider.genericagent.core.tool.ITool;
import stark.dataworks.coderaider.genericagent.core.tool.ToolDefinition;
import stark.dataworks.coderaider.genericagent.core.tool.ToolParameterSchema;

/**
 * ApplyPatchTool is a hosted tool that lets the model request file mutations via unified diffs.
 * The editor implementation handles the actual file operations.
 */
public class ApplyPatchTool implements ITool
{
    private static final String TOOL_NAME = "apply_patch";
    private static final String TOOL_DESCRIPTION = "Hosted apply_patch tool. Lets the model request file mutations via unified diffs.";

    private final IApplyPatchEditor editor;
    private final boolean needsApproval;
    private final ToolDefinition definition;

    public ApplyPatchTool(IApplyPatchEditor editor)
    {
        this(editor, false);
    }

    public ApplyPatchTool(IApplyPatchEditor editor, boolean needsApproval)
    {
        this.editor = editor;
        this.needsApproval = needsApproval;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition()
    {
        return new ToolDefinition(
            TOOL_NAME,
            TOOL_DESCRIPTION,
            List.of(
                new ToolParameterSchema("operation", "object", false,
                    "Apply patch operation object: {\"type\":\"create_file|update_file|delete_file\",\"path\":\"...\",\"diff\":\"...\"}"),
                new ToolParameterSchema("type", "string", false,
                    "Fallback flat payload form. Canonical values: create_file, update_file, delete_file."),
                new ToolParameterSchema("path", "string", false, "Fallback flat payload form."),
                new ToolParameterSchema("diff", "string", false, "Required for create_file/update_file."),
                new ToolParameterSchema("raw", "string", false,
                    "Fallback raw JSON text when model emits serialized tool payloads.")
            )
        );
    }

    @Override
    public ToolDefinition definition()
    {
        return definition;
    }

    @Override
    public String execute(Map<String, Object> input)
    {
        Map<String, Object> operation = extractOperation(input);
        if (operation == null)
        {
            return failedResult("Apply patch call is missing an operation payload.");
        }

        String type = stringify(operation.get("type"));
        if (!"create_file".equals(type) && !"update_file".equals(type) && !"delete_file".equals(type))
        {
            return failedResult("Unknown apply_patch operation: " + type);
        }

        Object pathObj = operation.get("path");
        if (!(pathObj instanceof String) || ((String) pathObj).isEmpty())
        {
            return failedResult("Apply patch operation is missing a valid path.");
        }

        String path = (String) pathObj;
        String diff = null;
        if ("create_file".equals(type) || "update_file".equals(type))
        {
            Object diffObj = operation.get("diff");
            if (!(diffObj instanceof String) || ((String) diffObj).isEmpty())
            {
                return failedResult("Apply patch operation " + type + " is missing the required diff payload.");
            }
            diff = (String) diffObj;
        }

        try
        {
            ApplyPatchResult result;
            switch (type)
            {
                case "create_file":
                    result = editor.createFile(ApplyPatchOperation.createFile(path, diff));
                    break;
                case "update_file":
                    result = editor.updateFile(ApplyPatchOperation.updateFile(path, diff));
                    break;
                case "delete_file":
                    result = editor.deleteFile(ApplyPatchOperation.deleteFile(path));
                    break;
                default:
                    return failedResult("Unknown apply_patch operation: " + type);
            }

            if (result == null)
            {
                return completedResult(null);
            }

            boolean completed = result.getStatus() == null || result.getStatus() == ApplyPatchResult.Status.COMPLETED;
            return completed ? completedResult(result.getOutput()) : failedResult(result.getOutput());
        }
        catch (Exception ex)
        {
            return failedResult(ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOperation(Map<String, Object> input)
    {
        Object opObj = input.get("operation");
        if (opObj instanceof Map)
        {
            return (Map<String, Object>) opObj;
        }

        if (input.containsKey("type") && input.containsKey("path"))
        {
            return input;
        }

        Object rawObj = input.get("raw");
        if (rawObj instanceof String)
        {
            Map<String, Object> parsed = parseOperationFromRaw((String) rawObj);
            if (parsed != null)
            {
                return parsed;
            }
        }

        for (Object value : input.values())
        {
            if (value instanceof String)
            {
                Map<String, Object> parsed = parseOperationFromRaw((String) value);
                if (parsed != null)
                {
                    return parsed;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOperationFromRaw(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        String jsonToParse = raw.trim();

        try
        {
            for (int attempt = 0; attempt < 8; attempt++)
            {
                JsonNode node = mapper.readTree(jsonToParse);
                Map<String, Object> operation = extractOperationFromNode(mapper, node);
                if (operation != null)
                {
                    return operation;
                }

                if (node.isTextual())
                {
                    jsonToParse = node.asText();
                    continue;
                }
                break;
            }
        }
        catch (Exception ignored)
        {
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOperationFromNode(ObjectMapper mapper, JsonNode node)
    {
        if (node == null || !node.isObject())
        {
            return null;
        }

        if (node.has("operation") && node.get("operation").isObject())
        {
            JsonNode opNode = node.get("operation");
            if (opNode.has("type") && opNode.has("path"))
            {
                return mapper.convertValue(opNode, Map.class);
            }
        }

        if (node.has("type") && node.has("path"))
        {
            return mapper.convertValue(node, Map.class);
        }

        return null;
    }

    private String stringify(Object value)
    {
        return value == null ? "None" : String.valueOf(value);
    }

    private String completedResult(String output)
    {
        return "{\"status\":\"completed\",\"output\":" + jsonString(output) + "}";
    }

    private String failedResult(String output)
    {
        return "{\"status\":\"failed\",\"output\":" + jsonString(output) + "}";
    }

    private String jsonString(String value)
    {
        if (value == null)
        {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    public boolean needsApproval()
    {
        return needsApproval;
    }

    public IApplyPatchEditor getEditor()
    {
        return editor;
    }

    public static String applyDiff(String original, String diff)
    {
        return DiffApplier.applyDiff(original, diff);
    }

    public static String applyCreateDiff(String diff)
    {
        return DiffApplier.applyDiff("", diff, DiffApplier.ApplyDiffMode.CREATE);
    }
}
