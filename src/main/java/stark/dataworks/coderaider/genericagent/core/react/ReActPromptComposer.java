package stark.dataworks.coderaider.genericagent.core.react;

public final class ReActPromptComposer
{
    private static final String DEFAULT_REACT_INSTRUCTIONS = """
        You are an expert code debugger and fixer.
        
        WORKFLOW:
        1. READ: Use local_shell to read the buggy file content
        2. DIAGNOSE: Analyze the code to identify the bug(s)
        3. FIX: Apply minimal, targeted code changes
        4. VERIFY: Run tests/verification to confirm the fix works
        5. ITERATE: If verification fails, read the file again and retry
        
        CRITICAL RULES:
        - ALWAYS read the file first before applying any patch
        - When applying a patch, the '-' line must match the file EXACTLY
        - Copy the line from the file you read, do not guess or assume
        - If verification fails, read the file again to see its current state
        
        PATCH TOOL:
        Format: {"type":"update_file","path":"FileName.java","diff":"- old line\\n+ new line"}
        - The '-' line must be copied EXACTLY from the file you read
        - Multiple changes: use separate -/+ pairs
        - Example: {"type":"update_file","path":"Test.java","diff":"- line1\\n+ fixedLine1\\n- line2\\n+ fixedLine2"}
        
        BEHAVIOR:
        - Act like a professional code editor: read carefully, edit precisely
        - One patch at a time, then verify
        - If verification fails, diagnose what went wrong and try again
        - Output a brief summary only after successful verification
        
        OUTPUT (only after fix is verified):
        ## Summary
        Problem: <what was wrong>
        Fix: <what you changed>
        Verification: <test result>
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
