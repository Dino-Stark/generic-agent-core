package stark.dataworks.coderaider.genericagent.core.react;

public final class ReActPromptComposer
{
    private static final String DEFAULT_REACT_INSTRUCTIONS = """
        ReAct mode is enabled.
        
        WORKFLOW (follow strictly):
        1. INSPECT: Read files/analyze problem (one tool call)
        2. ACT: Apply fix immediately (one tool call)
        3. VERIFY: Run verification (one tool call)
        4. STOP: Output brief summary when done
        
        PATCH TOOL USAGE (apply_patch):
        - Format: {"type":"update_file","path":"FileName.java","diff":"- old\\n+ new"}
        - MINIMAL DIFF: Only include lines that change (use - for remove, + for add)
        - EXACT MATCH: '-' lines must match file content exactly (copy-paste from file)
        - NO CONTEXT NEEDED: Simple -/+ pairs work best
        
        EXAMPLES:
        Single change: {"type":"update_file","path":"Test.java","diff":"- return a - b;\\n+ return a + b;"}
        Multiple: {"type":"update_file","path":"Test.java","diff":"- line1\\n+ new1\\n- line2\\n+ new2"}
        
        OUTPUT FORMAT (final answer only):
        ## Summary
        - Problem: <one sentence>
        - Fix: <what changed>
        - Verification: <result>
        
        RULES:
        - NO internal monologue or thinking process
        - NO step-by-step explanations
        - NO verbose descriptions
        - Act like a professional code editor: read → fix → verify → done
        """;

    private static final String INTENT_RECOGNITION_PREFIX = """
        [Intent]
        Task: <goal>
        Files: <paths>
        Tools: <names>
        
        """;

    private ReActPromptComposer()
    {
    }

    public static String compose(String baseSystemPrompt, String customInstructions, boolean reactEnabled)
    {
        if (!reactEnabled)
        {
            return baseSystemPrompt;
        }

        String normalizedBase = baseSystemPrompt == null ? "" : baseSystemPrompt;
        String instructions = (customInstructions == null || customInstructions.isBlank())
            ? DEFAULT_REACT_INSTRUCTIONS
            : customInstructions;

        return INTENT_RECOGNITION_PREFIX + normalizedBase + System.lineSeparator() + System.lineSeparator() + instructions;
    }
}
