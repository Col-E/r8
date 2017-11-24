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
  private static final List<String> REFLECTIONS = ImmutableList.of(
      "-identifiernamestring public class java.lang.Class {",
      "  public static java.lang.Class forName(java.lang.String);",
      "  public java.lang.reflect.Field getField(java.lang.String);",
      "  public java.lang.reflect.Field getDeclaredField(java.lang.String);",
      "  public java.lang.reflect.Method getMethod(java.lang.String, java.lang.Class[]);",
      "  public java.lang.reflect.Method getDeclaredMethod(java.lang.String, java.lang.Class[]);",
      "}"
  );

  public CompatProguardCommandBuilder(
      boolean forceProguardCompatibility,
      boolean ignoreMissingClasses) {
    super(forceProguardCompatibility, true, ignoreMissingClasses);
    setIgnoreDexInArchive(true);
    setEnableDesugaring(false);
    addProguardConfiguration(REFLECTIONS, EmbeddedOrigin.INSTANCE);
  }

  public void setProguardCompatibilityRulesOutput(Path path) {
    proguardCompatibilityRulesOutput = path;
  }
}
