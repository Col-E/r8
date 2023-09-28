// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfoLookup;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class R8MemberValuePropagation extends MemberValuePropagation<AppInfoWithLiveness> {

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  public R8MemberValuePropagation(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  @Override
  void rewriteArrayGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      ArrayGet arrayGet) {
    TypeElement arrayType = arrayGet.array().getType();
    if (!arrayType.isArrayType()) {
      // Does not type check.
      return;
    }

    TypeElement memberType = arrayType.asArrayType().getMemberType();
    if (!memberType.isClassType()) {
      // We don't know what the value of the element is.
      return;
    }

    boolean isAlwaysNull = false;
    ClassTypeElement memberClassType = memberType.asClassType();
    if (memberClassType.getClassType().isAlwaysNull(appView)) {
      isAlwaysNull = true;
    } else if (memberClassType.getInterfaces().hasSingleKnownInterface()) {
      isAlwaysNull =
          memberClassType.getInterfaces().getSingleKnownInterface().isAlwaysNull(appView);
    }

    if (!isAlwaysNull) {
      // We don't know what the value of the element is.
      return;
    }

    BasicBlock block = arrayGet.getBlock();
    Position position = arrayGet.getPosition();

    // All usages are replaced by the replacement value.
    Instruction replacement =
        appView
            .abstractValueFactory()
            .createNullValue(memberType)
            .createMaterializingInstruction(appView, code, arrayGet);
    affectedValues.addAll(arrayGet.outValue().affectedValues());
    arrayGet.outValue().replaceUsers(replacement.outValue());

    // Insert the definition of the replacement.
    replacement.setPosition(position);
    if (block.hasCatchHandlers()) {
      iterator
          .splitCopyCatchHandlers(code, blocks, appView.options())
          .listIterator(code)
          .add(replacement);
    } else {
      iterator.add(replacement);
    }
  }

  private boolean mayPropagateValueFor(DexClassAndField field) {
    if (field.isProgramField()) {
      return appView.appInfo().mayPropagateValueFor(appView, field.getReference());
    }
    return appView.getAssumeInfoCollection().contains(field);
  }

  private boolean mayPropagateValueFor(DexClassAndMethod method) {
    if (method.isProgramMethod()) {
      return appView.appInfo().mayPropagateValueFor(appView, method.getReference());
    }
    return appView.getAssumeInfoCollection().contains(method);
  }

  @Override
  void rewriteInvokeMethod(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    if (invoke.hasUnusedOutValue()) {
      return;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexType invokedHolder = invokedMethod.getHolderType();
    if (!invokedHolder.isClassType()) {
      return;
    }

    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormatLegacy(invokedMethod)
            .asSingleResolution();
    if (resolutionResult == null) {
      return;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    AssumeInfo lookup = AssumeInfoLookup.lookupAssumeInfo(appView, resolutionResult, singleTarget);
    if (applyAssumeInfo(code, affectedValues, blocks, iterator, invoke, lookup)) {
      return;
    }

    // No Proguard rule could replace the instruction check for knowledge about the return value.
    if (singleTarget != null && !mayPropagateValueFor(singleTarget)) {
      return;
    }

    AbstractValue abstractReturnValue;
    if (invokedMethod.getReturnType().isAlwaysNull(appView)) {
      abstractReturnValue =
          appView.abstractValueFactory().createNullValue(invokedMethod.getReturnType());
    } else {
      MethodOptimizationInfo optimizationInfo =
          resolutionResult.getOptimizationInfo(appView, invoke, singleTarget);
      abstractReturnValue = optimizationInfo.getAbstractReturnValue();
    }

    if (abstractReturnValue.isSingleValue()) {
      SingleValue singleReturnValue = abstractReturnValue.asSingleValue();
      if (singleReturnValue.isMaterializableInContext(appView, context)) {
        BasicBlock block = invoke.getBlock();
        Position position = invoke.getPosition();

        Instruction replacement =
            singleReturnValue.createMaterializingInstruction(appView, code, invoke);
        affectedValues.addAll(invoke.outValue().affectedValues());
        invoke.moveDebugValues(replacement);
        invoke.outValue().replaceUsers(replacement.outValue());
        invoke.setOutValue(null);

        if (invoke.isInvokeMethodWithReceiver()) {
          iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, context);
        } else if (invoke.isInvokeStatic() && singleTarget != null) {
          iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
              appView, code, singleTarget.getHolderType());
        }

        // Insert the definition of the replacement.
        replacement.setPosition(position);
        if (block.hasCatchHandlers()) {
          iterator
              .splitCopyCatchHandlers(code, blocks, appView.options())
              .listIterator(code)
              .add(replacement);
        } else {
          iterator.add(replacement);
        }

        if (singleTarget != null) {
          singleTarget.getDefinition().getMutableOptimizationInfo().markAsPropagated();
        }
      }
    }
  }

  @Override
  void rewriteInstanceGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InstanceGet current) {
    rewriteFieldGet(code, affectedValues, blocks, iterator, current);
  }

  @Override
  void rewriteStaticGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      StaticGet current) {
    rewriteFieldGet(code, affectedValues, blocks, iterator, current);
  }

  @SuppressWarnings("ReferenceEquality")
  private void rewriteFieldGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      FieldInstruction current) {
    DexField field = current.getField();

    // TODO(b/123857022): Should be able to use definitionFor().
    SingleFieldResolutionResult<?> resolutionResult =
        appView.appInfo().resolveField(field).asSingleFieldResolutionResult();
    if (resolutionResult == null) {
      boolean replaceCurrentInstructionWithConstNull =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.wasRemoved(field), false);
      if (replaceCurrentInstructionWithConstNull) {
        iterator.replaceCurrentInstruction(code.createConstNull());
      }
      return;
    }

    if (resolutionResult.isAccessibleFrom(code.context(), appView).isPossiblyFalse()) {
      return;
    }

    DexClassAndField target = resolutionResult.getResolutionPair();
    DexEncodedField definition = target.getDefinition();
    if (definition.isStatic() != current.isStaticGet()) {
      return;
    }

    if (current.isStaticGet() && current.hasUnusedOutValue()) {
      // Replace by initclass.
      iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
          appView, code, field.getHolderType());
      return;
    }

    if (!mayPropagateValueFor(target)) {
      return;
    }

    // Check if there is a Proguard configuration rule that specifies the value of the field.
    AssumeInfo lookup = appView.getAssumeInfoCollection().get(target);
    if (applyAssumeInfo(code, affectedValues, blocks, iterator, current, lookup)) {
      return;
    }

    AbstractValue abstractValue;
    if (field.getType().isAlwaysNull(appView)) {
      abstractValue = appView.abstractValueFactory().createNullValue(field.getType());
    } else if (appView.appInfo().isFieldWrittenByFieldPutInstruction(target)) {
      abstractValue = definition.getOptimizationInfo().getAbstractValue();
      if (!definition.isStatic()) {
        AbstractValue abstractReceiverValue =
            current.asInstanceGet().object().getAbstractValue(appView, code.context());
        if (abstractReceiverValue.hasObjectState()) {
          AbstractValue abstractValueFromObjectState =
              abstractReceiverValue.getObjectState().getAbstractFieldValue(definition);
          if (!abstractValueFromObjectState.isUnknown()) {
            // Prefer the abstract value from the current context, as this should be more precise
            // than the abstract value we computed for the field. If this is not always true, the
            // meet of the two values could be computed.
            abstractValue = abstractValueFromObjectState;
          }
        }
      }
    } else if (definition.isStatic()) {
      // This is guaranteed to read the static value of the field.
      abstractValue = definition.getStaticValue().toAbstractValue(appView.abstractValueFactory());
      // Verify that the optimization info is consistent with the static value.
      assert verifyStaticFieldValueConsistentWithOptimizationInfo(appView, definition);
    } else {
      // This is guaranteed to read the default value of the field.
      abstractValue = appView.abstractValueFactory().createDefaultValue(field.getType());
    }

    if (abstractValue.isSingleValue()) {
      SingleValue singleValue = abstractValue.asSingleValue();
      if (singleValue.isSingleFieldValue()
          && singleValue.asSingleFieldValue().getField() == field) {
        return;
      }
      if (singleValue.isMaterializableInContext(appView, code.context())) {
        BasicBlock block = current.getBlock();
        ProgramMethod context = code.context();
        Position position = current.getPosition();

        // All usages are replaced by the replacement value.
        Instruction replacement =
            singleValue.createMaterializingInstruction(appView, code, current);
        affectedValues.addAll(current.outValue().affectedValues());
        current.outValue().replaceUsers(replacement.outValue());

        // To preserve side effects, original field-get is replaced by an explicit null-check, if
        // the field-get instruction may only fail with an NPE, or the field-get remains as-is.
        if (current.isInstanceGet()) {
          iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, context);
        } else {
          assert current.isStaticGet();
          iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
              appView, code, target.getHolderType());
        }

        // Insert the definition of the replacement.
        replacement.setPosition(position);
        if (block.hasCatchHandlers()) {
          iterator
              .splitCopyCatchHandlers(code, blocks, appView.options())
              .listIterator(code)
              .add(replacement);
        } else {
          iterator.add(replacement);
        }

        feedback.markFieldAsPropagated(definition);
      }
    }
  }

  private boolean verifyStaticFieldValueConsistentWithOptimizationInfo(
      AppView<?> appView, DexEncodedField field) {
    AbstractValue computedValue = field.getOptimizationInfo().getAbstractValue();
    AbstractValue staticValue =
        field.getStaticValue().toAbstractValue(appView.abstractValueFactory());
    assert computedValue.isUnknown()
        || !field.hasExplicitStaticValue()
        || appView
            .getAbstractValueConstantPropagationJoiner()
            .lessThanOrEqualTo(staticValue, computedValue, field.getTypeElement(appView));
    return true;
  }

  @Override
  void rewriteInstancePut(IRCode code, InstructionListIterator iterator, InstancePut current) {
    replaceInstancePutByNullCheckIfNeverRead(code, iterator, current);
  }

  private void replaceInstancePutByNullCheckIfNeverRead(
      IRCode code, InstructionListIterator iterator, InstancePut current) {
    DexClassAndField field = appView.appInfo().resolveField(current.getField()).getResolutionPair();
    if (field == null || field.getAccessFlags().isStatic()) {
      return;
    }

    // If the field is read, we can't remove the instance-put unless the value of the field is known
    // to be null (in which case the instance-put is a no-op because it assigns the field the same
    // value as its default value).
    if (field.getType().isAlwaysNull(appView) || !appView.appInfo().isFieldRead(field)) {
      iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
      return;
    }

    if (appView.getAssumeInfoCollection().isMaterializableInAllContexts(appView, field)) {
      iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
    }
  }

  @Override
  void rewriteStaticPut(IRCode code, InstructionListIterator iterator, StaticPut current) {
    replaceStaticPutByInitClassIfNeverRead(code, iterator, current);
  }

  private void replaceStaticPutByInitClassIfNeverRead(
      IRCode code, InstructionListIterator iterator, StaticPut current) {
    DexClassAndField field = appView.appInfo().resolveField(current.getField()).getResolutionPair();
    if (field == null || !field.getAccessFlags().isStatic()) {
      return;
    }

    // If the field is read, we can't remove the static-put unless the value of the field is known
    // to be null (in which case the static-put is a no-op because it assigns the field the same
    // value as its default value).
    if (field.getType().isAlwaysNull(appView) || !appView.appInfo().isFieldRead(field)) {
      iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
          appView, code, field.getHolderType());
      return;
    }

    if (appView.getAssumeInfoCollection().isMaterializableInAllContexts(appView, field)) {
      iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
          appView, code, field.getHolderType());
    }
  }
}
