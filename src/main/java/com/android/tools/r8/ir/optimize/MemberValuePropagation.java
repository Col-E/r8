// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberRuleReturnValue;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
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

  private enum RuleType {
    NONE,
    ASSUME_NO_SIDE_EFFECTS,
    ASSUME_VALUES
  }

  private static class ProguardMemberRuleLookup {

    final RuleType type;
    final ProguardMemberRule rule;

    ProguardMemberRuleLookup(RuleType type, ProguardMemberRule rule) {
      this.type = type;
      this.rule = rule;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ProguardMemberRuleLookup)) {
        return false;
      }
      ProguardMemberRuleLookup otherLookup = (ProguardMemberRuleLookup) other;
      return type == otherLookup.type && rule == otherLookup.rule;
    }

    @Override
    public int hashCode() {
      return type.ordinal() * 31 + rule.hashCode();
    }
  }

  public MemberValuePropagation(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.reporter = appView.options().reporter;
  }

  private boolean mayPropagateValueFor(DexEncodedField field) {
    if (field.isProgramField(appView)) {
      return appView.appInfo().mayPropagateValueFor(field.field);
    }
    return appView.appInfo().assumedValues.containsKey(field.field)
        || appView.appInfo().noSideEffects.containsKey(field.field);
  }

  private boolean mayPropagateValueFor(DexClassAndMethod method) {
    if (method.isProgramMethod()) {
      return appView.appInfo().mayPropagateValueFor(method.getReference());
    }
    return appView.appInfo().assumedValues.containsKey(method.getReference())
        || appView.appInfo().noSideEffects.containsKey(method.getReference());
  }

  private ProguardMemberRuleLookup lookupMemberRule(DexClassAndMethod method) {
    return method != null ? lookupMemberRule(method.getDefinition()) : null;
  }

  private ProguardMemberRuleLookup lookupMemberRule(DexDefinition definition) {
    if (definition == null) {
      return null;
    }
    DexReference reference = definition.toReference();
    ProguardMemberRule rule = appView.appInfo().noSideEffects.get(reference);
    if (rule != null) {
      return new ProguardMemberRuleLookup(RuleType.ASSUME_NO_SIDE_EFFECTS, rule);
    }
    rule = appView.appInfo().assumedValues.get(reference);
    if (rule != null) {
      return new ProguardMemberRuleLookup(RuleType.ASSUME_VALUES, rule);
    }
    return null;
  }

  private Instruction constantReplacementFromProguardRule(
      ProguardMemberRule rule, IRCode code, Instruction instruction) {
    if (rule == null || !rule.hasReturnValue()) {
      return null;
    }

    ProguardMemberRuleReturnValue returnValueRule = rule.getReturnValue();

    // Check if this value can be assumed constant.
    if (returnValueRule.isSingleValue()) {
      return appView.abstractValueFactory()
          .createSingleNumberValue(returnValueRule.getSingleValue())
          .createMaterializingInstruction(appView, code, instruction);
    }

    if (returnValueRule.isField()) {
      DexField field = returnValueRule.getField();
      assert instruction.getOutType() == TypeElement.fromDexType(field.type, maybeNull(), appView);

      DexEncodedField staticField = appView.appInfo().lookupStaticTarget(field);
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

      Instruction replacement =
          staticField.valueAsConstInstruction(code, instruction.getLocalInfo(), appView);
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

  private void setValueRangeFromProguardRule(ProguardMemberRule rule, Value value) {
    if (rule.hasReturnValue() && rule.getReturnValue().isValueRange()) {
      assert !rule.getReturnValue().isSingleValue();
      value.setValueRange(rule.getReturnValue().getValueRange());
    }
  }

  private boolean tryConstantReplacementFromProguard(
      IRCode code,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      Instruction current,
      ProguardMemberRuleLookup lookup) {
    Instruction replacement = constantReplacementFromProguardRule(lookup.rule, code, current);
    if (replacement == null) {
      // Check to see if a value range can be assumed.
      setValueRangeFromProguardRule(lookup.rule, current.outValue());
      return false;
    }
    affectedValues.addAll(current.outValue().affectedValues());
    if (lookup.type == RuleType.ASSUME_NO_SIDE_EFFECTS) {
      iterator.replaceCurrentInstruction(replacement);
    } else {
      assert lookup.type == RuleType.ASSUME_VALUES;
      BasicBlock block = current.getBlock();
      Position position = current.getPosition();
      if (current.hasOutValue()) {
        assert replacement.outValue() != null;
        current.outValue().replaceUsers(replacement.outValue());
      }
      if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        replaceInstructionByInitClassIfPossible(
            staticGet, staticGet.getField().holder, code, iterator, code.context());
      }
      replacement.setPosition(position);
      if (block.hasCatchHandlers()) {
        iterator.split(code, blocks).listIterator(code).add(replacement);
      } else {
        iterator.add(replacement);
      }
    }
    return true;
  }

  private void rewriteInvokeMethodWithConstantValues(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.proto.returnType.isVoidType()) {
      return;
    }

    if (!invoke.hasOutValue() || !invoke.outValue().hasNonDebugUsers()) {
      return;
    }

    DexType invokedHolder = invokedMethod.holder;
    if (!invokedHolder.isClassType()) {
      return;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    ProguardMemberRuleLookup lookup = lookupMemberRule(singleTarget);
    if (lookup == null) {
      // -assumenosideeffects rules are applied to upward visible and overriding methods, but only
      // references that have actual definitions are marked by the root set builder. So, here, we
      // try again with a resolved target, not the direct definition, which may not exist.
      DexEncodedMethod resolutionTarget =
          appView.appInfo().unsafeResolveMethodDueToDexFormat(invokedMethod).getSingleTarget();
      lookup = lookupMemberRule(resolutionTarget);
    }

    if (lookup != null) {
      // Check to see if a constant value can be assumed.
      // But, if the current matched rule is -assumenosideeffects without the return value, it won't
      // be transformed into a replacement instruction. Check if there is -assumevalues rule bound
      // to the target.
      if (singleTarget != null
          && lookup.type == RuleType.ASSUME_NO_SIDE_EFFECTS
          && !lookup.rule.hasReturnValue()) {
        ProguardMemberRule rule = appView.appInfo().assumedValues.get(singleTarget.getReference());
        if (rule != null) {
          lookup = new ProguardMemberRuleLookup(RuleType.ASSUME_VALUES, rule);
        }
      }
      if (tryConstantReplacementFromProguard(
          code, affectedValues, blocks, iterator, invoke, lookup)) {
        return;
      }
    }

    // No Proguard rule could replace the instruction check for knowledge about the return value.
    if (singleTarget == null || !mayPropagateValueFor(singleTarget)) {
      return;
    }

    AbstractValue abstractReturnValue =
        singleTarget.getDefinition().getOptimizationInfo().getAbstractReturnValue();

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
          replaceInstructionByNullCheckIfPossible(invoke, iterator, context);
        } else if (invoke.isInvokeStatic()) {
          replaceInstructionByInitClassIfPossible(
              invoke, singleTarget.getHolderType(), code, iterator, context);
        }

        // Insert the definition of the replacement.
        replacement.setPosition(position);
        if (block.hasCatchHandlers()) {
          iterator.split(code, blocks).listIterator(code).add(replacement);
        } else {
          iterator.add(replacement);
        }
        singleTarget.getDefinition().getMutableOptimizationInfo().markAsPropagated();
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
    DexEncodedField target = appView.appInfo().resolveField(field).getResolvedField();
    if (target == null) {
      boolean replaceCurrentInstructionWithConstNull =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.wasRemoved(field), false);
      if (replaceCurrentInstructionWithConstNull) {
        iterator.replaceCurrentInstruction(code.createConstNull());
      }
      return;
    }

    if (target.isStatic() != current.isStaticGet()) {
      return;
    }

    if (!mayPropagateValueFor(target)) {
      return;
    }

    // Check if there is a Proguard configuration rule that specifies the value of the field.
    ProguardMemberRuleLookup lookup = lookupMemberRule(target);
    if (lookup != null
        && tryConstantReplacementFromProguard(
            code, affectedValues, blocks, iterator, current, lookup)) {
      return;
    }

    AbstractValue abstractValue;
    if (appView.appInfo().isFieldWrittenByFieldPutInstruction(target)) {
      abstractValue = target.getOptimizationInfo().getAbstractValue();
      if (abstractValue.isUnknown() && !target.isStatic()) {
        AbstractValue abstractReceiverValue =
            current.asInstanceGet().object().getAbstractValue(appView, code.context());
        if (abstractReceiverValue.isSingleFieldValue()) {
          abstractValue =
              abstractReceiverValue.asSingleFieldValue().getState().getAbstractFieldValue(target);
        }
      }
    } else if (target.isStatic()) {
      // This is guaranteed to read the static value of the field.
      abstractValue = target.getStaticValue().toAbstractValue(appView.abstractValueFactory());
      // Verify that the optimization info is consistent with the static value.
      assert target.getOptimizationInfo().getAbstractValue().isUnknown()
          || !target.hasExplicitStaticValue()
          || abstractValue == target.getOptimizationInfo().getAbstractValue();
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
          replaceInstructionByNullCheckIfPossible(current, iterator, context);
        } else {
          assert current.isStaticGet();
          replaceInstructionByInitClassIfPossible(
              current, target.holder(), code, iterator, context);
        }

        // Insert the definition of the replacement.
        replacement.setPosition(position);
        if (block.hasCatchHandlers()) {
          iterator.split(code, blocks).listIterator(code).add(replacement);
        } else {
          iterator.add(replacement);
        }
        feedback.markFieldAsPropagated(target);
      }
    }
  }

  private void replaceInstructionByNullCheckIfPossible(
      Instruction instruction, InstructionListIterator iterator, ProgramMethod context) {
    assert instruction.isInstanceFieldInstruction() || instruction.isInvokeMethodWithReceiver();
    assert !instruction.hasOutValue() || !instruction.outValue().hasAnyUsers();
    if (instruction.instructionMayHaveSideEffects(
        appView, context, Instruction.SideEffectAssumption.RECEIVER_NOT_NULL)) {
      return;
    }
    Value receiver =
        instruction.isInstanceFieldInstruction()
            ? instruction.asInstanceFieldInstruction().object()
            : instruction.asInvokeMethodWithReceiver().getReceiver();
    if (receiver.isNeverNull()) {
      iterator.removeOrReplaceByDebugLocalRead();
      return;
    }
    InvokeMethod replacement;
    if (appView.options().canUseRequireNonNull()) {
      DexMethod requireNonNullMethod = appView.dexItemFactory().objectsMethods.requireNonNull;
      replacement = new InvokeStatic(requireNonNullMethod, null, ImmutableList.of(receiver));
    } else {
      DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
      replacement = new InvokeVirtual(getClassMethod, null, ImmutableList.of(receiver));
    }
    iterator.replaceCurrentInstruction(replacement);
  }

  private void replaceInstructionByInitClassIfPossible(
      Instruction instruction,
      DexType holder,
      IRCode code,
      InstructionListIterator iterator,
      ProgramMethod context) {
    assert instruction.isStaticFieldInstruction() || instruction.isInvokeStatic();
    if (instruction.instructionMayHaveSideEffects(
        appView, context, Instruction.SideEffectAssumption.CLASS_ALREADY_INITIALIZED)) {
      return;
    }
    boolean classInitializationMayHaveSideEffects =
        holder.classInitializationMayHaveSideEffects(
            appView,
            // Types that are a super type of `context` are guaranteed to be initialized
            // already.
            type -> appView.appInfo().isSubtype(context.getHolderType(), type),
            Sets.newIdentityHashSet());
    if (!classInitializationMayHaveSideEffects) {
      iterator.removeOrReplaceByDebugLocalRead();
      return;
    }
    if (!appView.canUseInitClass()) {
      return;
    }
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(holder));
    if (clazz != null) {
      Value dest = code.createValue(TypeElement.getInt());
      iterator.replaceCurrentInstruction(new InitClass(dest, clazz.type));
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

    replaceInstructionByNullCheckIfPossible(current, iterator, code.context());
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

    replaceInstructionByInitClassIfPossible(
        current, field.holder(), code, iterator, code.context());
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
        if (current.isInvokeMethod()) {
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
