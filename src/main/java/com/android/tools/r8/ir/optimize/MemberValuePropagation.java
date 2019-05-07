// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.CompilationError;
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
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.google.common.collect.Sets;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

public class MemberValuePropagation {

  private final AppView<AppInfoWithLiveness> appView;

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
  }

  private boolean mayPropagateValueFor(DexEncodedField field) {
    return field.isProgramField(appView)
        ? appView.appInfo().mayPropagateValueFor(field.field)
        : appView.appInfo().assumedValues.containsKey(field.field);
  }

  private boolean mayPropagateValueFor(DexEncodedMethod method) {
    return method.isProgramMethod(appView)
        ? appView.appInfo().mayPropagateValueFor(method.method)
        : appView.appInfo().assumedValues.containsKey(method.method);
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
    // Check if this value can be assumed constant.
    Instruction replacement = null;
    TypeLatticeElement typeLattice = instruction.outValue().getTypeLattice();
    if (rule != null && rule.hasReturnValue() && rule.getReturnValue().isSingleValue()) {
      replacement = createConstNumberReplacement(
          code, rule.getReturnValue().getSingleValue(), typeLattice, instruction.getLocalInfo());
    }
    if (replacement == null
        && rule != null
        && rule.hasReturnValue()
        && rule.getReturnValue().isField()) {
      DexField field = rule.getReturnValue().getField();
      assert typeLattice
          == TypeLatticeElement.fromDexType(field.type, Nullability.maybeNull(), appView);
      DexEncodedField staticField = appView.appInfo().lookupStaticTarget(field.holder, field);
      if (staticField != null) {
        Value value = code.createValue(typeLattice, instruction.getLocalInfo());
        replacement =
            staticField.getStaticValue().asConstInstruction(false, value, appView.options());
        if (replacement.isDexItemBasedConstString()) {
          code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
        }
      } else {
        throw new CompilationError(field.holder.toSourceString() + "." + field.name.toString()
            + " used in assumevalues rule does not exist.");
      }
    }
    return replacement;
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
    affectedValues.add(replacement.outValue());
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
        iterator.split(code, blocks).listIterator().add(replacement);
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
    ProguardMemberRuleLookup lookup = lookupMemberRule(definition);
    if (lookup == null) {
      // Since -assumenosideeffects rules are always applied to all overriding methods, we can
      // simply check the target method, although this may be different from the dynamically
      // targeted method.
      DexEncodedMethod target = appView.definitionFor(invokedMethod);
      lookup = lookupMemberRule(target);
    }
    boolean invokeReplaced = false;
    if (lookup != null) {
      boolean outValueNullOrNotUsed = current.outValue() == null || !current.outValue().isUsed();
      if (lookup.type == RuleType.ASSUME_NO_SIDE_EFFECTS && outValueNullOrNotUsed) {
        // Remove invoke if marked as having no side effects and the return value is not used.
        iterator.removeOrReplaceByDebugLocalRead();
        invokeReplaced = true;
      } else if (!outValueNullOrNotUsed) {
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

      affectedValues.add(replacement.outValue());
      current.outValue().replaceUsers(replacement.outValue());
      current.setOutValue(null);
      replacement.setPosition(current.getPosition());
      current.moveDebugValues(replacement);
      if (current.getBlock().hasCatchHandlers()) {
        iterator.split(code, blocks).listIterator().add(replacement);
      } else {
        iterator.add(replacement);
      }
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
    if (target == null || !mayPropagateValueFor(target)) {
      return;
    }
    // Check if a this value is known const.
    Instruction replacement =
        target.valueAsConstInstruction(appView.appInfo(), current.dest(), appView.options());
    if (replacement != null) {
      affectedValues.add(replacement.outValue());
      iterator.replaceCurrentInstruction(replacement);
      if (replacement.isDexItemBasedConstString()) {
        code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
      }
      return;
    }
    ProguardMemberRuleLookup lookup = lookupMemberRule(target);
    if (lookup != null
        && lookup.type == RuleType.ASSUME_VALUES
        && tryConstantReplacementFromProguard(
            code, affectedValues, blocks, iterator, current, lookup)) {
      return;
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
          && !field.holder.initializationOfParentTypesMayHaveSideEffects(appView.appInfo())) {
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
      iterator.split(code, blocks).listIterator().add(nonNull);
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
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator iterator = block.listIterator();
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
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView, code.method).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }
}
