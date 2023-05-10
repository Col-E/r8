// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeUtils;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.EmptyInstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.ir.optimize.info.field.UnknownInstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;

public class InstanceFieldValueAnalysis extends FieldValueAnalysis {

  // Information about how this instance constructor initializes the fields on the newly created
  // instance.
  private final InstanceFieldInitializationInfoCollection.Builder builder =
      InstanceFieldInitializationInfoCollection.builder();

  private final InstanceFieldInitializationInfoFactory factory;

  private final DexClassAndMethod parentConstructor;
  private final InvokeDirect parentConstructorCall;

  private InstanceFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      DexClassAndMethod parentConstructor,
      InvokeDirect parentConstructorCall) {
    super(appView, code, feedback);
    this.factory = appView.instanceFieldInitializationInfoFactory();
    this.parentConstructor = parentConstructor;
    this.parentConstructorCall = parentConstructorCall;
  }

  /**
   * Returns information about how this instance constructor initializes the fields on the newly
   * created instance.
   */
  public static InstanceFieldInitializationInfoCollection run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      Timing timing) {
    timing.begin("Analyze instance initializer");
    InstanceFieldInitializationInfoCollection result =
        run(appView, code, classInitializerDefaultsResult, feedback);
    timing.end();
    return result;
  }

  private static InstanceFieldInitializationInfoCollection run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert code.context().getDefinition().isInstanceInitializer();

    InvokeDirect parentConstructorCall =
        IRCodeUtils.getUniqueConstructorInvoke(code.getThis(), appView.dexItemFactory());
    if (parentConstructorCall == null) {
      return EmptyInstanceFieldInitializationInfoCollection.getInstance();
    }

    DexClassAndMethod parentConstructor =
        parentConstructorCall.lookupSingleTarget(appView, code.context());
    if (parentConstructor == null) {
      return EmptyInstanceFieldInitializationInfoCollection.getInstance();
    }

    InstanceFieldValueAnalysis analysis =
        new InstanceFieldValueAnalysis(
            appView.withLiveness(),
            code,
            feedback,
            parentConstructor,
            parentConstructorCall);
    analysis.computeFieldOptimizationInfo(classInitializerDefaultsResult);
    analysis.analyzeParentConstructorCall();
    return analysis.builder.build();
  }

  @Override
  boolean isInstanceFieldValueAnalysis() {
    return true;
  }

  @Override
  InstanceFieldValueAnalysis asInstanceFieldValueAnalysis() {
    return this;
  }

  @Override
  boolean isSubjectToOptimization(DexClassAndField field) {
    return !field.getAccessFlags().isStatic() && field.getHolderType() == context.getHolderType();
  }

  @Override
  boolean isSubjectToOptimizationIgnoringPinning(DexClassAndField field) {
    throw new Unreachable("Used by static analysis only.");
  }

  @Override
  void updateFieldOptimizationInfo(DexClassAndField field, FieldInstruction fieldPut, Value value) {
    if (fieldNeverWrittenBetweenInstancePutAndMethodExit(field, fieldPut.asInstancePut())) {
      recordInstanceFieldIsInitializedWithValue(field, value);
    }
  }

  void analyzeForwardingConstructorCall(InvokeDirect invoke, Value thisValue) {
    if (invoke.getReceiver() != thisValue
        || invoke.getInvokedMethod().getHolderType() != context.getHolderType()) {
      // Not a forwarding constructor call.
      return;
    }

    ProgramMethod singleTarget = invoke.lookupSingleProgramTarget(appView, context);
    if (singleTarget == null) {
      // Failure, should generally not happen.
      return;
    }

    InstanceFieldInitializationInfoCollection infos =
        singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(invoke)
            .fieldInitializationInfos();
    for (DexClassAndField field :
        singleTarget.getHolder().getDirectAndIndirectInstanceFields(appView)) {
      InstanceFieldInitializationInfo info = infos.get(field);
      if (info.isArgumentInitializationInfo()) {
        int argumentIndex = info.asArgumentInitializationInfo().getArgumentIndex();
        info = getInstanceFieldInitializationInfo(field, invoke.getArgument(argumentIndex));
      }
      recordFieldPut(field, invoke, info);
    }
  }

  private void analyzeParentConstructorCall() {
    if (parentConstructor.getHolderType() == context.getHolderType()) {
      // Forwarding constructor calls are handled similar to instance-put instructions.
      return;
    }
    InstanceFieldInitializationInfoCollection infos =
        parentConstructor
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(parentConstructorCall)
            .fieldInitializationInfos();
    infos.forEach(
        appView,
        (field, info) -> {
          if (fieldNeverWrittenBetweenParentConstructorCallAndMethodExit(field)) {
            if (info.isArgumentInitializationInfo()) {
              int argumentIndex = info.asArgumentInitializationInfo().getArgumentIndex();
              recordInstanceFieldIsInitializedWithValue(
                  field, parentConstructorCall.getArgument(argumentIndex));
            } else {
              assert info.isSingleValue() || info.isTypeInitializationInfo();
              builder.recordInitializationInfo(field, info);
            }
          }
        });
  }

  private InstanceFieldInitializationInfo getInstanceFieldInitializationInfo(
      DexClassAndField field, Value value) {
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
      Argument argument = root.definition.asArgument();
      return factory.createArgumentInitializationInfo(argument.getIndex());
    }
    AbstractValue abstractValue = value.getAbstractValue(appView, context);
    if (abstractValue.isSingleValue()) {
      return abstractValue.asSingleValue();
    }
    DexType fieldType = field.getType();
    if (fieldType.isClassType()) {
      ClassTypeElement dynamicLowerBoundType = value.getDynamicLowerBoundType(appView);
      TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
      TypeElement staticFieldType = TypeElement.fromDexType(fieldType, maybeNull(), appView);
      if (dynamicLowerBoundType != null || !dynamicUpperBoundType.equals(staticFieldType)) {
        return factory.createTypeInitializationInfo(dynamicLowerBoundType, dynamicUpperBoundType);
      }
    }
    return UnknownInstanceFieldInitializationInfo.getInstance();
  }

  void recordInstanceFieldIsInitializedWithInfo(
      DexClassAndField field, InstanceFieldInitializationInfo info) {
    if (!info.isUnknown()) {
      builder.recordInitializationInfo(field, info);
    }
  }

  void recordInstanceFieldIsInitializedWithValue(DexClassAndField field, Value value) {
    recordInstanceFieldIsInitializedWithInfo(
        field, getInstanceFieldInitializationInfo(field, value));
  }

  private boolean fieldNeverWrittenBetweenInstancePutAndMethodExit(
      DexClassAndField field, InstancePut instancePut) {
    if (field.getAccessFlags().isFinal()) {
      return true;
    }

    if (appView.appInfo().isFieldOnlyWrittenInMethod(field, context.getDefinition())) {
      return true;
    }

    if (appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)) {
      if (parentConstructorCall.getInvokedMethod().holder != context.getHolderType()) {
        // The field is only written in instance initializers of the enclosing class, and the
        // constructor call targets a constructor in the super class.
        return true;
      }

      // The parent constructor call in this initializer targets another initializer on the same
      // class (constructor forwarding), which could potentially assign this field. Therefore, we
      // need to check that the instance-put instruction comes after the parent constructor call.
      BasicBlock instancePutBlock = instancePut.getBlock();
      BasicBlock parentConstructorCallBlock = parentConstructorCall.getBlock();

      if (instancePutBlock != parentConstructorCallBlock) {
        // Check that the parent constructor call dominates the instance-put instruction.
        return getOrCreateDominatorTree().dominatedBy(instancePutBlock, parentConstructorCallBlock);
      }

      // Check that the parent constructor call comes before the instance-put instruction in the
      // block.
      for (Instruction instruction : instancePutBlock.getInstructions()) {
        if (instruction == instancePut) {
          return false;
        }
        if (instruction == parentConstructorCall) {
          return true;
        }
      }
      throw new Unreachable();
    }

    // Otherwise, conservatively return false.
    return false;
  }

  private boolean fieldNeverWrittenBetweenParentConstructorCallAndMethodExit(
      DexClassAndField field) {
    if (field.getAccessFlags().isFinal()) {
      return true;
    }
    if (appView.appInfo().isFieldOnlyWrittenInMethod(field, parentConstructor.getDefinition())) {
      return true;
    }
    // Otherwise, conservatively return false.
    return false;
  }
}
