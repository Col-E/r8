// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/**
 * Exception thrown to interrupt processing after a fatal error or fail-if-error barrier.
 *
 * <p>The abort always contains the diagnostic causing the fatal error, or in the case of multiple
 * pending errors, one of the errors. In all cases, the abort exception signifies that the error has
 * been reported to the {@link com.android.tools.r8.DiagnosticsHandler}.
 */
public class AbortException extends RuntimeException {
  private final Diagnostic diagnostic;

  public AbortException(Diagnostic diagnostic) {
    assert diagnostic != null;
    this.diagnostic = diagnostic;
  }

  @Override
  public synchronized Throwable getCause() {
    // In the case of exception diagnostics, treat that as the parent cause.
    return diagnostic instanceof ExceptionDiagnostic
        ? ((ExceptionDiagnostic) diagnostic).getCause()
        : null;
  }

  @Override
  public String getMessage() {
    return diagnostic.getDiagnosticMessage();
  }

  public Origin getOrigin() {
    return diagnostic != null ? diagnostic.getOrigin() : Origin.unknown();
  }

  public Position getPosition() {
    return diagnostic != null ? diagnostic.getPosition() : Position.UNKNOWN;
  }
}
