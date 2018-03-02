// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

public final class KotlinSyntheticClass extends KotlinInfo {
  public enum Flavour {
    KotlinStyleLambda,
    Unclassified
  }

  public final Flavour flavour;

  KotlinSyntheticClass(Flavour flavour) {
    this.flavour = flavour;
  }

  public boolean isKotlinStyleLambda() {
    return flavour == Flavour.KotlinStyleLambda;
  }

  @Override
  public final Kind getKind() {
    return Kind.Synthetic;
  }

  @Override
  public final boolean isSyntheticClass() {
    return true;
  }

  @Override
  public KotlinSyntheticClass asSyntheticClass() {
    return this;
  }
}
