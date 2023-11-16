// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

// This class is used by the Android Studio Gradle plugin and is thus part of the R8 API.
@KeepForApi
public class CompatProguardCommandBuilder extends R8Command.Builder {

  public CompatProguardCommandBuilder() {
    this(true);
  }

  public CompatProguardCommandBuilder(boolean forceProguardCompatibility) {
    setProguardCompatibility(forceProguardCompatibility);
  }

  public CompatProguardCommandBuilder(
      boolean forceProguardCompatibility, DiagnosticsHandler diagnosticsHandler) {
    super(diagnosticsHandler);
    setProguardCompatibility(forceProguardCompatibility);
  }
}
