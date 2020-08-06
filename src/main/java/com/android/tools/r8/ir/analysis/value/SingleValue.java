// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class SingleValue extends AbstractValue implements InstanceFieldInitializationInfo {

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public boolean isSingleValue() {
    return true;
  }

  @Override
  public SingleValue asSingleValue() {
    return this;
  }

  /**
   * Note that calls to this method should generally be guarded by {@link
   * #isMaterializableInContext}.
   */
  public abstract Instruction createMaterializingInstruction(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCode code,
      TypeAndLocalInfoSupplier info);

  public abstract boolean isMaterializableInContext(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context);

  public abstract boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView);

  @Override
  public abstract SingleValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens);
}
