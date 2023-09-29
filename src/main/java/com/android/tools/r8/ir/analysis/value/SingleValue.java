// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
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
  public final Instruction createMaterializingInstruction(
      AppView<?> appView, IRCode code, TypeAndLocalInfoSupplier info) {
    return createMaterializingInstruction(appView, code.context(), code.valueNumberGenerator, info);
  }

  public abstract Instruction createMaterializingInstruction(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator valueNumberGenerator,
      TypeAndLocalInfoSupplier info);

  public final boolean isMaterializableInContext(AppView<?> appView, ProgramMethod context) {
    if (appView.enableWholeProgramOptimizations()) {
      assert appView.hasClassHierarchy();
      return internalIsMaterializableInContext(appView.withClassHierarchy(), context);
    }
    // All abstract values created in D8 should be accessible in all contexts.
    return true;
  }

  abstract boolean internalIsMaterializableInContext(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context);

  public abstract boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView);

  @Override
  public abstract SingleValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens);
}
