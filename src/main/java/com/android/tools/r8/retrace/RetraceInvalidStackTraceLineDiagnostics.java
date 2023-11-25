// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;

@KeepForApi
public class RetraceInvalidStackTraceLineDiagnostics implements Diagnostic {

  private static final String NULL_STACK_TRACE_LINE_MESSAGE = "The stack trace line is <null>";

  private final int lineNumber;
  private final String message;

  private RetraceInvalidStackTraceLineDiagnostics(int lineNumber, String message) {
    this.lineNumber = lineNumber;
    this.message = message;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return new TextPosition(0, lineNumber, TextPosition.UNKNOWN_COLUMN);
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }

  public static RetraceInvalidStackTraceLineDiagnostics createNull(int lineNumber) {
    return new RetraceInvalidStackTraceLineDiagnostics(lineNumber, NULL_STACK_TRACE_LINE_MESSAGE);
  }
}
