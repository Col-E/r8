// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.TrivialClassInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Predicate;

public class MemberValuePropagation {

  private final AppInfoWithLiveness appInfo;

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
  }

  public MemberValuePropagation(AppInfoWithLiveness appInfo) {
    this.appInfo = appInfo;
  }

  private ProguardMemberRuleLookup lookupMemberRule(DexItem item) {
    ProguardMemberRule rule = appInfo.noSideEffects.get(item);
    if (rule != null) {
      return new ProguardMemberRuleLookup(RuleType.ASSUME_NO_SIDE_EFFECTS, rule);
    }
    rule = appInfo.assumedValues.get(item);
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
    if (replacement == null && rule != null
        && rule.hasReturnValue() && rule.getReturnValue().isField()) {
      DexField field = rule.getReturnValue().getField();
      assert typeLattice
          == TypeLatticeElement.fromDexType(field.type, Nullability.maybeNull(), appInfo);
      DexEncodedField staticField = appInfo.lookupStaticTarget(field.clazz, field);
      if (staticField != null) {
        Value value = code.createValue(typeLattice, instruction.getLocalInfo());
        replacement = staticField.getStaticValue().asConstInstruction(false, value);
      } else {
        throw new CompilationError(field.clazz.toSourceString() + "." + field.name.toString()
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

  private void setValueRangeFromProguardRule(ProguardMemberRule rule, Value value) {
    if (rule.hasReturnValue() && rule.getReturnValue().isValueRange()) {
      assert !rule.getReturnValue().isSingleValue();
      value.setValueRange(rule.getReturnValue().getValueRange());
    }
  }

  private boolean tryConstantReplacementFromProguard(
      IRCode code,
      Set<Value> affectedValues,
      InstructionIterator iterator,
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
      iterator.add(replacement);
    }
    return true;
  }

  private void rewriteInvokeMethodWithConstantValues(
      IRCode code,
      DexType callingContext,
      Set<Value> affectedValues,
      InstructionIterator iterator,
      InvokeMethod current) {
    DexMethod invokedMethod = current.getInvokedMethod();
    DexType invokedHolder = invokedMethod.getHolder();
    if (!invokedHolder.isClassType()) {
      return;
    }
    // TODO(70550443): Maybe check all methods here.
    DexEncodedMethod definition = appInfo.lookup(current.getType(), invokedMethod, callingContext);
    ProguardMemberRuleLookup lookup = lookupMemberRule(definition);
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
            tryConstantReplacementFromProguard(code, affectedValues, iterator, current, lookup);
      }
    }
    if (invokeReplaced || current.outValue() == null) {
      return;
    }
    // No Proguard rule could replace the instruction check for knowledge about the return value.
    DexEncodedMethod target = current.lookupSingleTarget(appInfo, callingContext);
    if (target == null) {
      return;
    }
    if (target.getOptimizationInfo().neverReturnsNull() && current.outValue().canBeNull()) {
      Value knownToBeNonNullValue = current.outValue();
      knownToBeNonNullValue.markNeverNull();
      TypeLatticeElement typeLattice = knownToBeNonNullValue.getTypeLattice();
      assert typeLattice.isNullable() && typeLattice.isReference();
      knownToBeNonNullValue.narrowing(appInfo, typeLattice.asNonNullable());
      affectedValues.addAll(knownToBeNonNullValue.affectedValues());
    }
    if (target.getOptimizationInfo().returnsConstant()) {
      long constant = target.getOptimizationInfo().getReturnedConstant();
      ConstNumber replacement =
          createConstNumberReplacement(
              code, constant, current.outValue().getTypeLattice(), current.getLocalInfo());
      affectedValues.add(replacement.outValue());
      current.outValue().replaceUsers(replacement.outValue());
      current.setOutValue(null);
      replacement.setPosition(current.getPosition());
      current.moveDebugValues(replacement);
      iterator.add(replacement);
    }
  }

  private void rewriteStaticGetWithConstantValues(
      IRCode code,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      Set<Value> affectedValues,
      InstructionIterator iterator,
      StaticGet current) {
    DexField field = current.getField();
    DexEncodedField target = appInfo.lookupStaticTarget(field.getHolder(), field);
    if (target == null) {
      return;
    }
    // Check if a this value is known const.
    Instruction replacement = target.valueAsConstInstruction(appInfo, current.dest());
    if (replacement != null) {
      affectedValues.add(replacement.outValue());
      iterator.replaceCurrentInstruction(replacement);
      return;
    }
    ProguardMemberRuleLookup lookup = lookupMemberRule(target);
    if (lookup != null
        && lookup.type == RuleType.ASSUME_VALUES
        && tryConstantReplacementFromProguard(code, affectedValues, iterator, current, lookup)) {
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
      DexClass holderDefinition = appInfo.definitionFor(field.getHolder());
      if (holderDefinition != null
          && holderDefinition.accessFlags.isFinal()
          && !appInfo.canTriggerStaticInitializer(field.getHolder(), true)) {
        Value outValue = current.dest();
        DexEncodedMethod classInitializer = holderDefinition.getClassInitializer();
        if (classInitializer != null && !isProcessedConcurrently.test(classInitializer)) {
          TrivialInitializer info =
              classInitializer.getOptimizationInfo().getTrivialInitializerInfo();
          if (info != null
              && ((TrivialClassInitializer) info).field == field
              && !appInfo.isPinned(field)
              && outValue.canBeNull()) {
            outValue.markNeverNull();
            TypeLatticeElement typeLattice = outValue.getTypeLattice();
            assert typeLattice.isNullable() && typeLattice.isReference();
            outValue.narrowing(appInfo, typeLattice.asNonNullable());
            affectedValues.addAll(outValue.affectedValues());
          }
        }
      }
    }
  }

  private void rewritePutWithConstantValues(
      InstructionIterator iterator, FieldInstruction current) {
    DexField field = current.asFieldInstruction().getField();
    DexEncodedField target =
        current.isInstancePut()
            ? appInfo.lookupInstanceTarget(field.getHolder(), field)
            : appInfo.lookupStaticTarget(field.getHolder(), field);
    if (target != null && !isFieldRead(target)) {
      // Remove writes to dead (i.e. never read) fields.
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }

  /**
   * Replace invoke targets and field accesses with constant values where possible.
   * <p>
   * Also assigns value ranges to values where possible.
   */
  public void rewriteWithConstantValues(
      IRCode code, DexType callingContext, Predicate<DexEncodedMethod> isProcessedConcurrently) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isInvokeMethod()) {
        rewriteInvokeMethodWithConstantValues(
            code, callingContext, affectedValues, iterator, current.asInvokeMethod());
      } else if (current.isInstancePut() || current.isStaticPut()) {
        rewritePutWithConstantValues(iterator, current.asFieldInstruction());
      } else if (current.isStaticGet()) {
        rewriteStaticGetWithConstantValues(
            code, isProcessedConcurrently, affectedValues, iterator, current.asStaticGet());
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appInfo, code.method).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  private boolean isFieldRead(DexEncodedField field) {
    if (appInfo.fieldsRead.contains(field.field) || appInfo.isPinned(field.field)) {
      return true;
    }
    // For library classes we don't know whether a field is read.
    DexClass holder = appInfo.definitionFor(field.field.clazz);
    return holder == null || holder.isLibraryClass();
  }
}
