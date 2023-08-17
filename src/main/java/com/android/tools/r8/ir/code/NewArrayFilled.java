// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexFilledNewArrayRange;
import com.android.tools.r8.dex.code.DexInstruction;
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
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.StatefulObjectValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.List;

public class NewArrayFilled extends Invoke {

  private final DexType type;

  public NewArrayFilled(DexType type, Value result, List<Value> arguments) {
    super(result, arguments);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.NEW_ARRAY_FILLED;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public DexType getReturnType() {
    return getArrayType();
  }

  public DexType getArrayType() {
    return type;
  }

  @Override
  public InvokeType getType() {
    return InvokeType.NEW_ARRAY;
  }

  @Override
  protected String getTypeString() {
    return "NewArray";
  }

  @Override
  public String toString() {
    return super.toString() + "; type: " + type.toSourceString();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int argumentRegisters = requiredArgumentRegisters();
    builder.requestOutgoingRegisters(argumentRegisters);
    if (needsRangedInvoke(builder)) {
      assert argumentsConsecutive(builder);
      int firstRegister = argumentRegisterValue(0, builder);
      instruction = new DexFilledNewArrayRange(firstRegister, argumentRegisters, type);
    } else {
      int[] individualArgumentRegisters = new int[5];
      int argumentRegistersCount = fillArgumentRegisters(builder, individualArgumentRegisters);
      instruction =
          new DexFilledNewArray(
              argumentRegistersCount,
              type,
              individualArgumentRegisters[0], // C
              individualArgumentRegisters[1], // D
              individualArgumentRegisters[2], // E
              individualArgumentRegisters[3], // F
              individualArgumentRegisters[4]); // G
    }
    addInvokeAndMoveResult(instruction, builder);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNewArrayFilled() && type == other.asNewArrayFilled().type;
  }

  @Override
  public boolean isNewArrayFilled() {
    return true;
  }

  @Override
  public NewArrayFilled asNewArrayFilled() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forNewArrayFilled(type, context);
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
    String descriptor = type.toDescriptorString();
    ValueTypeConstraint constraint = ValueTypeConstraint.fromTypeDescriptorChar(descriptor.charAt(1));
    int wordSize = constraint.requiredRegisters();
    int argumentCount = arguments().size();
    builder.add(
            CfConstNumber.constNumber(argumentCount, ValueType.INT),
            new CfNewArray(type)
    );
    MemberType memberType = MemberType.getArrayMemberType(descriptor);
    int index = 0;
    for (int registerIndex = 0; registerIndex < argumentCount; ) {
      // Re-arrange the stack from:
      // [element, array]
      // to [array, array, index, element]

      // [element, array]
      builder.add(CfStackInstruction.DUP); // [element, array, array]
      builder.swap(2, wordSize); // [array, array, element]
      builder.add(CfConstNumber.constNumber(index++, ValueType.INT)); // [array, array, element, index]
      builder.swap(1, wordSize); // [array, array, index, element]
      builder.add(new CfArrayStore(memberType)); // [element?, array]
      registerIndex += wordSize;
    }
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (!instructionMayHaveSideEffects(appView, context, abstractValueSupplier)) {
      int size = inValues.size();
      return StatefulObjectValue.create(
          appView.abstractValueFactory().createKnownLengthArrayState(size));
    }
    return UnknownValue.getInstance();
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    DexType baseType = type.isArrayType() ? type.toBaseType(appView.dexItemFactory()) : type;
    if (baseType.isPrimitiveType()) {
      // Primitives types are known to be present and accessible.
      assert !type.isWideType() : "The array's contents must be single-word";
      return false;
    }

    assert baseType.isReferenceType();

    if (baseType == context.getHolderType()) {
      // The enclosing type is known to be present and accessible.
      return false;
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

    // Note: Implicitly assuming that all the arguments are of the right type, because the input
    // code must be valid.
    return false;
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    // Check if the instruction has a side effect on the locals environment.
    if (hasOutValue() && outValue().hasLocalInfo()) {
      assert appView.options().debug;
      return true;
    }

    return instructionInstanceCanThrow(appView, context, abstractValueSupplier, assumption);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerTypeReference(type);
  }

  // Returns the number of elements in the array.
  public int size() {
    return inValues.size();
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNewArrayFilled(getArrayType(), arguments());
  }
}
