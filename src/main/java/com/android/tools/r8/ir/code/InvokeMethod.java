// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.InliningOracle;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.List;

public abstract class InvokeMethod extends Invoke {

  private final DexMethod method;

  public InvokeMethod(DexMethod target, Value result, List<Value> arguments) {
    super(result, arguments);
    this.method = target;
  }

  @Override
  public DexType getReturnType() {
    return method.proto.returnType;
  }

  public DexMethod getInvokedMethod() {
    return method;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeMethod() && method == other.asInvokeMethod().getInvokedMethod();
  }

  @Override
  public String toString() {
    return super.toString() + "; method: " + method.toSourceString();
  }

  @Override
  public boolean isInvokeMethod() {
    return true;
  }

  @Override
  public InvokeMethod asInvokeMethod() {
    return this;
  }

  // In subclasses, e.g., invoke-virtual or invoke-super, use a narrower receiver type by using
  // receiver type and calling context---the holder of the method where the current invocation is.
  public abstract DexEncodedMethod lookupSingleTarget(
      AppView<AppInfoWithLiveness> appView, DexType invocationContext);

  public abstract Collection<DexEncodedMethod> lookupTargets(
      AppView<? extends AppInfoWithSubtyping> appView, DexType invocationContext);

  public abstract InlineAction computeInlining(
      InliningOracle decider,
      DexMethod invocationContext,
      ClassInitializationAnalysis classInitializationAnalysis);

  @Override
  public boolean identicalAfterRegisterAllocation(Instruction other, RegisterAllocator allocator) {
    if (!super.identicalAfterRegisterAllocation(other, allocator)) {
      return false;
    }

    if (allocator.options().canHaveIncorrectJoinForArrayOfInterfacesBug()) {
      InvokeMethod invoke = other.asInvokeMethod();

      // If one of the arguments of this invoke is an array, then make sure that the corresponding
      // argument of the other invoke is the exact same value. Otherwise, the verifier may
      // incorrectly join the types of these arrays to Object[].
      for (int i = 0; i < arguments().size(); ++i) {
        Value argument = arguments().get(i);
        if (argument.getTypeLattice().isArrayType() && argument != invoke.arguments().get(i)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    if (getReturnType().isVoidType()) {
      return;
    }
    if (outValue == null) {
      helper.popOutType(getReturnType(), this, it);
    } else {
      assert outValue.isUsed();
      helper.storeOutValue(this, it);
    }
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return getReturnType();
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, DexType context) {
    return true;
  }
}
