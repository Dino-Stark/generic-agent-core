package stark.dataworks.coderaider.gundam.core.examples;

import com.fasterxml.jackson.databind.ObjectMapper;

import stark.dataworks.coderaider.gundam.core.event.RunEvent;
import stark.dataworks.coderaider.gundam.core.event.RunEventType;
import stark.dataworks.coderaider.gundam.core.streaming.IRunEventListener;
import stark.dataworks.coderaider.gundam.core.streaming.RunEventPublisher;

final class ExampleStreamingPublishers
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExampleStreamingPublishers()
    {
    }

    static RunEventPublisher textOnly()
    {
        RunEventPublisher publisher = new RunEventPublisher();
        publisher.subscribe(new TextDeltaListener());
        return publisher;
    }

    static RunEventPublisher textWithToolLifecycle(String toolPrefix)
    {
        RunEventPublisher publisher = textOnly();
        publisher.subscribe(new ToolLifecycleListener(toolPrefix));
        return publisher;
    }

    static RunEventPublisher reasoningAndText()
    {
        RunEventPublisher publisher = textOnly();
        publisher.subscribe(event -> printReasoningDelta(event, "[reasoning] ", "\n"));
        return publisher;
    }

    static RunEventPublisher reasoningAndTextWithSections()
    {
        RunEventPublisher publisher = new RunEventPublisher();
        publisher.subscribe(new ReasoningSectionListener());
        return publisher;
    }

    private static String toJsonOrString(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        try
        {
            Object normalized = value instanceof String ? OBJECT_MAPPER.readValue((String) value, Object.class) : value;
            return OBJECT_MAPPER.writeValueAsString(normalized);
        }
        catch (Exception e)
        {
            return String.valueOf(value);
        }
    }

    private static void printTextDelta(RunEvent event)
    {
        if (event.getType() != RunEventType.MODEL_RESPONSE_DELTA)
        {
            return;
        }
        String delta = (String) event.getAttributes().get("delta");
        if (delta != null && !delta.isEmpty())
        {
            System.out.print(delta);
            System.out.flush();
        }
    }

    private static void printReasoningDelta(RunEvent event, String prefix, String suffix)
    {
        if (event.getType() != RunEventType.MODEL_REASONING_DELTA)
        {
            return;
        }
        String delta = (String) event.getAttributes().get("delta");
        if (delta != null && !delta.isEmpty())
        {
            System.out.print(prefix + delta + suffix);
            System.out.flush();
        }
    }

    private static final class TextDeltaListener implements IRunEventListener
    {
        @Override
        public void onEvent(RunEvent event)
        {
            printTextDelta(event);
        }
    }

    private static final class ToolLifecycleListener implements IRunEventListener
    {
        private final String toolPrefix;

        private ToolLifecycleListener(String toolPrefix)
        {
            this.toolPrefix = toolPrefix == null ? "" : toolPrefix;
        }

        @Override
        public void onEvent(RunEvent event)
        {
            if (event.getType() == RunEventType.TOOL_CALL_REQUESTED)
            {
                String tool = (String) event.getAttributes().get("tool");
                Object args = event.getAttributes().get("arguments");
                System.out.println("\n[" + toolPrefix + "Tool call: " + tool + " with arguments: " + toJsonOrString(args) + "]");
            }
            else if (event.getType() == RunEventType.TOOL_CALL_COMPLETED)
            {
                String tool = (String) event.getAttributes().get("tool");
                Object result = event.getAttributes().get("result");
                System.out.println("[" + toolPrefix + "Tool completed: " + tool + " with result: " + toJsonOrString(result) + "]");
                System.out.print("Continuing stream: ");
            }
        }
    }

    private static final class ReasoningSectionListener implements IRunEventListener
    {
        private boolean hasReasoning;
        private boolean hasAnswer;

        @Override
        public void onEvent(RunEvent event)
        {
            if (event.getType() == RunEventType.MODEL_REASONING_DELTA)
            {
                String delta = (String) event.getAttributes().get("delta");
                if (delta != null && !delta.isEmpty())
                {
                    if (!hasReasoning)
                    {
                        System.out.println("=== Thinking ===");
                        hasReasoning = true;
                    }
                    System.out.print(delta);
                    System.out.flush();
                }
            }
            else if (event.getType() == RunEventType.MODEL_RESPONSE_DELTA)
            {
                String delta = (String) event.getAttributes().get("delta");
                if (delta != null && !delta.isEmpty())
                {
                    if (!hasAnswer && hasReasoning)
                    {
                        System.out.println("\n\n=== Answer ===");
                        hasAnswer = true;
                    }
                    System.out.print(delta);
                    System.out.flush();
                }
            }
        }
    }
}
