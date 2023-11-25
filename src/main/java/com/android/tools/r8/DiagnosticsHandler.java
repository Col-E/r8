// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.io.PrintStream;

/**
 * A DiagnosticsHandler can be provided to customize handling of diagnostics information.
 *
 * <p>During compilation the warning and info methods will be called.
 */
@KeepForApi
public interface DiagnosticsHandler {

  /** Should be considered private. */
  @SuppressWarnings("ReferenceEquality")
  static void printDiagnosticToStream(Diagnostic diagnostic, String prefix, PrintStream stream) {
    if (diagnostic.getOrigin() != Origin.unknown()) {
      stream.print(prefix + " in " + diagnostic.getOrigin());
      if (diagnostic.getPosition() != Position.UNKNOWN) {
        stream.print(" at " + diagnostic.getPosition().getDescription());
      }
      stream.println(":");
    } else {
      stream.print(prefix + ": ");
    }
    stream.println(diagnostic.getDiagnosticMessage());
  }

  /**
   * Handle error diagnostics.
   *
   * @param error Diagnostic containing error information.
   */
  default void error(Diagnostic error) {
    printDiagnosticToStream(error, "Error", System.err);
  }

  /**
   * Handle warning diagnostics.
   *
   * @param warning Diagnostic containing warning information.
   */
  default void warning(Diagnostic warning) {
    printDiagnosticToStream(warning, "Warning", System.err);
  }

  /**
   * Handle info diagnostics.
   *
   * @param info Diagnostic containing the information.
   */
  default void info(Diagnostic info) {
    printDiagnosticToStream(info, "Info", System.out);
  }

  /**
   * Modify the level of a diagnostic.
   *
   * <p>This modification is allowed only for non-fatal compiler diagnostics.
   *
   * <p>Changing a non-error into an error will cause the compiler to exit with a <code>
   * CompilationFailedException</code> at its next error check point.
   *
   * <p>Changing an error into a non-error will allow the compiler to continue compilation. Note
   * that doing so could very well lead to an internal compiler error due to a broken invariant.
   */
  default DiagnosticsLevel modifyDiagnosticsLevel(DiagnosticsLevel level, Diagnostic diagnostic) {
    return level;
  }
}
