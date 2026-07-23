package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Compiles every {@code ```java} fenced code block in the README and {@code docs/} to verify that
 * each in-documentation snippet is valid, runnable code (offline). Statement-level snippets are
 * wrapped in a synthetic class exposing a {@code client} field; snippets that declare their own
 * {@code class} are compiled as-is.
 */
class DocSnippetsTest {

  private static final Pattern FENCE = Pattern.compile("```java\\s*\\n(.*?)```", Pattern.DOTALL);

  @Test
  void allDocSnippetsCompile() throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    List<Path> docFiles = new ArrayList<>();
    Path readme = root.resolve("README.md");
    if (Files.exists(readme)) {
      docFiles.add(readme);
    }
    Path docsDir = root.resolve("docs");
    if (Files.isDirectory(docsDir)) {
      try (var stream = Files.walk(docsDir)) {
        stream.filter(p -> p.toString().endsWith(".md")).forEach(docFiles::add);
      }
    }
    assertTrue(!docFiles.isEmpty(), "expected at least one documentation file with snippets");

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "a JDK (not just a JRE) is required to compile doc snippets");

    List<String> failures = new ArrayList<>();
    int snippetCount = 0;

    for (Path doc : docFiles) {
      String content = Files.readString(doc, StandardCharsets.UTF_8);
      Matcher m = FENCE.matcher(content);
      int index = 0;
      while (m.find()) {
        String snippet = m.group(1);
        String className =
            "Snippet_" + doc.getFileName().toString().replaceAll("\\W", "_") + "_" + index;
        String source = wrap(className, snippet);
        String error = compile(compiler, className, source);
        if (error != null) {
          failures.add(
              doc.getFileName()
                  + " snippet #"
                  + index
                  + ":\n"
                  + error
                  + "\n--- snippet ---\n"
                  + snippet);
        }
        index++;
        snippetCount++;
      }
    }

    if (!failures.isEmpty()) {
      fail("Doc snippets failed to compile:\n\n" + String.join("\n\n", failures));
    }
    assertTrue(
        snippetCount > 0, "expected to find at least one ```java snippet in the documentation");
  }

  /** Wraps a snippet. Snippets that declare their own class compile as-is; others are wrapped. */
  private static String wrap(String className, String snippet) {
    if (snippet.contains("class ")) {
      return snippet;
    }
    return "import com.apify.client.*;\n"
        + "import com.apify.client.actor.*;\n"
        + "import com.apify.client.build.*;\n"
        + "import com.apify.client.dataset.*;\n"
        + "import com.apify.client.http.*;\n"
        + "import com.apify.client.keyvalue.*;\n"
        + "import com.apify.client.log.*;\n"
        + "import com.apify.client.requestqueue.*;\n"
        + "import com.apify.client.run.*;\n"
        + "import com.apify.client.schedule.*;\n"
        + "import com.apify.client.store.*;\n"
        + "import com.apify.client.task.*;\n"
        + "import com.apify.client.user.*;\n"
        + "import com.apify.client.webhook.*;\n"
        + "import tools.jackson.databind.*;\n"
        + "import java.util.*;\n"
        + "import java.util.concurrent.*;\n"
        + "import java.time.*;\n"
        + "import java.io.*;\n"
        + "@SuppressWarnings(\"all\")\n"
        + "public class "
        + className
        + " {\n"
        + "  ApifyClient client;\n"
        + "  void run() throws Exception {\n"
        + snippet
        + "\n  }\n}\n";
  }

  private static String compile(JavaCompiler compiler, String className, String source) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaFileObject file = new StringSource(className, source);
    // Compile against the current runtime classpath (built classes + Jackson).
    List<String> options =
        List.of("-classpath", System.getProperty("java.class.path"), "-proc:none", "-d", tempDir());
    JavaCompiler.CompilationTask task =
        compiler.getTask(null, null, diagnostics, options, null, List.of(file));
    boolean ok = task.call();
    if (ok) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
      if (d.getKind() == Diagnostic.Kind.ERROR) {
        sb.append(d.getMessage(null)).append('\n');
      }
    }
    return sb.toString();
  }

  private static String tempDir() {
    try {
      return Files.createTempDirectory("doc-snippets").toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** An in-memory Java source file. */
  private static final class StringSource extends SimpleJavaFileObject {
    private final String code;

    StringSource(String className, String code) {
      super(URI.create("string:///" + className + ".java"), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }
}
