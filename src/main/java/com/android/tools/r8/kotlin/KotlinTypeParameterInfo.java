// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmVariance;

// Provides access to Kotlin information about a type-parameter.
public class KotlinTypeParameterInfo {

  private final int flags;
  private final int id;
  private final String name;
  private final KmVariance variance;

  private KotlinTypeParameterInfo(int flags, int id, String name, KmVariance variance) {
    this.flags = flags;
    this.id = id;
    this.name = name;
    this.variance = variance;
  }

  static KotlinTypeParameterInfo fromKmTypeParameter(KmTypeParameter kmTypeParameter) {
    return new KotlinTypeParameterInfo(
        kmTypeParameter.getFlags(),
        kmTypeParameter.getId(),
        kmTypeParameter.getName(),
        kmTypeParameter.getVariance());
  }

  public int getFlags() {
    return flags;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public KmVariance getVariance() {
    return variance;
  }
}
