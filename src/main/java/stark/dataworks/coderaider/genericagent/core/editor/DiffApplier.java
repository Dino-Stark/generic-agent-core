package stark.dataworks.coderaider.genericagent.core.editor;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DiffApplier
{
    private DiffApplier()
    {
    }

    public enum ApplyDiffMode
    {
        DEFAULT,
        CREATE
    }

    public static String applyDiff(String input, String diff)
    {
        return applyDiff(input, diff, ApplyDiffMode.DEFAULT);
    }

    public static String applyDiff(String input, String diff, ApplyDiffMode mode)
    {
        if (diff == null || diff.isEmpty())
        {
            return input;
        }

        List<String> diffLines = splitLines(diff);

        if (mode == ApplyDiffMode.CREATE)
        {
            return parseCreateDiff(diffLines);
        }

        String normalizedInput = normalizeNewlines(input != null ? input : "");
        String newline = detectNewline(input);

        if (isGitUnifiedDiff(diffLines))
        {
            try
            {
                Patch<String> patch = parseGitPatch(diffLines);
                if (patch != null && !patch.getDeltas().isEmpty())
                {
                    List<String> originalLines = Arrays.asList(normalizedInput.split("\n", -1));
                    List<String> result = DiffUtils.patch(originalLines, patch);
                    return String.join(newline, result);
                }
            }
            catch (PatchFailedException e)
            {
                return trySimplePatch(normalizedInput, diffLines, newline);
            }
            catch (Exception e)
            {
                return trySimplePatch(normalizedInput, diffLines, newline);
            }
        }

        if (isSimpleUnifiedDiff(diffLines))
        {
            try
            {
                Patch<String> patch = parseSimpleUnifiedPatch(diffLines);
                if (patch != null && !patch.getDeltas().isEmpty())
                {
                    List<String> originalLines = Arrays.asList(normalizedInput.split("\n", -1));
                    List<String> result = DiffUtils.patch(originalLines, patch);
                    return String.join(newline, result);
                }
            }
            catch (PatchFailedException e)
            {
                return applySimpleDiff(normalizedInput, diffLines, newline);
            }
            catch (Exception e)
            {
                return applySimpleDiff(normalizedInput, diffLines, newline);
            }
        }

        return applySimpleDiff(normalizedInput, diffLines, newline);
    }

    private static boolean isGitUnifiedDiff(List<String> lines)
    {
        for (String line : lines)
        {
            if (line.startsWith("diff --git"))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimpleUnifiedDiff(List<String> lines)
    {
        boolean hasMinus = false;
        boolean hasPlus = false;

        for (String line : lines)
        {
            if (line.startsWith("--- "))
            {
                hasMinus = true;
            }
            if (line.startsWith("+++ "))
            {
                hasPlus = true;
            }
        }

        return hasMinus && hasPlus;
    }

    private static Patch<String> parseGitPatch(List<String> diffLines)
    {
        try
        {
            String diffText = String.join("\n", diffLines);
            InputStream inputStream = new ByteArrayInputStream(diffText.getBytes(StandardCharsets.UTF_8));
            UnifiedDiff unifiedDiff = UnifiedDiffReader.parseUnifiedDiff(inputStream);

            if (unifiedDiff == null || unifiedDiff.getFiles() == null || unifiedDiff.getFiles().isEmpty())
            {
                return null;
            }

            return unifiedDiff.getFiles().get(0).getPatch();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Patch<String> parseSimpleUnifiedPatch(List<String> diffLines)
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append("diff --git a/file b/file\n");
            for (String line : diffLines)
            {
                sb.append(line).append("\n");
            }

            String diffText = sb.toString();
            InputStream inputStream = new ByteArrayInputStream(diffText.getBytes(StandardCharsets.UTF_8));
            UnifiedDiff unifiedDiff = UnifiedDiffReader.parseUnifiedDiff(inputStream);

            if (unifiedDiff == null || unifiedDiff.getFiles() == null || unifiedDiff.getFiles().isEmpty())
            {
                return null;
            }

            return unifiedDiff.getFiles().get(0).getPatch();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String applySimpleDiff(String input, List<String> diffLines, String newline)
    {
        // Try simple string replacement for minimal diffs first
        String simpleResult = trySimpleStringReplacement(diffLines, input);
        if (simpleResult != null)
        {
            return simpleResult;
        }

        // Fall back to line-by-line processing
        String[] inputLines = input.split("\n", -1);
        List<String> result = new ArrayList<>();
        int inputIndex = 0;

        String anchor = extractAnchor(diffLines);
        if (anchor != null && !anchor.isEmpty())
        {
            while (inputIndex < inputLines.length)
            {
                if (inputLines[inputIndex].equals(anchor))
                {
                    result.add(inputLines[inputIndex]);
                    inputIndex++;
                    break;
                }
                result.add(inputLines[inputIndex]);
                inputIndex++;
            }
        }

        List<SimpleDiffLine> parsedDiff = parseSimpleDiff(diffLines);

        for (SimpleDiffLine diffLine : parsedDiff)
        {
            switch (diffLine.type)
            {
                case CONTEXT:
                    while (inputIndex < inputLines.length && inputIndex < diffLine.sourceLine)
                    {
                        result.add(inputLines[inputIndex]);
                        inputIndex++;
                    }
                    if (inputIndex < inputLines.length)
                    {
                        result.add(inputLines[inputIndex]);
                        inputIndex++;
                    }
                    break;
                case REMOVE:
                    if (inputIndex < inputLines.length)
                    {
                        inputIndex++;
                    }
                    break;
                case ADD:
                    result.add(diffLine.content);
                    break;
            }
        }

        while (inputIndex < inputLines.length)
        {
            result.add(inputLines[inputIndex]);
            inputIndex++;
        }

        return String.join(newline, result);
    }

    /**
     * Simple string-based replacement for minimal diffs.
     * Much safer than line-by-line approach.
     */
    private static String trySimpleStringReplacement(List<String> diffLines, String input)
    {
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();

        for (String line : diffLines)
        {
            if (line.startsWith("diff --git") || line.startsWith("index ") ||
                line.startsWith("new file mode") || line.startsWith("--- ") ||
                line.startsWith("+++ ") || line.startsWith("@@"))
            {
                continue;
            }

            if (line.startsWith("-") && !line.startsWith("---"))
            {
                removed.add(line.substring(1));
            }
            else if (line.startsWith("+") && !line.startsWith("+++"))
            {
                added.add(line.substring(1));
            }
            else if (line.startsWith(" "))
            {
                // Context line - this diff is not simple replacement only
                return null;
            }
        }

        if (removed.isEmpty() || added.isEmpty())
        {
            return null;
        }

        // Apply replacements as string operations
        String result = input;
        int maxPairs = Math.min(removed.size(), added.size());
        for (int i = 0; i < maxPairs; i++)
        {
            String oldStr = removed.get(i);
            String newStr = added.get(i);
            
            // Try exact match first
            int index = result.indexOf(oldStr);
            if (index >= 0)
            {
                result = result.substring(0, index) + newStr + result.substring(index + oldStr.length());
                continue;
            }
            
            // If no exact match, this diff needs context - return null
            return null;
        }

        return result;
    }

    private static String extractAnchor(List<String> lines)
    {
        for (String line : lines)
        {
            if (line.startsWith("@@") && line.length() > 2)
            {
                String rest = line.substring(2).trim();
                if (!rest.isEmpty())
                {
                    return rest;
                }
            }
        }
        return null;
    }

    private static List<SimpleDiffLine> parseSimpleDiff(List<String> lines)
    {
        List<SimpleDiffLine> result = new ArrayList<>();
        int sourceLine = 0;

        for (String line : lines)
        {
            if (line.startsWith("@@"))
            {
                continue;
            }

            if (line.startsWith(" "))
            {
                result.add(new SimpleDiffLine(DiffType.CONTEXT, line.substring(1), sourceLine));
                sourceLine++;
            }
            else if (line.startsWith("-"))
            {
                result.add(new SimpleDiffLine(DiffType.REMOVE, line.substring(1), sourceLine));
                sourceLine++;
            }
            else if (line.startsWith("+"))
            {
                result.add(new SimpleDiffLine(DiffType.ADD, line.substring(1), -1));
            }
            else if (!line.trim().isEmpty())
            {
                result.add(new SimpleDiffLine(DiffType.CONTEXT, line, sourceLine));
                sourceLine++;
            }
        }

        return result;
    }

    private static List<String> splitLines(String text)
    {
        List<String> lines = new ArrayList<>();
        String[] split = text.split("\r?\n", -1);
        for (String line : split)
        {
            lines.add(line);
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
        {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String normalizeNewlines(String text)
    {
        return text.replace("\r\n", "\n");
    }

    private static String detectNewline(String text)
    {
        if (text != null && text.contains("\r\n"))
        {
            return "\r\n";
        }
        return "\n";
    }

    private static String parseCreateDiff(List<String> lines)
    {
        StringBuilder content = new StringBuilder();
        boolean inHunk = false;

        for (String line : lines)
        {
            if (line.startsWith("diff --git") || line.startsWith("index ") ||
                line.startsWith("new file mode") || line.startsWith("--- ") ||
                line.startsWith("+++ "))
            {
                continue;
            }

            if (line.startsWith("@@"))
            {
                inHunk = true;
                continue;
            }

            if (inHunk && line.startsWith("+"))
            {
                content.append(line.substring(1)).append("\n");
            }
            else if (!inHunk && line.startsWith("+"))
            {
                content.append(line.substring(1)).append("\n");
            }
        }

        if (content.length() > 0 && content.charAt(content.length() - 1) == '\n')
        {
            content.setLength(content.length() - 1);
        }

        return content.toString();
    }

    private static String trySimplePatch(String input, List<String> diffLines, String newline)
    {
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();

        for (String line : diffLines)
        {
            if (line.startsWith("diff --git") || line.startsWith("index ") ||
                line.startsWith("new file mode") || line.startsWith("--- ") ||
                line.startsWith("+++ ") || line.startsWith("@@"))
            {
                continue;
            }

            if (line.startsWith("-") && !line.startsWith("---"))
            {
                removed.add(line.substring(1));
            }
            else if (line.startsWith("+") && !line.startsWith("+++"))
            {
                added.add(line.substring(1));
            }
        }

        if (removed.isEmpty() && added.isEmpty())
        {
            return input;
        }

        String[] inputLines = input.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < inputLines.length; i++)
        {
            String inputLine = inputLines[i];
            boolean matched = false;

            for (int j = 0; j < removed.size() && j < added.size(); j++)
            {
                String oldLine = removed.get(j);
                if (inputLine.trim().equals(oldLine.trim()))
                {
                    String indent = getIndent(inputLine);
                    result.add(indent + added.get(j).trim());
                    matched = true;
                    break;
                }
            }

            if (!matched)
            {
                result.add(inputLine);
            }
        }

        return String.join(newline, result);
    }

    private static String getIndent(String line)
    {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'))
        {
            i++;
        }
        return line.substring(0, i);
    }

    private enum DiffType
    {
        CONTEXT,
        REMOVE,
        ADD
    }

    private static class SimpleDiffLine
    {
        final DiffType type;
        final String content;
        final int sourceLine;

        SimpleDiffLine(DiffType type, String content, int sourceLine)
        {
            this.type = type;
            this.content = content;
            this.sourceLine = sourceLine;
        }
    }
}
