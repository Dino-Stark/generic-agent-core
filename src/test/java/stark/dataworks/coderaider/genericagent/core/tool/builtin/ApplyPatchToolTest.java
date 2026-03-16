package stark.dataworks.coderaider.genericagent.core.tool.builtin;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchOperation;
import stark.dataworks.coderaider.genericagent.core.editor.ApplyPatchResult;
import stark.dataworks.coderaider.genericagent.core.editor.IApplyPatchEditor;

class ApplyPatchToolTest
{
    @Test
    void testToolDefinition()
    {
        IApplyPatchEditor editor = new TestEditor();
        ApplyPatchTool tool = new ApplyPatchTool(editor);

        assertEquals("apply_patch", tool.definition().getName());
        assertTrue(tool.definition().getDescription().contains("Hosted apply_patch tool"));
        assertTrue(tool.definition().getParameters().size() >= 1);
        assertEquals("operation", tool.definition().getParameters().get(0).getName());
        assertFalse(tool.needsApproval());
    }

    @Test
    void testCreateFile()
    {
        TestEditor editor = new TestEditor();
        ApplyPatchTool tool = new ApplyPatchTool(editor);

        String result = tool.execute(Map.of(
            "operation", Map.of(
                "type", "create_file",
                "path", "/test/file.txt",
                "diff", "+line1\n+line2"
            )
        ));

        assertEquals("{\"status\":\"completed\",\"output\":\"Created /test/file.txt\"}", result);
        assertEquals(1, editor.getCreateCount());
    }

    @Test
    void testUpdateFile()
    {
        TestEditor editor = new TestEditor();
        ApplyPatchTool tool = new ApplyPatchTool(editor);

        String result = tool.execute(Map.of(
            "operation", Map.of(
                "type", "update_file",
                "path", "/test/file.txt",
                "diff", "-old\n+new"
            )
        ));

        assertEquals("{\"status\":\"completed\",\"output\":\"Updated /test/file.txt\"}", result);
        assertEquals(1, editor.getUpdateCount());
    }

    @Test
    void testDeleteFile()
    {
        TestEditor editor = new TestEditor();
        ApplyPatchTool tool = new ApplyPatchTool(editor);

        String result = tool.execute(Map.of(
            "operation", Map.of(
                "type", "delete_file",
                "path", "/test/file.txt"
            )
        ));

        assertEquals("{\"status\":\"completed\",\"output\":\"Deleted /test/file.txt\"}", result);
        assertEquals(1, editor.getDeleteCount());
    }


    @Test
    void testFlatPayloadFallback()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of(
            "type", "update_file",
            "path", "a.txt",
            "diff", "-a\n+b"
        ));
        assertEquals("{\"status\":\"completed\",\"output\":\"Updated a.txt\"}", result);
    }

    @Test
    void testRawPayloadFallback()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of("raw", "{\"operation\":{\"type\":\"update_file\",\"path\":\"a.txt\",\"diff\":\"-a\\n+b\"}}"));
        assertEquals("{\"status\":\"completed\",\"output\":\"Updated a.txt\"}", result);
    }

    @Test
    void testEscapedRawPayloadFallback()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String raw = "\"{\\\"type\\\":\\\"update_file\\\",\\\"path\\\":\\\"a.txt\\\",\\\"diff\\\":\\\"-a\\\\n+b\\\"}\"";
        String result = tool.execute(Map.of("raw", raw));
        assertEquals("{\"status\":\"completed\",\"output\":\"Updated a.txt\"}", result);
    }

    @Test
    void testMissingOperation()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of());
        assertEquals("{\"status\":\"failed\",\"output\":\"Apply patch call is missing an operation payload.\"}", result);
    }

    @Test
    void testOperationMustBeObject()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of("operation", "bad"));
        assertEquals("{\"status\":\"failed\",\"output\":\"Apply patch call is missing an operation payload.\"}", result);
    }

    @Test
    void testMissingValidPath()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of("operation", Map.of("type", "create_file")));
        assertEquals("{\"status\":\"failed\",\"output\":\"Apply patch operation is missing a valid path.\"}", result);
    }

    @Test
    void testMissingDiffForUpdate()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of("operation", Map.of("type", "update_file", "path", "a.txt")));
        assertEquals("{\"status\":\"failed\",\"output\":\"Apply patch operation update_file is missing the required diff payload.\"}", result);
    }

    @Test
    void testUnknownOperationType()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new TestEditor());
        String result = tool.execute(Map.of("operation", Map.of("type", "unknown", "path", "a.txt")));
        assertEquals("{\"status\":\"failed\",\"output\":\"Unknown apply_patch operation: unknown\"}", result);
    }

    @Test
    void testEditorFailure()
    {
        ApplyPatchTool tool = new ApplyPatchTool(new FailingEditor());
        String result = tool.execute(Map.of("operation", Map.of("type", "update_file", "path", "a.txt", "diff", "-a\n+b")));
        assertEquals("{\"status\":\"failed\",\"output\":\"boom\"}", result);
    }

    @Test
    void testApplyDiffStaticMethod()
    {
        String original = "line1\nline2\nline3";
        String diff = " line1\n-line2\n+line2_new\n line3";
        String result = ApplyPatchTool.applyDiff(original, diff);
        assertEquals("line1\nline2_new\nline3", result);
    }

    @Test
    void testApplyCreateDiffStaticMethod()
    {
        String diff = "+line1\n+line2";
        String result = ApplyPatchTool.applyCreateDiff(diff);
        assertEquals("line1\nline2", result);
    }

    private static class TestEditor implements IApplyPatchEditor
    {
        private int createCount = 0;
        private int updateCount = 0;
        private int deleteCount = 0;

        @Override
        public ApplyPatchResult createFile(ApplyPatchOperation operation)
        {
            createCount++;
            return ApplyPatchResult.completed("Created " + operation.getPath());
        }

        @Override
        public ApplyPatchResult updateFile(ApplyPatchOperation operation)
        {
            updateCount++;
            return ApplyPatchResult.completed("Updated " + operation.getPath());
        }

        @Override
        public ApplyPatchResult deleteFile(ApplyPatchOperation operation)
        {
            deleteCount++;
            return ApplyPatchResult.completed("Deleted " + operation.getPath());
        }

        public int getCreateCount()
        {
            return createCount;
        }

        public int getUpdateCount()
        {
            return updateCount;
        }

        public int getDeleteCount()
        {
            return deleteCount;
        }
    }

    private static class FailingEditor extends TestEditor
    {
        @Override
        public ApplyPatchResult updateFile(ApplyPatchOperation operation)
        {
            throw new RuntimeException("boom");
        }
    }
}
