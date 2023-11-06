// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/**
 * Diagnostic for any unhandled exception arising during compilation.
 *
 * <p>The inner-most exception giving rise to the exception can be obtained as the "cause". If the
 * the inner-most exception is not the same as the exception at the point of the interception and
 * conversion to a diagnostic, the full exception stack can be obtained in the suppressed exceptions
 * on the inner-most cause.
 */
@KeepForApi
public class ExceptionDiagnostic implements Diagnostic {

  private final Throwable cause;
  private final Origin origin;
  private final Position position;

  public ExceptionDiagnostic(Throwable cause, Origin origin, Position position) {
    assert cause != null;
    assert origin != null;
    assert position != null;
    this.cause = cause;
    this.origin = origin;
    this.position = position;
  }

  public ExceptionDiagnostic(Throwable cause) {
    this(cause, Origin.unknown(), Position.UNKNOWN);
  }

  public ExceptionDiagnostic(Throwable e, Origin origin) {
    this(e, origin, Position.UNKNOWN);
  }

  public ExceptionDiagnostic(ResourceException e) {
    this(e, e.getOrigin());
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  public Throwable getCause() {
    return cause;
  }

  @Override
  public String getDiagnosticMessage() {
    return cause.toString();
  }
}
