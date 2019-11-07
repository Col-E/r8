// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;

public final class NonTrivialInstanceInitializerInfo extends InstanceInitializerInfo {

  public static final NonTrivialInstanceInitializerInfo INSTANCE =
      new NonTrivialInstanceInitializerInfo();

  private NonTrivialInstanceInitializerInfo() {}

  @Override
  public AbstractFieldSet readSet() {
    return EmptyFieldSet.getInstance();
  }

  @Override
  public boolean isEligibleForClassInlining() {
    return true;
  }

  @Override
  public boolean isEligibleForClassStaticizing() {
    return true;
  }

  @Override
  public boolean instanceFieldInitializationMayDependOnEnvironment() {
    return false;
  }

  @Override
  public boolean mayHaveOtherSideEffectsThanInstanceFieldAssignments() {
    return false;
  }

  @Override
  public boolean receiverNeverEscapesOutsideConstructorChain() {
    return true;
  }
}
