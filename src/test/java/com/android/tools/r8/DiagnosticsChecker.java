// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.ListUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Helper to check that a particular error occurred.
public class DiagnosticsChecker implements DiagnosticsHandler {
  public List<Diagnostic> errors = new ArrayList<>();
  public List<Diagnostic> warnings = new ArrayList<>();
  public List<Diagnostic> infos = new ArrayList<>();

  @Override
  public void error(Diagnostic error) {
    errors.add(error);
  }

  @Override
  public void warning(Diagnostic warning) {
    warnings.add(warning);
  }

  @Override
  public void info(Diagnostic info) {
    infos.add(info);
  }

  public interface FailingRunner {
    void run(DiagnosticsHandler handler) throws CompilationFailedException;
  }

  public static void checkErrorsContains(String snippet, FailingRunner runner)
      throws CompilationFailedException {
    DiagnosticsChecker handler = new DiagnosticsChecker();
    try {
      runner.run(handler);
    } catch (CompilationFailedException e) {
      List<String> messages = ListUtils.map(handler.errors, Diagnostic::getDiagnosticMessage);
      System.out.println("Expecting match for '" + snippet + "'");
      System.out.println("StdErr:\n" + messages);
      assertTrue(
          "Expected to find snippet '"
              + snippet
              + "' in error messages:\n"
              + String.join("\n", messages),
          handler.errors.stream().anyMatch(d -> d.getDiagnosticMessage().contains(snippet)));
      throw e;
    }
  }

  public static Diagnostic checkDiagnostic(Diagnostic diagnostic, Consumer<Origin> originChecker,
      int lineStart, int columnStart, String... messageParts) {
    if (originChecker != null) {
      originChecker.accept(diagnostic.getOrigin());
    }
    TextPosition position;
    if (diagnostic.getPosition() instanceof TextRange) {
      position = ((TextRange) diagnostic.getPosition()).getStart();
    } else {
      position = ((TextPosition) diagnostic.getPosition());
    }
    assertEquals(lineStart, position.getLine());
    assertEquals(columnStart, position.getColumn());
    for (String part : messageParts) {
      assertTrue(diagnostic.getDiagnosticMessage() + " doesn't contain \"" + part + "\"",
          diagnostic.getDiagnosticMessage().contains(part));
    }
    return diagnostic;
  }

  public static Diagnostic checkDiagnostic(Diagnostic diagnostic, Consumer<Origin> originChecker,
      String... messageParts) {
    if (originChecker != null) {
      originChecker.accept(diagnostic.getOrigin());
    }
    assertEquals(diagnostic.getPosition(), Position.UNKNOWN);
    for (String part : messageParts) {
      assertTrue(diagnostic.getDiagnosticMessage() + " doesn't contain \"" + part + "\"",
          diagnostic.getDiagnosticMessage().contains(part));
    }
    return diagnostic;
  }

  static class PathOriginChecker implements Consumer<Origin> {
    private final Path path;
    PathOriginChecker(Path path) {
      this.path = path;
    }

    public void accept(Origin origin) {
      if (path != null) {
        assertEquals(path, ((PathOrigin) origin).getPath());
      } else {
        assertSame(Origin.unknown(), origin);
      }
    }
  }

  public static Diagnostic checkDiagnostic(Diagnostic diagnostic, Path path,
      int lineStart, int columnStart, String... messageParts) {
    return checkDiagnostic(diagnostic, new PathOriginChecker(path), lineStart, columnStart,
        messageParts);
  }

  public static Diagnostic checkDiagnostics(List<Diagnostic> diagnostics, int index, Path path,
      int lineStart, int columnStart, String... messageParts) {
    return checkDiagnostic(diagnostics.get(index), path, lineStart, columnStart, messageParts);
  }

  public static Diagnostic checkDiagnostics(List<Diagnostic> diagnostics, Path path,
      int lineStart, int columnStart, String... messageParts) {
    assertEquals(1, diagnostics.size());
    return checkDiagnostics(diagnostics, 0, path, lineStart, columnStart, messageParts);
  }
}
