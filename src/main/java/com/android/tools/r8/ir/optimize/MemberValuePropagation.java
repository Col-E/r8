// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfoLookup;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRuleReturnValue;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

public class MemberValuePropagation {

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  private final AppView<AppInfoWithLiveness> appView;
  private final Reporter reporter;

  // Fields for which we have reported warnings to due Proguard configuration rules.
  private final Set<DexField> warnedFields = Sets.newIdentityHashSet();

  public MemberValuePropagation(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.reporter = appView.options().reporter;
  }

  private void rewriteArrayGet(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
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
            .createNullValue()
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
      return appView.appInfo().mayPropagateValueFor(field.getReference());
    }
    return appView.appInfo().assumedValues.containsKey(field.getReference())
        || appView.appInfo().noSideEffects.containsKey(field.getReference());
  }

  private boolean mayPropagateValueFor(DexClassAndMethod method) {
    if (method.isProgramMethod()) {
      return appView.appInfo().mayPropagateValueFor(method.getReference());
    }
    return appView.appInfo().assumedValues.containsKey(method.getReference())
        || appView.appInfo().noSideEffects.containsKey(method.getReference());
  }

  private Instruction createReplacementFromAssumeInfo(
      AssumeInfo assumeInfo, IRCode code, Instruction instruction) {
    if (!assumeInfo.hasReturnInfo()) {
      return null;
    }

    ProguardMemberRuleReturnValue returnValueRule = assumeInfo.getReturnInfo();

    // Check if this value can be assumed constant.
    if (returnValueRule.isSingleValue()) {
      if (instruction.getOutType().isReferenceType()) {
        if (returnValueRule.getSingleValue() == 0) {
          return appView
              .abstractValueFactory()
              .createNullValue()
              .createMaterializingInstruction(appView, code, instruction);
        }
        return null;
      }
      return appView.abstractValueFactory()
          .createSingleNumberValue(returnValueRule.getSingleValue())
          .createMaterializingInstruction(appView, code, instruction);
    }

    if (returnValueRule.isField()) {
      DexField field = returnValueRule.getField();
      assert instruction.getOutType() == TypeElement.fromDexType(field.type, maybeNull(), appView);

      DexClassAndField staticField = appView.appInfo().lookupStaticTarget(field);
      if (staticField == null) {
        if (warnedFields.add(field)) {
          reporter.warning(
              new StringDiagnostic(
                  "Field `"
                      + field.toSourceString()
                      + "` is used in an -assumevalues rule but does not exist.",
                  code.origin));
        }
        return null;
      }

      if (AccessControl.isMemberAccessible(
              staticField, staticField.getHolder(), code.context(), appView)
          .isTrue()) {
        return StaticGet.builder()
            .setField(field)
            .setFreshOutValue(code, field.getTypeElement(appView), instruction.getLocalInfo())
            .build();
      }

      Instruction replacement =
          staticField
              .getDefinition()
              .valueAsConstInstruction(code, instruction.getLocalInfo(), appView);
      if (replacement == null) {
        reporter.warning(
            new StringDiagnostic(
                "Unable to apply the rule `"
                    + returnValueRule.toString()
                    + "`: Could not determine the value of field `"
                    + field.toSourceString()
                    + "`",
                code.origin));
        return null;
      }
      return replacement;
    }

    return null;
  }

  private void setValueRangeFromAssumeInfo(AssumeInfo assumeInfo, Value value) {
    if (assumeInfo.hasReturnInfo() && assumeInfo.getReturnInfo().isValueRange()) {
      assert !assumeInfo.getReturnInfo().isSingleValue();
      value.setValueRange(assumeInfo.getReturnInfo().getValueRange());
    }
  }

  private boolean applyAssumeInfoIfPossible(
      IRCode code,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      Instruction current,
      AssumeInfo assumeInfo) {
    Instruction replacement = createReplacementFromAssumeInfo(assumeInfo, code, current);
    if (replacement == null) {
      // Check to see if a value range can be assumed.
      if (current.getOutType().isPrimitiveType()) {
        setValueRangeFromAssumeInfo(assumeInfo, current.outValue());
      }
      return false;
    }
    affectedValues.addAll(current.outValue().affectedValues());
    if (assumeInfo.isAssumeNoSideEffects()) {
      iterator.replaceCurrentInstruction(replacement);
    } else {
      assert assumeInfo.isAssumeValues();
      BasicBlock block = current.getBlock();
      Position position = current.getPosition();
      if (current.hasOutValue()) {
        assert replacement.outValue() != null;
        current.outValue().replaceUsers(replacement.outValue());
      }
      if (current.isInstanceGet()) {
        iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        iterator.replaceCurrentInstructionByInitClassIfPossible(
            appView, code, staticGet.getField().holder);
      }
      replacement.setPosition(position);
      if (block.hasCatchHandlers()) {
        BasicBlock splitBlock = iterator.split(code, blocks);
        splitBlock.listIterator(code).add(replacement);

        // Process the materialized value.
        blocks.previous();
        assert !iterator.hasNext();
        assert IteratorUtils.peekNext(blocks) == splitBlock;

        return true;
      } else {
        iterator.add(replacement);
      }
    }

    // Process the materialized value.
    iterator.previous();
    assert iterator.peekNext() == replacement;

    return true;
  }

  private void rewriteInvokeMethodWithConstantValues(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
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

    SingleResolutionResult resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormat(invokedMethod).asSingleResolution();
    if (resolutionResult == null) {
      return;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    AssumeInfo lookup = AssumeInfoLookup.lookupAssumeInfo(appView, resolutionResult, singleTarget);
    if (lookup != null
        && applyAssumeInfoIfPossible(code, affectedValues, blocks, iterator, invoke, lookup)) {
      return;
    }

    // No Proguard rule could replace the instruction check for knowledge about the return value.
    if (singleTarget != null && !mayPropagateValueFor(singleTarget)) {
      return;
    }

    AbstractValue abstractReturnValue;
    if (invokedMethod.getReturnType().isAlwaysNull(appView)) {
      abstractReturnValue = appView.abstractValueFactory().createNullValue();
    } else if (singleTarget != null) {
      abstractReturnValue =
          singleTarget.getDefinition().getOptimizationInfo().getAbstractReturnValue();
    } else {
      abstractReturnValue = UnknownValue.getInstance();
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
          iterator.replaceCurrentInstructionByInitClassIfPossible(
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

  private void rewriteFieldGetWithConstantValues(
      IRCode code,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      FieldInstruction current) {
    DexField field = current.getField();

    // TODO(b/123857022): Should be able to use definitionFor().
    SuccessfulFieldResolutionResult resolutionResult =
        appView.appInfo().resolveField(field).asSuccessfulResolution();
    if (resolutionResult == null) {
      boolean replaceCurrentInstructionWithConstNull =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.wasRemoved(field), false);
      if (replaceCurrentInstructionWithConstNull) {
        iterator.replaceCurrentInstruction(code.createConstNull());
      }
      return;
    }

    DexClassAndField target = resolutionResult.getResolutionPair();
    DexEncodedField definition = target.getDefinition();
    if (definition.isStatic() != current.isStaticGet()) {
      return;
    }

    if (!mayPropagateValueFor(target)) {
      return;
    }

    // Check if there is a Proguard configuration rule that specifies the value of the field.
    AssumeInfo lookup = AssumeInfoLookup.lookupAssumeInfo(appView, target);
    if (lookup != null
        && applyAssumeInfoIfPossible(code, affectedValues, blocks, iterator, current, lookup)) {
      return;
    }

    AbstractValue abstractValue;
    if (field.getType().isAlwaysNull(appView)) {
      abstractValue = appView.abstractValueFactory().createSingleNumberValue(0);
    } else if (appView.appInfo().isFieldWrittenByFieldPutInstruction(definition)) {
      abstractValue = definition.getOptimizationInfo().getAbstractValue();
      if (abstractValue.isUnknown() && !definition.isStatic()) {
        AbstractValue abstractReceiverValue =
            current.asInstanceGet().object().getAbstractValue(appView, code.context());
        if (abstractReceiverValue.hasObjectState()) {
          abstractValue = abstractReceiverValue.getObjectState().getAbstractFieldValue(definition);
        }
      }
    } else if (definition.isStatic()) {
      // This is guaranteed to read the static value of the field.
      abstractValue = definition.getStaticValue().toAbstractValue(appView.abstractValueFactory());
      // Verify that the optimization info is consistent with the static value.
      assert definition.getOptimizationInfo().getAbstractValue().isUnknown()
          || !definition.hasExplicitStaticValue()
          || abstractValue.equals(definition.getOptimizationInfo().getAbstractValue());
    } else {
      // This is guaranteed to read the default value of the field.
      abstractValue = appView.abstractValueFactory().createSingleNumberValue(0);
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
          iterator.replaceCurrentInstructionByInitClassIfPossible(
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

  private void replaceInstancePutByNullCheckIfNeverRead(
      IRCode code, InstructionListIterator iterator, InstancePut current) {
    DexEncodedField field = appView.appInfo().resolveField(current.getField()).getResolvedField();
    if (field == null || field.isStatic()) {
      return;
    }

    // If the field is read, we can't remove the instance-put unless the value of the field is known
    // to be null (in which case the instance-put is a no-op because it assigns the field the same
    // value as its default value).
    if (!field.type().isAlwaysNull(appView) && appView.appInfo().isFieldRead(field)) {
      return;
    }

    iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
  }

  private void replaceStaticPutByInitClassIfNeverRead(
      IRCode code, InstructionListIterator iterator, StaticPut current) {
    DexEncodedField field = appView.appInfo().resolveField(current.getField()).getResolvedField();
    if (field == null || !field.isStatic()) {
      return;
    }

    // If the field is read, we can't remove the static-put unless the value of the field is known
    // to be null (in which case the static-put is a no-op because it assigns the field the same
    // value as its default value).
    if (!field.type().isAlwaysNull(appView) && appView.appInfo().isFieldRead(field)) {
      return;
    }

    iterator.replaceCurrentInstructionByInitClassIfPossible(appView, code, field.getHolderType());
  }

  /**
   * Replace invoke targets and field accesses with constant values where possible.
   *
   * <p>Also assigns value ranges to values where possible.
   */
  public void run(IRCode code) {
    IRMetadata metadata = code.metadata();
    if (!metadata.mayHaveFieldInstruction() && !metadata.mayHaveInvokeMethod()) {
      return;
    }
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    run(code, code.listIterator(), affectedValues, alwaysTrue());
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
    assert code.verifyTypes(appView);
  }

  public void run(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      Set<Value> affectedValues,
      Predicate<BasicBlock> blockTester) {
    ProgramMethod context = code.context();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (!blockTester.test(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isArrayGet()) {
          rewriteArrayGet(
              code, context, affectedValues, blockIterator, iterator, current.asArrayGet());
        } else if (current.isInvokeMethod()) {
          rewriteInvokeMethodWithConstantValues(
              code, context, affectedValues, blockIterator, iterator, current.asInvokeMethod());
        } else if (current.isFieldGet()) {
          rewriteFieldGetWithConstantValues(
              code, affectedValues, blockIterator, iterator, current.asFieldInstruction());
        } else if (current.isInstancePut()) {
          replaceInstancePutByNullCheckIfNeverRead(code, iterator, current.asInstancePut());
        } else if (current.isStaticPut()) {
          replaceStaticPutByInitClassIfNeverRead(code, iterator, current.asStaticPut());
        }
      }
    }
  }
}
