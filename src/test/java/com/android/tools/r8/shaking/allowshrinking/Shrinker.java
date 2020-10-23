// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.allowshrinking;

import com.android.tools.r8.ProguardVersion;

class Shrinker {

  public static final Shrinker R8 = new Shrinker(null);
  public static final Shrinker PG = new Shrinker(ProguardVersion.getLatest());
  public static final Shrinker PG5 = new Shrinker(ProguardVersion.V5_2_1);
  public static final Shrinker PG6 = new Shrinker(ProguardVersion.V6_0_1);
  public static final Shrinker PG7 = new Shrinker(ProguardVersion.V7_0_0);

  private final ProguardVersion proguardVersion;

  private Shrinker(ProguardVersion proguardVersion) {
    this.proguardVersion = proguardVersion;
  }

  boolean isR8() {
    return proguardVersion == null;
  }

  boolean isPG() {
    return proguardVersion != null;
  }

  public ProguardVersion getProguardVersion() {
    return proguardVersion;
  }

  @Override
  public String toString() {
    return isR8() ? "r8" : "pg" + proguardVersion.getVersion();
  }
}
