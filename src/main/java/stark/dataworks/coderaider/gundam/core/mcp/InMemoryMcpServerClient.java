package stark.dataworks.coderaider.gundam.core.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * InMemoryMcpServerClient implements MCP server integration and tool bridging.
 */
public class InMemoryMcpServerClient implements IMcpServerClient
{

    /**
 * In-memory MCP tools grouped by server id.
     */
    private final Map<String, List<McpToolDescriptor>> toolsByServer = new ConcurrentHashMap<>();

    /**
 * Handlers registered for in-memory MCP tool execution.
     */
    private final Map<String, Function<Map<String, Object>, String>> handlers = new ConcurrentHashMap<>();

    /**
 * In-memory MCP resources grouped by server id.
     */
    private final Map<String, List<McpResource>> resourcesByServer = new ConcurrentHashMap<>();

    /**
 * In-memory MCP resource templates grouped by server id.
     */
    private final Map<String, List<McpResourceTemplate>> templatesByServer = new ConcurrentHashMap<>();

    /**
     * Registers tools.
     * @param serverId MCP server identifier.
     * @param tools tools.
     */
    public void registerTools(String serverId, List<McpToolDescriptor> tools)
    {
        toolsByServer.put(serverId, tools);
    }

    /**
     * Registers handler.
     * @param serverId MCP server identifier.
     * @param toolName tool name.
     * @param Function<Map<String function<map<string.
     * @param Object> object>.
     * @param handler handler.
     */
    public void registerHandler(String serverId, String toolName, Function<Map<String, Object>, String> handler)
    {
        handlers.put(serverId + "::" + toolName, handler);
    }

    /**
     * Registers resources.
     * @param serverId MCP server identifier.
     * @param resources resources.
     */
    public void registerResources(String serverId, List<McpResource> resources)
    {
        resourcesByServer.put(serverId, resources);
    }

    /**
     * Registers resource templates.
     * @param serverId MCP server identifier.
     * @param templates templates.
     */
    public void registerResourceTemplates(String serverId, List<McpResourceTemplate> templates)
    {
        templatesByServer.put(serverId, templates);
    }

    /**
     * Lists tools.
     * @param config run configuration.
     * @return List of mcp tool descriptor values.
     */
    @Override
    public List<McpToolDescriptor> listTools(McpServerConfiguration config)
    {
        return toolsByServer.getOrDefault(config.getServerId(), List.of());
    }

    /**
     * Calls tool.
     * @param config run configuration.
     * @param toolName tool name.
     * @param Map<String map<string.
     * @param args args.
     * @return Result text returned by this operation.
     */
    @Override
    public String callTool(McpServerConfiguration config, String toolName, Map<String, Object> args)
    {
        Function<Map<String, Object>, String> fn = handlers.get(config.getServerId() + "::" + toolName);
        if (fn == null)
        {
            return "MCP tool handler missing: " + toolName;
        }
        return fn.apply(args);
    }

    /**
     * Lists resources.
     * @param config run configuration.
     * @return List of mcp resource values.
     */
    @Override
    public List<McpResource> listResources(McpServerConfiguration config)
    {
        return resourcesByServer.getOrDefault(config.getServerId(), List.of());
    }

    /**
     * Lists resource templates.
     * @param config run configuration.
     * @return List of mcp resource template values.
     */
    @Override
    public List<McpResourceTemplate> listResourceTemplates(McpServerConfiguration config)
    {
        return templatesByServer.getOrDefault(config.getServerId(), List.of());
    }

    /**
     * Reads resource.
     * @param config run configuration.
     * @param uri resource URI.
     * @return mcp resource result.
     */
    @Override
    public McpResource readResource(McpServerConfiguration config, String uri)
    {
        return listResources(config).stream()
            .filter(r -> r.uri().equals(uri))
            .findFirst()
            .orElse(new McpResource(uri, "text/plain", ""));
    }
}
