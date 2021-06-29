// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

public final class UnknownEnumUnboxerMethodClassification extends EnumUnboxerMethodClassification {

  private static final UnknownEnumUnboxerMethodClassification INSTANCE =
      new UnknownEnumUnboxerMethodClassification();

  private UnknownEnumUnboxerMethodClassification() {}

  static UnknownEnumUnboxerMethodClassification getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isUnknownClassification() {
    return true;
  }
}
