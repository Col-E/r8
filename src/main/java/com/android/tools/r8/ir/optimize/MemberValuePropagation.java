// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.TrivialClassInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
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
      Value value = code.createValue(typeLattice, instruction.getLocalInfo());
      assert !typeLattice.isReference() || rule.getReturnValue().isNull();
      replacement = new ConstNumber(value, rule.getReturnValue().getSingleValue());
    }
    if (replacement == null &&
        rule != null && rule.hasReturnValue() && rule.getReturnValue().isField()) {
      DexField field = rule.getReturnValue().getField();
      assert TypeLatticeElement.fromDexType(field.type, true, appInfo) == typeLattice;
      DexEncodedField staticField = appInfo.lookupStaticTarget(field.clazz, field);
      if (staticField != null) {
        Value value = code.createValue(typeLattice, instruction.getLocalInfo());
        replacement = staticField.getStaticValue().asConstInstruction(false, value);
      } else {
        throw new CompilationError(field.clazz.toSourceString() + "." + field.name.toString() +
            " used in assumevalues rule does not exist.");
      }
    }
    return replacement;
  }

  private void setValueRangeFromProguardRule(ProguardMemberRule rule, Value value) {
    if (rule.hasReturnValue() && rule.getReturnValue().isValueRange()) {
      assert !rule.getReturnValue().isSingleValue();
      value.setValueRange(rule.getReturnValue().getValueRange());
    }
  }

  private void replaceInstructionFromProguardRule(RuleType ruleType, InstructionIterator iterator,
      Instruction current, Instruction replacement) {
    if (ruleType == RuleType.ASSUME_NO_SIDE_EFFECTS) {
      iterator.replaceCurrentInstruction(replacement);
    } else {
      if (current.outValue() != null) {
        assert replacement.outValue() != null;
        current.outValue().replaceUsers(replacement.outValue());
      }
      replacement.setPosition(current.getPosition());
      iterator.add(replacement);
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
        InvokeMethod invoke = current.asInvokeMethod();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        DexType invokedHolder = invokedMethod.getHolder();
        if (!invokedHolder.isClassType()) {
          continue;
        }
        // TODO(70550443): Maybe check all methods here.
        DexEncodedMethod definition = appInfo
            .lookup(invoke.getType(), invokedMethod, callingContext);

        boolean invokeReplaced = false;
        ProguardMemberRuleLookup lookup = lookupMemberRule(definition);
        if (lookup != null) {
          if (lookup.type == RuleType.ASSUME_NO_SIDE_EFFECTS
              && (invoke.outValue() == null || !invoke.outValue().isUsed())) {
            // Remove invoke if marked as having no side effects and the return value is not used.
            iterator.remove();
            invokeReplaced = true;
          } else if (invoke.outValue() != null && invoke.outValue().isUsed()) {
            // Check to see if a constant value can be assumed.
            Instruction replacement =
                constantReplacementFromProguardRule(lookup.rule, code, invoke);
            if (replacement != null) {
              affectedValues.add(replacement.outValue());
              replaceInstructionFromProguardRule(lookup.type, iterator, current, replacement);
              invokeReplaced = true;
            } else {
              // Check to see if a value range can be assumed.
              setValueRangeFromProguardRule(lookup.rule, current.outValue());
            }
          }
        }

        // If no Proguard rule could replace the instruction check for knowledge about the
        // return value.
        if (!invokeReplaced && invoke.outValue() != null) {
          DexEncodedMethod target = invoke.lookupSingleTarget(appInfo, callingContext);
          if (target != null) {
            if (target.getOptimizationInfo().neverReturnsNull() && invoke.outValue().canBeNull()) {
              invoke.outValue().markNeverNull();
            }
            if (target.getOptimizationInfo().returnsConstant()) {
              long constant = target.getOptimizationInfo().getReturnedConstant();
              Value value = code.createValue(
                  invoke.outValue().getTypeLattice(), invoke.getLocalInfo());
              Instruction knownConstReturn = new ConstNumber(value, constant);
              affectedValues.add(value);
              invoke.outValue().replaceUsers(value);
              invoke.setOutValue(null);
              knownConstReturn.setPosition(invoke.getPosition());
              invoke.moveDebugValues(knownConstReturn);
              iterator.add(knownConstReturn);
            }
          }
        }
      } else if (current.isInstancePut()) {
        InstancePut instancePut = current.asInstancePut();
        DexField field = instancePut.getField();
        DexEncodedField target = appInfo.lookupInstanceTarget(field.getHolder(), field);
        if (target != null) {
          // Remove writes to dead (i.e. never read) fields.
          if (!isFieldRead(target, false) && instancePut.object().isNeverNull()) {
            iterator.remove();
          }
        }
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        DexField field = staticGet.getField();
        DexEncodedField target = appInfo.lookupStaticTarget(field.getHolder(), field);
        ProguardMemberRuleLookup lookup = null;
        if (target != null) {
          // Check if a this value is known const.
          Instruction replacement = target.valueAsConstInstruction(appInfo, staticGet.dest());
          if (replacement == null) {
            lookup = lookupMemberRule(target);
            if (lookup != null) {
              replacement = constantReplacementFromProguardRule(lookup.rule, code, staticGet);
            }
          }
          if (replacement == null) {
            // If no const replacement was found, at least store the range information.
            if (lookup != null) {
              setValueRangeFromProguardRule(lookup.rule, staticGet.dest());
            }
          }
          if (replacement != null) {
            affectedValues.add(replacement.outValue());
            // Ignore assumenosideeffects for fields.
            if (lookup != null && lookup.type == RuleType.ASSUME_VALUES) {
              replaceInstructionFromProguardRule(lookup.type, iterator, current, replacement);
            } else {
              iterator.replaceCurrentInstruction(replacement);
            }
          } else if (staticGet.dest() != null) {
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
            //
            DexClass holderDefinition = appInfo.definitionFor(field.getHolder());
            if (holderDefinition != null &&
                holderDefinition.accessFlags.isFinal() &&
                !appInfo.canTriggerStaticInitializer(field.getHolder(), true)) {

              Value outValue = staticGet.dest();
              DexEncodedMethod classInitializer = holderDefinition.getClassInitializer();
              if (classInitializer != null && !isProcessedConcurrently.test(classInitializer)) {
                TrivialInitializer info =
                    classInitializer.getOptimizationInfo().getTrivialInitializerInfo();
                if (info != null &&
                    ((TrivialClassInitializer) info).field == field &&
                    !appInfo.isPinned(field) &&
                    outValue.canBeNull()) {
                  outValue.markNeverNull();
                }
              }
            }
          }
        }
      } else if (current.isStaticPut()) {
        StaticPut staticPut = current.asStaticPut();
        DexField field = staticPut.getField();
        DexEncodedField target = appInfo.lookupStaticTarget(field.getHolder(), field);
        if (target != null) {
          // Remove writes to dead (i.e. never read) fields.
          if (!isFieldRead(target, true)) {
            iterator.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appInfo, code.method).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  private boolean isFieldRead(DexEncodedField field, boolean isStatic) {
    if (appInfo.fieldsRead.contains(field.field)
        || appInfo.isPinned(field.field)) {
      return true;
    }
    // For library classes we don't know whether a field is read.
    DexClass holder = appInfo.definitionFor(field.field.clazz);
    return holder == null || holder.isLibraryClass();
  }
}
