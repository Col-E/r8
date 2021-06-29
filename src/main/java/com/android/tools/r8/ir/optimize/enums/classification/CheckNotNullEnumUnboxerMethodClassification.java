// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

public final class CheckNotNullEnumUnboxerMethodClassification
    extends EnumUnboxerMethodClassification {

  private int argumentIndex;

  CheckNotNullEnumUnboxerMethodClassification(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public int getArgumentIndex() {
    return argumentIndex;
  }

  @Override
  public boolean isCheckNotNullClassification() {
    return true;
  }

  @Override
  public CheckNotNullEnumUnboxerMethodClassification asCheckNotNullClassification() {
    return this;
  }
}
