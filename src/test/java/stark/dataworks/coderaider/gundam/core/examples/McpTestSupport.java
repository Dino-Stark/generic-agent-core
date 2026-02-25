package stark.dataworks.coderaider.gundam.core.examples;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

final class McpTestSupport
{
    private McpTestSupport()
    {
    }

    static String pythonExecutable()
    {
        if (commandExists("python3"))
        {
            return "python3";
        }
        if (commandExists("python"))
        {
            return "python";
        }
        throw new IllegalStateException("Neither python3 nor python is available in PATH");
    }

    static Process startPythonScript(String scriptPath, String... args)
    {
        String[] command = new String[2 + args.length];
        command[0] = pythonExecutable();
        command[1] = scriptPath;
        System.arraycopy(args, 0, command, 2, args.length);
        try
        {
            return new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to start MCP script: " + scriptPath, ex);
        }
    }

    static void waitForHttp(String url, Duration timeout)
    {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException last = null;
        while (System.nanoTime() < deadline)
        {
            try
            {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(500);
                connection.setReadTimeout(500);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                if (code >= 200 && code < 500)
                {
                    return;
                }
            }
            catch (Exception ex)
            {
                last = new RuntimeException(ex);
            }

            try
            {
                Thread.sleep(150);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for MCP server", ex);
            }
        }
        throw new IllegalStateException("MCP HTTP endpoint did not become ready in time: " + url, last);
    }

    static void stopQuietly(Process process)
    {
        if (process == null)
        {
            return;
        }
        process.destroy();
    }

    private static boolean commandExists(String command)
    {
        try
        {
            Process process = new ProcessBuilder("bash", "-lc", "command -v " + command).start();
            int code = process.waitFor();
            return code == 0;
        }
        catch (Exception ex)
        {
            return false;
        }
    }
}
