// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;

public class InstanceFieldValueAnalysis extends FieldValueAnalysis {

  // Information about how this instance constructor initializes the fields on the newly created
  // instance.
  private final InstanceFieldInitializationInfoCollection.Builder builder =
      InstanceFieldInitializationInfoCollection.builder();

  private final InstanceFieldInitializationInfoFactory factory;

  private final DexEncodedMethod parentConstructor;
  private final InvokeDirect parentConstructorCall;

  private InstanceFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      DexEncodedMethod parentConstructor,
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

    DexEncodedMethod parentConstructor =
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
  boolean isSubjectToOptimization(DexEncodedField field) {
    return !field.isStatic() && field.holder() == context.getHolderType();
  }

  @Override
  void updateFieldOptimizationInfo(DexEncodedField field, FieldInstruction fieldPut, Value value) {
    if (fieldNeverWrittenBetweenInstancePutAndMethodExit(field, fieldPut.asInstancePut())) {
      recordFieldIsInitializedWithValue(field, value);
    }
  }

  private void analyzeParentConstructorCall() {
    InstanceFieldInitializationInfoCollection infos =
        parentConstructor
            .getOptimizationInfo()
            .getInstanceInitializerInfo()
            .fieldInitializationInfos();
    infos.forEach(
        appView,
        (field, info) -> {
          if (fieldNeverWrittenBetweenParentConstructorCallAndMethodExit(field)) {
            if (info.isArgumentInitializationInfo()) {
              int argumentIndex = info.asArgumentInitializationInfo().getArgumentIndex();
              recordFieldIsInitializedWithValue(
                  field, parentConstructorCall.arguments().get(argumentIndex));
            } else {
              assert info.isSingleValue() || info.isTypeInitializationInfo();
              builder.recordInitializationInfo(field, info);
            }
          }
        });
  }

  private void recordFieldIsInitializedWithValue(DexEncodedField field, Value value) {
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
      Argument argument = root.definition.asArgument();
      builder.recordInitializationInfo(
          field, factory.createArgumentInitializationInfo(argument.getIndex()));
      return;
    }

    AbstractValue abstractValue = value.getAbstractValue(appView, context);
    if (abstractValue.isSingleValue()) {
      builder.recordInitializationInfo(field, abstractValue.asSingleValue());
      return;
    }

    DexType fieldType = field.field.type;
    if (fieldType.isClassType()) {
      ClassTypeElement dynamicLowerBoundType = value.getDynamicLowerBoundType(appView);
      TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
      TypeElement staticFieldType = TypeElement.fromDexType(fieldType, maybeNull(), appView);
      if (dynamicLowerBoundType != null || !dynamicUpperBoundType.equals(staticFieldType)) {
        builder.recordInitializationInfo(
            field,
            factory.createTypeInitializationInfo(dynamicLowerBoundType, dynamicUpperBoundType));
      }
    }
  }

  private boolean fieldNeverWrittenBetweenInstancePutAndMethodExit(
      DexEncodedField field, InstancePut instancePut) {
    if (field.isFinal()) {
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
      DexEncodedField field) {
    if (field.isFinal()) {
      return true;
    }
    if (appView.appInfo().isFieldOnlyWrittenInMethod(field, parentConstructor)) {
      return true;
    }
    // Otherwise, conservatively return false.
    return false;
  }
}
