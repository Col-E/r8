// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.InliningOracle;
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
    return inValues.get(0);
  }

  @Override
  public final InlineAction computeInlining(InliningOracle decider, DexType invocationContext) {
    return decider.computeForInvokeWithReceiver(this, invocationContext);
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value) {
    return getReceiver() == value;
  }
}
