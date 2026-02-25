package stark.dataworks.coderaider.gundam.core.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiToolAdaptersTest
{
    @Test
    void adaptsToolObjectsAnnotatedWithSpringAiTool()
    {
        List<ITool> tools = SpringAiToolAdapters.fromToolObjects(new WeatherTools());

        assertEquals(1, tools.size());
        ITool tool = tools.get(0);
        assertEquals("weather_lookup", tool.definition().getName());
        assertFalse(tool.definition().getDescription().isBlank());
        assertEquals(2, tool.definition().getParameters().size());

        ToolParameterSchema city = tool.definition().getParameters().stream()
            .filter(parameter -> "city".equals(parameter.getName()))
            .findFirst()
            .orElseThrow();
        assertTrue(city.isRequired());
        assertEquals("string", city.getType());

        String output = tool.execute(Map.of("city", "Shanghai", "unit", "celsius"));
        assertEquals("Shanghai:celsius", output);
    }

    @Test
    void toolRegistryRegistersSpringToolObjects()
    {
        ToolRegistry registry = new ToolRegistry();
        registry.registerSpringToolObjects(new WeatherTools());

        ITool tool = registry.get("weather_lookup").orElse(null);
        assertNotNull(tool);
        assertEquals("Tokyo:celsius", tool.execute(Map.of("city", "Tokyo")));
    }

    static class WeatherTools
    {
        @Tool(name = "weather_lookup", description = "Look up weather", returnDirect = false)
        public String weather(
            @ToolParam(description = "city name") String city,
            @ToolParam(required = false, description = "temperature unit") String unit)
        {
            String finalUnit = unit == null || unit.isBlank() ? "celsius" : unit;
            return city + ":" + finalUnit;
        }
    }
}
