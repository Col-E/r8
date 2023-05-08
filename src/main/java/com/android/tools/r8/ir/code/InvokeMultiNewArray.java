// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfMultiANewArray;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.LongInterval;
import java.util.List;

public class InvokeMultiNewArray extends Invoke {

  private final DexType type;

  public InvokeMultiNewArray(DexType type, Value result, List<Value> arguments) {
    super(result, arguments);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.INVOKE_MULTI_NEW_ARRAY;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isInvokeMultiNewArray() {
    return true;
  }

  @Override
  public InvokeMultiNewArray asInvokeMultiNewArray() {
    return this;
  }

  @Override
  public InvokeType getType() {
    return InvokeType.MULTI_NEW_ARRAY;
  }

  public DexType getArrayType() {
    return type;
  }

  @Override
  public DexType getReturnType() {
    return getArrayType();
  }

  @Override
  protected String getTypeString() {
    return "MultiNewArray";
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeMultiNewArray() && type == other.asInvokeMultiNewArray().type;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInvokeMultiNewArray(type, context);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(type, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return type;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfMultiANewArray(type, arguments().size()), this);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("InvokeNewArray (non-empty) not supported when compiling to dex files.");
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInvokeMultiNewArray(type, arguments());
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    DexType baseType = type.isArrayType() ? type.toBaseType(appView.dexItemFactory()) : type;
    if (baseType.isPrimitiveType()) {
      // Primitives types are known to be present and accessible.
      assert !type.isWideType() : "The array's contents must be single-word";
      return instructionInstanceCanThrowNegativeArraySizeException();
    }

    assert baseType.isReferenceType();

    if (baseType == context.getHolderType()) {
      // The enclosing type is known to be present and accessible.
      return instructionInstanceCanThrowNegativeArraySizeException();
    }

    if (!appView.enableWholeProgramOptimizations()) {
      // Conservatively bail-out in D8, because we require whole program knowledge to determine if
      // the type is present and accessible.
      return true;
    }

    assert appView.appInfo().hasClassHierarchy();
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();

    // Check if the type is guaranteed to be present.
    DexClass clazz = appView.definitionFor(baseType);
    if (clazz == null || !clazz.isResolvable(appView)) {
      return true;
    }

    // Check if the type is guaranteed to be accessible.
    if (AccessControl.isClassAccessible(clazz, context, appViewWithClassHierarchy)
        .isPossiblyFalse()) {
      return true;
    }

    // The type is known to be present and accessible.
    return instructionInstanceCanThrowNegativeArraySizeException();
  }

  private boolean instructionInstanceCanThrowNegativeArraySizeException() {
    boolean mayHaveNegativeArraySize = false;
    for (Value value : arguments()) {
      if (!value.hasValueRange()) {
        mayHaveNegativeArraySize = true;
        break;
      }
      LongInterval valueRange = value.getValueRange();
      if (valueRange.getMin() < 0) {
        mayHaveNegativeArraySize = true;
        break;
      }
    }
    return mayHaveNegativeArraySize;
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    // Check if the instruction has a side effect on the locals environment.
    if (hasOutValue() && outValue().hasLocalInfo()) {
      assert appView.options().debug;
      return true;
    }

    return instructionInstanceCanThrow(appView, context);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerTypeReference(type);
  }
}
