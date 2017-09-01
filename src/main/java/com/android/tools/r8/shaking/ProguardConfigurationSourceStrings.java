// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import joptsimple.internal.Strings;

public class ProguardConfigurationSourceStrings implements ProguardConfigurationSource {
  private final List<String> config;

  public ProguardConfigurationSourceStrings(List<String> config) {
    this.config = config;
  }

  public String get() throws IOException{
    return Strings.join(config, "\n");
  }

  public Path getBaseDirectory() {
    return Paths.get(".");
  }

  public String getName() {
    return "<no file>";
  }
}
