// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
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
  public EnumUnboxerMethodClassification fixupAfterParameterRemoval(
      ArgumentInfoCollection removedParameters) {
    if (removedParameters.getArgumentInfo(argumentIndex).isRemovedArgumentInfo()) {
      // If the null-checked argument is removed from the parameters of the method, then we can no
      // longer classify this method as a check-not-null method. This is OK in terms of enum
      // unboxing, since after the parameter removal enums at the call site will no longer have the
      // check-not-null invoke instruction as a user.
      //
      // Note that when we materialize the enum instance in the check-not-null method, it is
      // important that this method is reprocessed by enum unboxing (or that materialized instance
      // would not be unboxed). This is guaranteed by argument removal: Since we have removed a
      // parameter from the method, we will need to reprocess its code in the second optimization
      // pass.
      return unknown();
    }

    int newArgumentIndex = removedParameters.getNewArgumentIndex(argumentIndex);
    return newArgumentIndex != argumentIndex
        ? new CheckNotNullEnumUnboxerMethodClassification(newArgumentIndex)
        : this;
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
