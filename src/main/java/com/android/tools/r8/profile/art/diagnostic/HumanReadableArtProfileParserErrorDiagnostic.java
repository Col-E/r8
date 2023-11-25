// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.diagnostic;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class HumanReadableArtProfileParserErrorDiagnostic implements Diagnostic {

  private final String rule;
  private final int lineNumber;
  private final Origin origin;

  public HumanReadableArtProfileParserErrorDiagnostic(String rule, int lineNumber, Origin origin) {
    this.rule = rule;
    this.lineNumber = lineNumber;
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
    return "Unable to parse rule at line " + lineNumber + " from ART profile: " + rule;
  }
}
