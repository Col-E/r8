// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;

/**
 * Diagnostic information about a class file which could not be generated as the code size of a
 * method overflowed the limit.
 */
@KeepForApi
public class CodeSizeOverflowDiagnostic extends ClassFileOverflowDiagnostic {

  private final MethodReference method;
  private final int codeSize;
  private final MethodPosition position;

  public CodeSizeOverflowDiagnostic(Origin origin, MethodReference method, int codeSize) {
    super(origin);
    this.method = method;
    this.codeSize = codeSize;
    this.position = new MethodPosition(method);
  }

  /** Code size of the method. */
  public int getCodeSize() {
    return codeSize;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Method "
        + method
        + " too large for class file."
        + " Code size was "
        + getCodeSize()
        + ".";
  }
}
