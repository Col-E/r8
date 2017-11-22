// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;

public class CompatProguardCommandBuilder extends R8Command.Builder {
  private static final List<String> CLASS_FOR_NAME = ImmutableList.of(
      "-identifiernamestring public class java.lang.Class {",
      "  public static java.lang.Class forName(java.lang.String);",
      "}"
  );

  public CompatProguardCommandBuilder(
      boolean forceProguardCompatibility,
      boolean ignoreMissingClasses) {
    super(forceProguardCompatibility, true, ignoreMissingClasses);
    setIgnoreDexInArchive(true);
    setEnableDesugaring(false);
    addProguardConfiguration(CLASS_FOR_NAME, EmbeddedOrigin.INSTANCE);
  }

  public void setProguardCompatibilityRulesOutput(Path path) {
    proguardCompatibilityRulesOutput = path;
  }
}
