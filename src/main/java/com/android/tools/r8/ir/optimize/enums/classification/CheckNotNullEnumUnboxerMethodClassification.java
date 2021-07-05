// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;

public final class CheckNotNullEnumUnboxerMethodClassification
    extends EnumUnboxerMethodClassification {

  private int argumentIndex;

  CheckNotNullEnumUnboxerMethodClassification(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public int getArgumentIndex() {
    return argumentIndex;
  }

  public boolean isUseEligibleForUnboxing(InvokeStatic invoke, Value enumValue) {
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex);
      if (argument == enumValue && argumentIndex != getArgumentIndex()) {
        return false;
      }
    }
    return invoke.hasUnusedOutValue();
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
