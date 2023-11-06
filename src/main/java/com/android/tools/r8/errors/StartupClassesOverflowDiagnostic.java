// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@KeepForApi
public class StartupClassesOverflowDiagnostic implements Diagnostic {

  private final int numberOfStartupDexFiles;

  StartupClassesOverflowDiagnostic(int numberOfStartupDexFiles) {
    this.numberOfStartupDexFiles = numberOfStartupDexFiles;
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
    return "Unable to include all startup classes in classes.dex. "
        + "Startup classes were distributed in: classes.dex, "
        + IntStream.range(2, numberOfStartupDexFiles + 1)
            .mapToObj(i -> "classes" + i + ".dex")
            .collect(Collectors.joining(", "))
        + ".";
  }

  // Public factory to keep the constructor of the diagnostic out of the public API.
  public static class Factory {

    public static StartupClassesOverflowDiagnostic createStartupClassesOverflowDiagnostic(
        int numberOfStartupDexFiles) {
      return new StartupClassesOverflowDiagnostic(numberOfStartupDexFiles);
    }
  }
}
