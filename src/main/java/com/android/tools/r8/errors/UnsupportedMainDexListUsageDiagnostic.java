// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/**
 * Diagnostic to issue warnings/errors for unsupported usage of main-dex list.
 *
 * <p>See b/181858113 for context.
 */
@KeepForApi
public class UnsupportedMainDexListUsageDiagnostic implements Diagnostic {
  private final Origin origin;

  public UnsupportedMainDexListUsageDiagnostic(Origin origin) {
    this.origin = origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Unsupported usage of main-dex list. "
        + "The usage of main-dex-list content for the compilation of non-DEX inputs is deprecated. "
        + "See issue https://issuetracker.google.com/181858113 for context.";
  }
}
