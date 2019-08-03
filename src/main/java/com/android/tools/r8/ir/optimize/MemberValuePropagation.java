// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.TrivialClassInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberRuleReturnValue;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

public class MemberValuePropagation {

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

  private boolean mayPropagateValueFor(DexEncodedMethod method) {
    if (method.isProgramMethod(appView)) {
      return appView.appInfo().mayPropagateValueFor(method.method);
    }
    return appView.appInfo().assumedValues.containsKey(method.method)
        || appView.appInfo().noSideEffects.containsKey(method.method);
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
    TypeLatticeElement typeLattice = instruction.outValue().getTypeLattice();

    // Check if this value can be assumed constant.
    if (returnValueRule.isSingleValue()) {
      return createConstNumberReplacement(
          code, returnValueRule.getSingleValue(), typeLattice, instruction.getLocalInfo());
    }

    if (returnValueRule.isField()) {
      DexField field = returnValueRule.getField();
      assert typeLattice
          == TypeLatticeElement.fromDexType(field.type, Nullability.maybeNull(), appView);

      DexEncodedField staticField = appView.appInfo().lookupStaticTarget(field.holder, field);
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

      Value value = code.createValue(typeLattice, instruction.getLocalInfo());
      ConstInstruction replacement = staticField.valueAsConstInstruction(code, value, appView);
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
      if (replacement.isDexItemBasedConstString()) {
        code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
      }
      return replacement;
    }

    return null;
  }

  private static ConstNumber createConstNumberReplacement(
      IRCode code, long constant, TypeLatticeElement typeLattice, DebugLocalInfo debugLocalInfo) {
    assert !typeLattice.isReference() || constant == 0;
    Value returnedValue =
        code.createValue(
            typeLattice.isReference() ? TypeLatticeElement.NULL : typeLattice, debugLocalInfo);
    return new ConstNumber(returnedValue, constant);
  }

  private ConstString createConstStringReplacement(
      IRCode code,
      DexString constant,
      TypeLatticeElement typeLattice,
      DebugLocalInfo debugLocalInfo) {
    assert typeLattice.isClassType();
    assert appView
        .isSubtype(
            appView.dexItemFactory().stringType,
            typeLattice.asClassTypeLatticeElement().getClassType())
        .isTrue();
    Value returnedValue = code.createValue(typeLattice, debugLocalInfo);
    ConstString instruction =
        new ConstString(
            returnedValue, constant, ThrowingInfo.defaultForConstString(appView.options()));
    assert !instruction.instructionInstanceCanThrow();
    return instruction;
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
      if (current.outValue() != null) {
        assert replacement.outValue() != null;
        current.outValue().replaceUsers(replacement.outValue());
      }
      replacement.setPosition(current.getPosition());
      if (current.getBlock().hasCatchHandlers()) {
        iterator.split(code, blocks).listIterator(code).add(replacement);
      } else {
        iterator.add(replacement);
      }
    }
    return true;
  }

  private void rewriteInvokeMethodWithConstantValues(
      IRCode code,
      DexType callingContext,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      InvokeMethod current) {
    DexMethod invokedMethod = current.getInvokedMethod();
    DexType invokedHolder = invokedMethod.holder;
    if (!invokedHolder.isClassType()) {
      return;
    }
    DexEncodedMethod definition = current.lookupSingleTarget(appView, callingContext);
    if (definition != null && definition.isInstanceInitializer()) {
      // Member value propagation does not apply to constructors. Removing a call to a constructor
      // that is marked as having no side effects could lead to verification errors, due to
      // uninitialized instances being used.
      return;
    }

    ProguardMemberRuleLookup lookup = lookupMemberRule(definition);
    if (lookup == null) {
      // -assumenosideeffects rules are applied to upward visible and overriding methods, but only
      // references that have actual definitions are marked by the root set builder. So, here, we
      // try again with a resolved target, not the direct definition, which may not exist.
      DexEncodedMethod target =
          appView.appInfo().resolveMethod(invokedHolder, invokedMethod).asSingleTarget();
      lookup = lookupMemberRule(target);
    }
    boolean invokeReplaced = false;
    if (lookup != null) {
      boolean outValueNullOrNotUsed = current.outValue() == null || !current.outValue().isUsed();
      if (lookup.type == RuleType.ASSUME_NO_SIDE_EFFECTS && outValueNullOrNotUsed) {
        // Remove invoke if marked as having no side effects and the return value is not used.
        iterator.removeOrReplaceByDebugLocalRead();
        return;
      }

      if (!outValueNullOrNotUsed) {
        // Check to see if a constant value can be assumed.
        invokeReplaced =
            tryConstantReplacementFromProguard(
                code, affectedValues, blocks, iterator, current, lookup);
      }
    }
    if (invokeReplaced || current.outValue() == null) {
      return;
    }
    // No Proguard rule could replace the instruction check for knowledge about the return value.
    DexEncodedMethod target = current.lookupSingleTarget(appView, callingContext);
    if (target == null || !mayPropagateValueFor(target)) {
      return;
    }
    if (target.getOptimizationInfo().returnsConstant()) {
      ConstInstruction replacement;
      if (target.getOptimizationInfo().returnsConstantNumber()) {
        long constant = target.getOptimizationInfo().getReturnedConstantNumber();
        replacement =
            createConstNumberReplacement(
                code, constant, current.outValue().getTypeLattice(), current.getLocalInfo());
      } else {
        assert target.getOptimizationInfo().returnsConstantString();
        DexString constant = target.getOptimizationInfo().getReturnedConstantString();
        replacement =
            createConstStringReplacement(
                code, constant, current.outValue().getTypeLattice(), current.getLocalInfo());
      }

      affectedValues.addAll(current.outValue().affectedValues());
      current.outValue().replaceUsers(replacement.outValue());
      current.setOutValue(null);
      replacement.setPosition(current.getPosition());
      current.moveDebugValues(replacement);
      if (current.getBlock().hasCatchHandlers()) {
        iterator.split(code, blocks).listIterator(code).add(replacement);
      } else {
        iterator.add(replacement);
      }
      target.getMutableOptimizationInfo().markAsPropagated();
      return;
    }
    if (target.getOptimizationInfo().neverReturnsNull()
        && current.outValue().getTypeLattice().isReference()
        && current.outValue().canBeNull()) {
      insertAssumeNotNull(code, affectedValues, blocks, iterator, current);
    }
  }

  private void rewriteStaticGetWithConstantValues(
      IRCode code,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      StaticGet current) {
    DexField field = current.getField();

    // TODO(b/123857022): Should be able to use definitionFor().
    DexEncodedField target = appView.appInfo().lookupStaticTarget(field.holder, field);
    if (target == null) {
      boolean replaceCurrentInstructionWithConstNull =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.wasRemoved(field), false);
      if (replaceCurrentInstructionWithConstNull) {
        iterator.replaceCurrentInstruction(code.createConstNull());
      }
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

    // Check if a this value is known const.
    if (!appView.appInfo().isPinned(target.field)) {
      ConstInstruction replacement = target.valueAsConstInstruction(code, current.dest(), appView);
      if (replacement != null) {
        affectedValues.addAll(current.outValue().affectedValues());
        iterator.replaceCurrentInstruction(replacement);
        if (replacement.isDexItemBasedConstString()) {
          code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
        }
        target.getMutableOptimizationInfo().markAsPropagated();
        return;
      }
    }

    if (current.dest() != null) {
      // In case the class holder of this static field satisfying following criteria:
      //   -- cannot trigger other static initializer except for its own
      //   -- is final
      //   -- has a class initializer which is classified as trivial
      //      (see CodeRewriter::computeClassInitializerInfo) and
      //      initializes the field being accessed
      //
      // ... and the field itself is not pinned by keep rules (in which case it might
      // be updated outside the class constructor, e.g. via reflections), it is safe
      // to assume that the static-get instruction reads the value it initialized value
      // in class initializer and is never null.
      DexClass holderDefinition = appView.definitionFor(field.holder);
      if (holderDefinition != null
          && holderDefinition.accessFlags.isFinal()
          && !field.holder.initializationOfParentTypesMayHaveSideEffects(appView)) {
        Value outValue = current.dest();
        DexEncodedMethod classInitializer = holderDefinition.getClassInitializer();
        if (classInitializer != null && !isProcessedConcurrently.test(classInitializer)) {
          TrivialInitializer info =
              classInitializer.getOptimizationInfo().getTrivialInitializerInfo();
          if (info != null
              && ((TrivialClassInitializer) info).field == field
              && !appView.appInfo().isPinned(field)
              && outValue.getTypeLattice().isReference()
              && outValue.canBeNull()) {
            insertAssumeNotNull(code, affectedValues, blocks, iterator, current);
          }
        }
      }
    }
  }

  private void rewriteInstanceGetWithConstantValues(
      IRCode code,
      Set<Value> affectedValues,
      InstructionListIterator iterator,
      InstanceGet current) {
    if (current.object().getTypeLattice().isNullable()) {
      return;
    }

    DexField field = current.getField();

    // TODO(b/123857022): Should be able to use definitionFor().
    DexEncodedField target = appView.appInfo().lookupInstanceTarget(field.holder, field);
    if (target == null || !mayPropagateValueFor(target)) {
      return;
    }

    // Check if a this value is known const.
    ConstInstruction replacement = target.valueAsConstInstruction(code, current.dest(), appView);
    if (replacement != null) {
      affectedValues.add(replacement.outValue());
      iterator.replaceCurrentInstruction(replacement);
      if (replacement.isDexItemBasedConstString()) {
        code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
      }
      target.getMutableOptimizationInfo().markAsPropagated();
    }
  }

  private void insertAssumeNotNull(
      IRCode code,
      Set<Value> affectedValues,
      ListIterator<BasicBlock> blocks,
      InstructionListIterator iterator,
      Instruction current) {
    Value knownToBeNonNullValue = current.outValue();
    Set<Value> affectedUsers = knownToBeNonNullValue.affectedValues();
    TypeLatticeElement typeLattice = knownToBeNonNullValue.getTypeLattice();
    Value nonNullValue =
        code.createValue(
            typeLattice.asReferenceTypeLatticeElement().asNotNull(),
            knownToBeNonNullValue.getLocalInfo());
    knownToBeNonNullValue.replaceUsers(nonNullValue);
    Assume nonNull =
        Assume.createAssumeNonNullInstruction(
            nonNullValue, knownToBeNonNullValue, current, appView);
    nonNull.setPosition(appView.options().debug ? current.getPosition() : Position.none());
    if (current.getBlock().hasCatchHandlers()) {
      iterator.split(code, blocks).listIterator(code).add(nonNull);
    } else {
      iterator.add(nonNull);
    }
    affectedValues.addAll(affectedUsers);
  }

  /**
   * Replace invoke targets and field accesses with constant values where possible.
   *
   * <p>Also assigns value ranges to values where possible.
   */
  public void rewriteWithConstantValues(
      IRCode code, DexType callingContext, Predicate<DexEncodedMethod> isProcessedConcurrently) {
    IRMetadata metadata = code.metadata();
    if (!metadata.mayHaveFieldGet() && !metadata.mayHaveInvokeMethod()) {
      return;
    }

    Set<Value> affectedValues = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        if (current.isInvokeMethod()) {
          rewriteInvokeMethodWithConstantValues(
              code, callingContext, affectedValues, blocks, iterator, current.asInvokeMethod());
        } else if (current.isStaticGet()) {
          rewriteStaticGetWithConstantValues(
              code,
              isProcessedConcurrently,
              affectedValues,
              blocks,
              iterator,
              current.asStaticGet());
        } else if (current.isInstanceGet()) {
          rewriteInstanceGetWithConstantValues(
              code, affectedValues, iterator, current.asInstanceGet());
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }
}
