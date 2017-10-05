// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;

public class StringDiagnostic implements Diagnostic {
  private final String message;

  public StringDiagnostic(String message) {
    this.message = message;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }
}
