// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Version;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class RetraceUnknownMapVersionDiagnostic implements Diagnostic {

  private final String versionName;

  private RetraceUnknownMapVersionDiagnostic(String versionName) {
    this.versionName = versionName;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return String.format(
        "Map version '%s' is unknown or introduced later than retrace version '%s'.",
        versionName, Version.getVersionString());
  }

  public static RetraceUnknownMapVersionDiagnostic create(String versionName) {
    return new RetraceUnknownMapVersionDiagnostic(versionName);
  }
}
