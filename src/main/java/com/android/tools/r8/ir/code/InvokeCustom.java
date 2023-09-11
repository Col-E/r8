// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeCustom;
import com.android.tools.r8.dex.code.DexInvokeCustomRange;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection.Builder;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;

public final class InvokeCustom extends Invoke {

  private final DexCallSite callSite;

  public InvokeCustom(DexCallSite callSite, Value result, List<Value> arguments) {
    super(result, arguments);
    assert callSite != null;
    this.callSite = callSite;
  }

  @Override
  public int opcode() {
    return Opcodes.INVOKE_CUSTOM;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean verifyLambdaInterfaces(
      TypeElement returnType, InterfaceCollection lambdaInterfaceSet, DexType objectType) {
    InterfaceCollection primaryInterfaces = returnType.asClassType().getInterfaces();
    if (returnType.asClassType().getClassType() == objectType) {
      // The interfaces returned by the LambdaDescriptor assumed to already contain the primary
      // interface. If they're both singleton lists they must be identical and we can return the
      // primary return type.
      assert lambdaInterfaceSet.containsKnownInterface(primaryInterfaces.getSingleKnownInterface());
    } else {
      // We arrive here if the primary interface is a missing class. In that case the
      // returnType will be the missing type as the class type.
      assert primaryInterfaces.isEmpty();
      assert lambdaInterfaceSet.containsKnownInterface(returnType.asClassType().getClassType());
    }
    return true;
  }

  @Override
  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  public TypeElement evaluate(AppView<?> appView) {
    TypeElement returnType = super.evaluate(appView);
    if (!appView.appInfo().hasLiveness()) {
      return returnType;
    }
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    List<DexType> lambdaInterfaces = LambdaDescriptor.getInterfaces(callSite, appViewWithLiveness);
    if (lambdaInterfaces == null || lambdaInterfaces.isEmpty()) {
      return returnType;
    }

    // The primary return type is either an interface or a missing type.
    assert returnType instanceof ClassTypeElement;

    InterfaceCollection primaryInterfaces = returnType.asClassType().getInterfaces();
    DexType objectType = appView.dexItemFactory().objectType;

    if (returnType.asClassType().getClassType() == objectType) {
      assert primaryInterfaces.hasSingleKnownInterface();
      // Shortcut for the common case: single interface. Save creating a new lattice type.
      if (lambdaInterfaces.size() == 1) {
        assert lambdaInterfaces.get(0) == primaryInterfaces.getSingleKnownInterface();
        return returnType;
      }
    }

    Builder builder = InterfaceCollection.builder();
    lambdaInterfaces.forEach(iface -> builder.addInterface(iface, true));
    InterfaceCollection lambdaInterfaceSet = builder.build();

    assert verifyLambdaInterfaces(returnType, lambdaInterfaceSet, objectType);

    return ClassTypeElement.create(
        objectType, Nullability.maybeNull(), appViewWithLiveness, lambdaInterfaceSet);
  }

  @Override
  public DexType getReturnType() {
    return callSite.methodProto.returnType;
  }

  public DexCallSite getCallSite() {
    return callSite;
  }

  @Override
  public InvokeType getType() {
    return InvokeType.CUSTOM;
  }

  @Override
  protected String getTypeString() {
    return "Custom";
  }

  @Override
  public String toString() {
    return super.toString() + "; call site: " + callSite.toSourceString();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new DexInvokeCustomRange(firstRegister, argumentRegisters, getCallSite());
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction =
          new DexInvokeCustom(
              argumentRegistersCount,
              getCallSite(),
              individualArgumentRegisters[0], // C
              individualArgumentRegisters[1], // D
              individualArgumentRegisters[2], // E
              individualArgumentRegisters[3], // F
              individualArgumentRegisters[4]); // G
    }
    addInvokeAndMoveResult(instruction, builder);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfInvokeDynamic(getCallSite()), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addInvokeCustom(getCallSite(), arguments());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeCustom() && callSite == other.asInvokeCustom().callSite;
  }

  @Override
  public boolean isInvokeCustom() {
    return true;
  }

  @Override
  public InvokeCustom asInvokeCustom() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forInvokeCustom();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Essentially the same as InvokeMethod but with call site's method proto
    // instead of a static called method.
    helper.loadInValues(this, it);
    if (getCallSite().methodProto.returnType.isVoidType()) {
      return;
    }
    if (outValue == null) {
      helper.popOutType(getCallSite().methodProto.returnType, this, it);
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
    return getCallSite().methodProto.returnType;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return true;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerCallSite(callSite);
  }

  @Override
  protected boolean needsRangedInvoke(DexBuilder builder) {
    return builder.getOptions().testing.forceInvokeRangeForInvokeCustom
        ? true
        : super.needsRangedInvoke(builder);
  }
}
