// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.phis;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.MaterializingInstructionsInfo;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class EffectivelyTrivialPhiOptimization {

  private final AppView<?> appView;
  private final IRCode code;

  public EffectivelyTrivialPhiOptimization(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  public boolean removeEffectivelyTrivialPhis() {
    AffectedValues affectedValues = new AffectedValues();
    Map<BasicBlock, Set<Phi>> phisToRemove = new IdentityHashMap<>();
    for (BasicBlock block : code.getBlocks()) {
      for (Phi phi : block.getPhis()) {
        removeEffectivelyTrivialPhi(phi, affectedValues, phisToRemove);
      }
    }
    if (phisToRemove.isEmpty()) {
      return false;
    }
    phisToRemove.forEach(BasicBlock::removePhis);
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    return true;
  }

  /**
   * Removes this phi and all other phis that contribute to the value of this phi if the phi is
   * effectively trivial, i.e., it always has the same value.
   */
  private void removeEffectivelyTrivialPhi(
      Phi phi, AffectedValues affectedValues, Map<BasicBlock, Set<Phi>> phisToRemove) {
    if (appView.options().debug) {
      return;
    }

    SingleValueOrValue singleValueOrValue = computeEffectivelyTrivialPhiValue(phi);
    if (singleValueOrValue == null) {
      // Not effectively final.
      return;
    }

    // If we didn't find a non-phi operand then all seen phis are effectively unused.
    if (!singleValueOrValue.hasSingleValue() && !singleValueOrValue.hasValue()) {
      for (Phi seenPhi : singleValueOrValue.getPhis()) {
        addPhiToRemove(phisToRemove, seenPhi);
      }
      return;
    }

    // Otherwise all phis can be replaced by the operand value. If we found only a single non-phi
    // operand value then this is guaranteed to dominate all phis. Otherwise we try to materialize
    // the abstract value in a position that dominates all phis.
    Value replacementValue;
    if (singleValueOrValue.hasValue()) {
      replacementValue = singleValueOrValue.getValue();
    } else {
      assert singleValueOrValue.hasSingleValue();
      SingleValue singleValue = singleValueOrValue.getSingleValue();
      assert singleValue.isMaterializableInContext(appView, code.context());
      InstructionListIterator entryBlockIterator = code.entryBlock().listIterator(code);
      entryBlockIterator.positionBeforeNextInstructionThatMatches(i -> !i.isArgument());

      // Insert materializing instruction.
      TypeElement phiType = phi.getType();
      TypeElement materializedValueType = phiType;
      if (singleValue.isSingleBoxedPrimitive()) {
        materializedValueType = singleValue.asSingleBoxedPrimitive().getBoxedPrimitiveType(appView);
      } else if (singleValue.isSingleFieldValue()) {
        SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
        materializedValueType = singleFieldValue.getField().getTypeElement(appView);
      } else if (phiType.isReferenceType() && singleValue.isNull()) {
        materializedValueType = TypeElement.getNull();
      } else {
        assert phiType.isPrimitiveType() || phiType.isNullType() || phiType.isDefinitelyNotNull()
            : singleValue + ": " + phiType;
      }
      Instruction[] materializingInstructions =
          singleValue.createMaterializingInstructions(
              appView,
              code,
              MaterializingInstructionsInfo.create(
                  materializedValueType, null, code.getEntryPosition()));
      entryBlockIterator.addAll(materializingInstructions);
      Instruction replacement = ArrayUtils.last(materializingInstructions);
      replacementValue = replacement.outValue();

      // Insert assume-not-null instruction.
      if (materializedValueType.isReferenceType()
          && materializedValueType.nullability().isMaybeNull()
          && phiType.nullability().isDefinitelyNotNull()) {
        Assume assume =
            Assume.create(
                DynamicType.definitelyNotNull(),
                code.createValue(phiType),
                replacementValue,
                replacement,
                appView,
                code.context());
        assume.setPosition(code.getEntryPosition(), appView.options());
        entryBlockIterator.add(assume);
        replacementValue = assume.outValue();
      }
    }

    // Remove phis.
    for (Phi seenPhi : singleValueOrValue.getPhis()) {
      for (Value operand : seenPhi.getOperands()) {
        operand.removePhiUser(seenPhi);
      }
      addPhiToRemove(phisToRemove, seenPhi);
    }
    // Replace all uses of the phis by the replacement. This is done after detaching the phis from
    // the graph to make sure that we don't add one of the phis as a user of the replacement value.
    for (Phi seenPhi : singleValueOrValue.getPhis()) {
      seenPhi.replaceUsers(replacementValue, affectedValues);
    }
  }

  private SingleValueOrValue computeEffectivelyTrivialPhiValue(Phi phi) {
    Value representativeOperand = null;
    AbstractValue representativeOperandAbstractValue = null;
    boolean foundDifferentOperandValuesWithSameAbstractValue = false;
    WorkList<Phi> worklist = WorkList.newIdentityWorkList(phi);
    while (worklist.hasNext()) {
      Phi currentPhi = worklist.next();
      for (Value operand : currentPhi.getOperands()) {
        if (operand.isPhi()) {
          worklist.addIfNotSeen(operand.asPhi());
        } else {
          if (representativeOperand == null) {
            assert representativeOperandAbstractValue == null;
            representativeOperand = operand;
            representativeOperandAbstractValue = operand.getAbstractValue(appView, code.context());
          } else if (operand == representativeOperand) {
            continue;
          } else if (representativeOperandAbstractValue.isSingleValue()
              && operand
                  .getAbstractValue(appView, code.context())
                  .equals(representativeOperandAbstractValue)) {
            foundDifferentOperandValuesWithSameAbstractValue = true;
            continue;
          } else {
            // Not effectively trivial.
            return null;
          }
        }
      }
    }
    if (representativeOperand == null) {
      // If we didn't find a non-phi operand then all seen phis are effectively unused.
      return new SingleValueOrValue(worklist.getSeenSet());
    }
    if (foundDifferentOperandValuesWithSameAbstractValue) {
      if (representativeOperandAbstractValue.isSingleValue()) {
        return new SingleValueOrValue(
            worklist.getSeenSet(), representativeOperandAbstractValue.asSingleValue());
      }
      // The computed value is not a constant.
      return null;
    }
    return new SingleValueOrValue(worklist.getSeenSet(), representativeOperand);
  }

  private void addPhiToRemove(Map<BasicBlock, Set<Phi>> phisToRemove, Phi phi) {
    phisToRemove.computeIfAbsent(phi.getBlock(), ignoreKey(Sets::newIdentityHashSet)).add(phi);
  }

  private static class SingleValueOrValue {

    private final SingleValue singleValue;
    private final Value value;
    private final Set<Phi> phis;

    SingleValueOrValue(Set<Phi> phis) {
      this(phis, null, null);
    }

    SingleValueOrValue(Set<Phi> phis, SingleValue singleValue) {
      this(phis, singleValue, null);
    }

    SingleValueOrValue(Set<Phi> phis, Value value) {
      this(phis, null, value);
    }

    SingleValueOrValue(Set<Phi> phis, SingleValue singleValue, Value value) {
      this.singleValue = singleValue;
      this.value = value;
      this.phis = phis;
    }

    boolean hasSingleValue() {
      return singleValue != null;
    }

    SingleValue getSingleValue() {
      return singleValue;
    }

    Set<Phi> getPhis() {
      return phis;
    }

    boolean hasValue() {
      return value != null;
    }

    Value getValue() {
      return value;
    }
  }
}
