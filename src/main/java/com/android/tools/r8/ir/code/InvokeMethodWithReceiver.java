// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import java.util.List;

public abstract class InvokeMethodWithReceiver extends InvokeMethod {

  InvokeMethodWithReceiver(DexMethod target, Value result, List<Value> arguments) {
    super(target, result, arguments);
  }

  @Override
  public boolean isInvokeMethodWithReceiver() {
    return true;
  }

  @Override
  public InvokeMethodWithReceiver asInvokeMethodWithReceiver() {
    return this;
  }

  public Value getReceiver() {
    assert inValues.size() > 0;
    return inValues.get(0);
  }

  @Override
  public final InlineAction computeInlining(
      DexEncodedMethod singleTarget,
      Reason reason,
      DefaultInliningOracle decider,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return decider.computeForInvokeWithReceiver(
        this, singleTarget, reason, whyAreYouNotInliningReporter);
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, DexItemFactory dexItemFactory) {
    return getReceiver() == value;
  }

  @Override
  public boolean throwsOnNullInput() {
    return true;
  }

  @Override
  public Value getNonNullInput() {
    return getReceiver();
  }

  @Override
  public boolean verifyTypes(AppView<?> appView) {
    assert super.verifyTypes(appView);

    Value receiver = getReceiver();
    TypeLatticeElement receiverType = receiver.getTypeLattice();
    assert receiverType.isPreciseType();

    if (appView.appInfo().hasSubtyping()) {
      AppView<? extends AppInfoWithSubtyping> appViewWithSubtyping = appView.withSubtyping();
      ClassTypeLatticeElement receiverLowerBoundType =
          receiver.getDynamicLowerBoundType(appViewWithSubtyping);
      if (receiverLowerBoundType != null) {
        DexType refinedReceiverType =
            TypeAnalysis.getRefinedReceiverType(appViewWithSubtyping, this);
        assert receiverLowerBoundType.getClassType() == refinedReceiverType
            || receiverLowerBoundType.isBasedOnMissingClass(appViewWithSubtyping);
      }
    }

    return true;
  }
}
