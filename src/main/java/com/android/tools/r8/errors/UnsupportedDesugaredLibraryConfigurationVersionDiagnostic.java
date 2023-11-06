// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class UnsupportedDesugaredLibraryConfigurationVersionDiagnostic implements Diagnostic {

  private static final String dagRoot = "https://developer.android.com";
  private static final String versionMatrixUrl =
      dagRoot + "/studio/build/library-desugaring-versions";
  private static final String desugaredLibraryUrl = dagRoot + "/studio/build/library-desugaring";

  private final Origin origin;

  public UnsupportedDesugaredLibraryConfigurationVersionDiagnostic(Origin origin) {
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
    return "Unsupported desugared library configuration version, please upgrade the D8/R8"
        + " compiler."
        + " See "
        + versionMatrixUrl
        + "."
        + " To learn more about library desugaring read "
        + desugaredLibraryUrl
        + ".";
  }
}
