// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

public enum BenchmarkTarget {

  // Possible dashboard targets on golem.
  D8("d8", "D8"),
  R8_COMPAT("r8-compat", "R8"),
  R8_NON_COMPAT("r8-full", "R8-full"),
  R8_FORCE_OPT("r8-force", "R8-full-minify-optimize-shrink");

  private final String idName;
  private final String golemName;

  BenchmarkTarget(String idName, String golemName) {
    this.idName = idName;
    this.golemName = golemName;
  }

  public String getGolemName() {
    return golemName;
  }

  public String getIdentifierName() {
    return idName;
  }
}
