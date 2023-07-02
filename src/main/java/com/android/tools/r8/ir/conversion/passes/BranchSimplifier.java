// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.conversion.passes.BranchSimplifier.ControlFlowSimplificationResult.NO_CHANGE;
import static com.android.tools.r8.ir.conversion.passes.BranchSimplifier.ControlFlowSimplificationResult.create;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.FieldResolutionResult.SingleProgramFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerUtils;
import com.android.tools.r8.ir.analysis.equivalence.BasicBlockBehavioralSubsumption;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.ConstantOrNonConstantNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleConstClassValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.controlflow.SwitchCaseAnalyzer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOutputMode;
import com.android.tools.r8.utils.LongInterval;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Set;

public class BranchSimplifier extends CodeRewriterPass<AppInfo> {

  public BranchSimplifier(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "BranchSimplifier";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  @Override
  protected CodeRewriterResult noChange() {
    return NO_CHANGE;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    ControlFlowSimplificationResult switchResult = rewriteSwitch(code);
    ControlFlowSimplificationResult ifResult = simplifyIf(code);
    return switchResult.combine(ifResult);
  }

  public ControlFlowSimplificationResult simplifyIf(IRCode code) {
    BasicBlockBehavioralSubsumption behavioralSubsumption =
        new BasicBlockBehavioralSubsumption(appView, code);
    boolean simplified = false;
    for (BasicBlock block : code.blocks) {
      // Skip removed (= unreachable) blocks.
      if (block.getNumber() != 0 && block.getPredecessors().isEmpty()) {
        continue;
      }
      if (block.exit().isIf()) {
        flipIfBranchesIfNeeded(code, block);
        if (rewriteIfWithConstZero(code, block)) {
          simplified = true;
        }

        if (simplifyKnownBooleanCondition(code, block)) {
          simplified = true;
          if (!block.exit().isIf()) {
            continue;
          }
        }

        // Simplify if conditions when possible.
        If theIf = block.exit().asIf();
        if (theIf.isZeroTest()) {
          if (simplifyIfZeroTest(code, block, theIf)) {
            simplified = true;
            continue;
          }
        } else {
          if (simplifyNonIfZeroTest(code, block, theIf)) {
            simplified = true;
            continue;
          }
        }

        // Unable to determine which branch will be taken. Check if the true target can safely be
        // rewritten to the false target.
        if (behavioralSubsumption.isSubsumedBy(
            theIf.inValues().get(0), theIf.getTrueTarget(), theIf.fallthroughBlock())) {
          simplifyIfWithKnownCondition(code, block, theIf, theIf.fallthroughBlock());
          simplified = true;
        }
      }
    }
    Set<Value> affectedValues = code.removeUnreachableBlocks();
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
    return create(!affectedValues.isEmpty(), simplified);
  }

  public static class ControlFlowSimplificationResult implements CodeRewriterResult {

    static ControlFlowSimplificationResult create(
        boolean anyAffectedValues, boolean anySimplifications) {
      if (anyAffectedValues) {
        assert anySimplifications;
        return ALL_CHANGED;
      }
      return anySimplifications ? ONLY_SIMPLIFICATIONS : NO_CHANGE;
    }

    static final ControlFlowSimplificationResult ALL_CHANGED =
        new ControlFlowSimplificationResult(true, true);
    static final ControlFlowSimplificationResult ONLY_SIMPLIFICATIONS =
        new ControlFlowSimplificationResult(false, true);
    static final ControlFlowSimplificationResult NO_CHANGE =
        new ControlFlowSimplificationResult(false, false);

    private final boolean anyAffectedValues;
    private final boolean anySimplifications;

    private ControlFlowSimplificationResult(boolean anyAffectedValues, boolean anySimplifications) {
      assert !anyAffectedValues || anySimplifications;
      this.anyAffectedValues = anyAffectedValues;
      this.anySimplifications = anySimplifications;
    }

    @Override
    public ControlFlowSimplificationResult asControlFlowSimplificationResult() {
      return this;
    }

    public boolean anyAffectedValues() {
      return anyAffectedValues;
    }

    public boolean anySimplifications() {
      return anySimplifications;
    }

    @Override
    public boolean hasChanged() {
      assert !anyAffectedValues || anySimplifications;
      return anySimplifications();
    }

    public ControlFlowSimplificationResult combine(ControlFlowSimplificationResult ifResult) {
      return create(
          anyAffectedValues || ifResult.anyAffectedValues,
          anySimplifications || ifResult.anySimplifications);
    }
  }

  private boolean simplifyIfZeroTest(IRCode code, BasicBlock block, If theIf) {
    Value lhs = theIf.lhs();
    Value lhsRoot = lhs.getAliasedValue();
    if (lhsRoot.isConstNumber()) {
      ConstNumber cond = lhsRoot.getConstInstruction().asConstNumber();
      BasicBlock target = theIf.targetFromCondition(cond);
      simplifyIfWithKnownCondition(code, block, theIf, target);
      return true;
    }

    if (theIf.isNullTest()) {
      assert theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE;

      if (lhs.isAlwaysNull(appView)) {
        simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromNullObject());
        return true;
      }

      if (lhs.isNeverNull()) {
        simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromNonNullObject());
        return true;
      }
    }

    if (theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE) {
      AbstractValue lhsAbstractValue = lhs.getAbstractValue(appView, code.context());
      if (lhsAbstractValue.isConstantOrNonConstantNumberValue()
          && !lhsAbstractValue.asConstantOrNonConstantNumberValue().containsInt(0)) {
        // Value doesn't contain zero at all.
        simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromCondition(1));
        return true;
      }
    }

    if (lhs.hasValueRange()) {
      LongInterval interval = lhs.getValueRange();
      if (!interval.containsValue(0)) {
        // Interval doesn't contain zero at all.
        int sign = Long.signum(interval.getMin());
        simplifyIfWithKnownCondition(code, block, theIf, sign);
        return true;
      }

      // Interval contains zero.
      switch (theIf.getType()) {
        case GE:
        case LT:
          // [a, b] >= 0 is always true if a >= 0.
          // [a, b] < 0 is always false if a >= 0.
          // In both cases a zero condition takes the right branch.
          if (interval.getMin() == 0) {
            simplifyIfWithKnownCondition(code, block, theIf, 0);
            return true;
          }
          break;

        case LE:
        case GT:
          // [a, b] <= 0 is always true if b <= 0.
          // [a, b] > 0 is always false if b <= 0.
          // In both cases a zero condition takes the right branch.
          if (interval.getMax() == 0) {
            simplifyIfWithKnownCondition(code, block, theIf, 0);
            return true;
          }
          break;

        case EQ:
        case NE:
          // Only a single element interval [0, 0] can be dealt with here.
          // Such intervals should have been replaced by constants.
          assert !interval.isSingleValue();
          break;
      }
    }
    return false;
  }

  private boolean simplifyNonIfZeroTest(IRCode code, BasicBlock block, If theIf) {
    Value lhs = theIf.lhs();
    Value lhsRoot = lhs.getAliasedValue();
    Value rhs = theIf.rhs();
    Value rhsRoot = rhs.getAliasedValue();
    if (lhsRoot == rhsRoot) {
      // Comparing the same value.
      simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromCondition(0));
      return true;
    }

    if (lhsRoot.isDefinedByInstructionSatisfying(Instruction::isCreatingInstanceOrArray)
        && rhsRoot.isDefinedByInstructionSatisfying(Instruction::isCreatingInstanceOrArray)) {
      // Comparing two newly created objects.
      assert theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE;
      simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromCondition(1));
      return true;
    }

    if (lhsRoot.isConstNumber() && rhsRoot.isConstNumber()) {
      // Zero test with a constant of comparison between between two constants.
      ConstNumber left = lhsRoot.getConstInstruction().asConstNumber();
      ConstNumber right = rhsRoot.getConstInstruction().asConstNumber();
      BasicBlock target = theIf.targetFromCondition(left, right);
      simplifyIfWithKnownCondition(code, block, theIf, target);
      return true;
    }

    if (theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE) {
      AbstractValue lhsAbstractValue = lhs.getAbstractValue(appView, code.context());
      AbstractValue rhsAbstractValue = rhs.getAbstractValue(appView, code.context());
      if (lhsAbstractValue.isConstantOrNonConstantNumberValue()
          && rhsAbstractValue.isConstantOrNonConstantNumberValue()) {
        ConstantOrNonConstantNumberValue lhsNumberValue =
            lhsAbstractValue.asConstantOrNonConstantNumberValue();
        ConstantOrNonConstantNumberValue rhsNumberValue =
            rhsAbstractValue.asConstantOrNonConstantNumberValue();
        if (!lhsNumberValue.mayOverlapWith(rhsNumberValue)) {
          // No overlap.
          simplifyIfWithKnownCondition(code, block, theIf, 1);
          return true;
        }
      }
    }

    if (lhs.hasValueRange() && rhs.hasValueRange()) {
      // Zero test with a value range, or comparison between between two values,
      // each with a value ranges.
      LongInterval leftRange = lhs.getValueRange();
      LongInterval rightRange = rhs.getValueRange();
      // Two overlapping ranges. Check for single point overlap.
      if (!leftRange.overlapsWith(rightRange)) {
        // No overlap.
        int cond = Long.signum(leftRange.getMin() - rightRange.getMin());
        simplifyIfWithKnownCondition(code, block, theIf, cond);
        return true;
      }

      // The two intervals overlap. We can simplify if they overlap at the end points.
      switch (theIf.getType()) {
        case LT:
        case GE:
          // [a, b] < [c, d] is always false when a == d.
          // [a, b] >= [c, d] is always true when a == d.
          // In both cases 0 condition will choose the right branch.
          if (leftRange.getMin() == rightRange.getMax()) {
            simplifyIfWithKnownCondition(code, block, theIf, 0);
            return true;
          }
          break;
        case GT:
        case LE:
          // [a, b] > [c, d] is always false when b == c.
          // [a, b] <= [c, d] is always true when b == c.
          // In both cases 0 condition will choose the right branch.
          if (leftRange.getMax() == rightRange.getMin()) {
            simplifyIfWithKnownCondition(code, block, theIf, 0);
            return true;
          }
          break;
        case EQ:
        case NE:
          // Since there is overlap EQ and NE cannot be determined.
          break;
      }
    }

    if (theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE) {
      ProgramMethod context = code.context();
      AbstractValue abstractValue = lhs.getAbstractValue(appView, context);
      if (abstractValue.isSingleConstClassValue()) {
        AbstractValue otherAbstractValue = rhs.getAbstractValue(appView, context);
        if (otherAbstractValue.isSingleConstClassValue()) {
          SingleConstClassValue singleConstClassValue = abstractValue.asSingleConstClassValue();
          SingleConstClassValue otherSingleConstClassValue =
              otherAbstractValue.asSingleConstClassValue();
          simplifyIfWithKnownCondition(
              code,
              block,
              theIf,
              BooleanUtils.intValue(
                  singleConstClassValue.getType() != otherSingleConstClassValue.getType()));
          return true;
        }
        return false;
      }

      if (abstractValue.isSingleFieldValue()) {
        AbstractValue otherAbstractValue = rhs.getAbstractValue(appView, context);
        if (otherAbstractValue.isSingleFieldValue()) {
          SingleFieldValue singleFieldValue = abstractValue.asSingleFieldValue();
          SingleFieldValue otherSingleFieldValue = otherAbstractValue.asSingleFieldValue();
          if (singleFieldValue.getField() == otherSingleFieldValue.getField()) {
            simplifyIfWithKnownCondition(code, block, theIf, 0);
            return true;
          }

          DexClass holder = appView.definitionForHolder(singleFieldValue.getField());
          DexEncodedField field = singleFieldValue.getField().lookupOnClass(holder);
          if (field != null && field.isEnum()) {
            DexClass otherHolder = appView.definitionForHolder(otherSingleFieldValue.getField());
            DexEncodedField otherField =
                otherSingleFieldValue.getField().lookupOnClass(otherHolder);
            if (otherField != null && otherField.isEnum()) {
              simplifyIfWithKnownCondition(code, block, theIf, 1);
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private void simplifyIfWithKnownCondition(
      IRCode code, BasicBlock block, If theIf, BasicBlock target) {
    BasicBlock deadTarget =
        target == theIf.getTrueTarget() ? theIf.fallthroughBlock() : theIf.getTrueTarget();
    rewriteIfToGoto(code, block, theIf, target, deadTarget);
  }

  private void simplifyIfWithKnownCondition(IRCode code, BasicBlock block, If theIf, int cond) {
    simplifyIfWithKnownCondition(code, block, theIf, theIf.targetFromCondition(cond));
  }

  /* Identify simple diamond shapes converting boolean true/false to 1/0. We consider the forms:
   *
   * (1)
   *
   *      [dbg pos x]             [dbg pos x]
   *   ifeqz booleanValue       ifnez booleanValue
   *      /        \              /        \
   * [dbg pos x][dbg pos x]  [dbg pos x][dbg pos x]
   *  [const 0]  [const 1]    [const 1]  [const 0]
   *    goto      goto          goto      goto
   *      \        /              \        /
   *      phi(0, 1)                phi(1, 0)
   *
   * which can be replaced by a fallthrough and the phi value can be replaced
   * with the boolean value itself.
   *
   * (2)
   *
   *      [dbg pos x]              [dbg pos x]
   *    ifeqz booleanValue       ifnez booleanValue
   *      /        \              /        \
   * [dbg pos x][dbg pos x]  [dbg pos x][dbg pos x]
   *  [const 1]  [const 0]   [const 0]  [const 1]
   *    goto      goto          goto      goto
   *      \        /              \        /
   *      phi(1, 0)                phi(0, 1)
   *
   * which can be replaced by a fallthrough and the phi value can be replaced
   * by an xor instruction which is smaller.
   */
  private boolean simplifyKnownBooleanCondition(IRCode code, BasicBlock block) {
    If theIf = block.exit().asIf();
    Value testValue = theIf.inValues().get(0);
    if (theIf.isZeroTest() && testValue.knownToBeBoolean()) {
      BasicBlock trueBlock = theIf.getTrueTarget();
      BasicBlock falseBlock = theIf.fallthroughBlock();
      if (isBlockSupportedBySimplifyKnownBooleanCondition(trueBlock)
          && isBlockSupportedBySimplifyKnownBooleanCondition(falseBlock)
          && trueBlock.getSuccessors().get(0) == falseBlock.getSuccessors().get(0)) {
        BasicBlock targetBlock = trueBlock.getSuccessors().get(0);
        if (targetBlock.getPredecessors().size() == 2) {
          int trueIndex = targetBlock.getPredecessors().indexOf(trueBlock);
          int falseIndex = trueIndex == 0 ? 1 : 0;
          int deadPhis = 0;
          // Locate the phis that have the same value as the boolean and replace them
          // by the boolean in all users.
          for (Phi phi : targetBlock.getPhis()) {
            Value trueValue = phi.getOperand(trueIndex);
            Value falseValue = phi.getOperand(falseIndex);
            if (trueValue.isConstNumber() && falseValue.isConstNumber()) {
              ConstNumber trueNumber = trueValue.getConstInstruction().asConstNumber();
              ConstNumber falseNumber = falseValue.getConstInstruction().asConstNumber();
              if ((theIf.getType() == IfType.EQ
                      && trueNumber.isIntegerZero()
                      && falseNumber.isIntegerOne())
                  || (theIf.getType() == IfType.NE
                      && trueNumber.isIntegerOne()
                      && falseNumber.isIntegerZero())) {
                phi.replaceUsers(testValue);
                deadPhis++;
              } else if ((theIf.getType() == IfType.NE
                      && trueNumber.isIntegerZero()
                      && falseNumber.isIntegerOne())
                  || (theIf.getType() == IfType.EQ
                      && trueNumber.isIntegerOne()
                      && falseNumber.isIntegerZero())) {
                Value newOutValue = code.createValue(phi.getType(), phi.getLocalInfo());
                ConstNumber cstToUse = trueNumber.isIntegerOne() ? trueNumber : falseNumber;
                BasicBlock phiBlock = phi.getBlock();
                Position phiPosition = phiBlock.getPosition();
                int insertIndex = 0;
                if (cstToUse.getBlock() == trueBlock || cstToUse.getBlock() == falseBlock) {
                  // The constant belongs to the block to remove, create a new one.
                  cstToUse = ConstNumber.copyOf(code, cstToUse);
                  cstToUse.setBlock(phiBlock);
                  cstToUse.setPosition(phiPosition);
                  phiBlock.getInstructions().add(insertIndex++, cstToUse);
                }
                phi.replaceUsers(newOutValue);
                Instruction newInstruction =
                    Xor.create(NumericType.INT, newOutValue, testValue, cstToUse.outValue());
                newInstruction.setBlock(phiBlock);
                // The xor is replacing a phi so it does not have an actual position.
                newInstruction.setPosition(phiPosition);
                phiBlock.listIterator(code, insertIndex).add(newInstruction);
                deadPhis++;
              }
            }
          }
          // If all phis were removed, there is no need for the diamond shape anymore
          // and it can be rewritten to a goto to one of the branches.
          if (deadPhis == targetBlock.getPhis().size()) {
            rewriteIfToGoto(code, block, theIf, trueBlock, falseBlock);
            return true;
          }
          return deadPhis > 0;
        }
      }
    }
    return false;
  }

  private boolean isBlockSupportedBySimplifyKnownBooleanCondition(BasicBlock b) {
    if (b.isTrivialGoto()) {
      return true;
    }

    int instructionSize = b.getInstructions().size();
    if (b.exit().isGoto() && (instructionSize == 2 || instructionSize == 3)) {
      Instruction constInstruction = b.getInstructions().get(instructionSize - 2);
      if (constInstruction.isConstNumber()) {
        if (!constInstruction.asConstNumber().isIntegerOne()
            && !constInstruction.asConstNumber().isIntegerZero()) {
          return false;
        }
        if (instructionSize == 2) {
          return true;
        }
        Instruction firstInstruction = b.getInstructions().getFirst();
        if (firstInstruction.isDebugPosition()) {
          assert b.getPredecessors().size() == 1;
          BasicBlock predecessorBlock = b.getPredecessors().get(0);
          InstructionIterator it = predecessorBlock.iterator(predecessorBlock.exit());
          Instruction previousPosition = null;
          while (it.hasPrevious() && !(previousPosition = it.previous()).isDebugPosition()) {
            // Intentionally empty.
          }
          if (previousPosition != null) {
            return previousPosition.getPosition() == firstInstruction.getPosition();
          }
        }
      }
    }

    return false;
  }

  private void rewriteIfToGoto(
      IRCode code, BasicBlock block, If theIf, BasicBlock target, BasicBlock deadTarget) {
    deadTarget.unlinkSinglePredecessorSiblingsAllowed();
    assert theIf == block.exit();
    block.replaceLastInstruction(new Goto(), code);
    assert block.exit().isGoto();
    assert block.exit().asGoto().getTarget() == target;
  }

  private boolean rewriteIfWithConstZero(IRCode code, BasicBlock block) {
    If theIf = block.exit().asIf();
    if (theIf.isZeroTest()) {
      return false;
    }

    Value leftValue = theIf.lhs();
    Value rightValue = theIf.rhs();
    if (leftValue.isConstNumber() || rightValue.isConstNumber()) {
      if (leftValue.isConstNumber()) {
        if (leftValue.getConstInstruction().asConstNumber().isZero()) {
          If ifz = new If(theIf.getType().forSwappedOperands(), rightValue);
          block.replaceLastInstruction(ifz, code);
          assert block.exit() == ifz;
          return true;
        }
      } else if (rightValue.getConstInstruction().asConstNumber().isZero()) {
        If ifz = new If(theIf.getType(), leftValue);
        block.replaceLastInstruction(ifz, code);
        assert block.exit() == ifz;
        return true;
      }
    }

    return false;
  }

  private boolean flipIfBranchesIfNeeded(IRCode code, BasicBlock block) {
    If theIf = block.exit().asIf();
    BasicBlock trueTarget = theIf.getTrueTarget();
    BasicBlock fallthrough = theIf.fallthroughBlock();
    assert trueTarget != fallthrough;

    if (!fallthrough.isSimpleAlwaysThrowingPath() || trueTarget.isSimpleAlwaysThrowingPath()) {
      return false;
    }

    // In case fall-through block always throws there is a good chance that it
    // is created for error checks and 'trueTarget' represents most more common
    // non-error case. Flipping the if in this case may result in faster code
    // on older Android versions.
    List<Value> inValues = theIf.inValues();
    If newIf = new If(theIf.getType().inverted(), inValues);
    block.replaceLastInstruction(newIf, code);
    block.swapSuccessors(trueTarget, fallthrough);
    return true;
  }

  private ControlFlowSimplificationResult rewriteSwitch(IRCode code) {
    return rewriteSwitch(code, SwitchCaseAnalyzer.getInstance());
  }

  private ControlFlowSimplificationResult rewriteSwitch(
      IRCode code, SwitchCaseAnalyzer switchCaseAnalyzer) {
    if (!options.isSwitchRewritingEnabled()) {
      return NO_CHANGE;
    }
    if (!code.metadata().mayHaveSwitch()) {
      return NO_CHANGE;
    }
    return rewriteSwitchFull(code, switchCaseAnalyzer);
  }

  private ControlFlowSimplificationResult rewriteSwitchFull(
      IRCode code, SwitchCaseAnalyzer switchCaseAnalyzer) {
    boolean needToRemoveUnreachableBlocks = false;
    boolean anySimplifications = false;
    ListIterator<BasicBlock> blocksIterator = code.listIterator();
    while (blocksIterator.hasNext()) {
      BasicBlock block = blocksIterator.next();
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instruction.isSwitch()) {
          Switch theSwitch = instruction.asSwitch();
          if (options.testing.enableDeadSwitchCaseElimination) {
            SwitchCaseEliminator eliminator =
                removeUnnecessarySwitchCases(code, theSwitch, iterator, switchCaseAnalyzer);
            anySimplifications |= eliminator.canBeOptimized();
            if (eliminator.mayHaveIntroducedUnreachableBlocks()) {
              needToRemoveUnreachableBlocks = true;
            }

            iterator.previous();
            instruction = iterator.next();
            if (instruction.isGoto()) {
              continue;
            }

            assert instruction.isSwitch();
            theSwitch = instruction.asSwitch();
          }
          if (theSwitch.isIntSwitch()) {
            anySimplifications |=
                rewriteIntSwitch(code, blocksIterator, block, iterator, theSwitch.asIntSwitch());
          }
        }
      }
    }

    // Rewriting of switches introduces new branching structure. It relies on critical edges
    // being split on the way in but does not maintain this property. We therefore split
    // critical edges at exit.
    code.splitCriticalEdges();

    Set<Value> affectedValues =
        needToRemoveUnreachableBlocks ? code.removeUnreachableBlocks() : ImmutableSet.of();
    boolean anyAffectedValues = !affectedValues.isEmpty();
    if (anyAffectedValues) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
    return create(anyAffectedValues, anySimplifications);
  }

  public void rewriteSingleKeySwitchToIf(
      IRCode code, BasicBlock block, InstructionListIterator iterator, IntSwitch theSwitch) {
    // Rewrite the switch to an if.
    int fallthroughBlockIndex = theSwitch.getFallthroughBlockIndex();
    int caseBlockIndex = theSwitch.targetBlockIndices()[0];
    if (fallthroughBlockIndex < caseBlockIndex) {
      block.swapSuccessorsByIndex(fallthroughBlockIndex, caseBlockIndex);
    }
    If replacement;
    if (theSwitch.isIntSwitch() && theSwitch.asIntSwitch().getFirstKey() == 0) {
      replacement = new If(IfType.EQ, theSwitch.value());
    } else {
      Instruction labelConst = theSwitch.materializeFirstKey(appView, code);
      labelConst.setPosition(theSwitch.getPosition());
      iterator.previous();
      iterator.add(labelConst);
      Instruction dummy = iterator.next();
      assert dummy == theSwitch;
      replacement = new If(IfType.EQ, ImmutableList.of(theSwitch.value(), labelConst.outValue()));
    }
    iterator.replaceCurrentInstruction(replacement);
  }

  private boolean rewriteIntSwitch(
      IRCode code,
      ListIterator<BasicBlock> blockIterator,
      BasicBlock block,
      InstructionListIterator iterator,
      IntSwitch theSwitch) {
    if (disableSwitchToIfRewritingForClassIdComparisons(theSwitch)) {
      return false;
    }

    if (theSwitch.numberOfKeys() == 1) {
      rewriteSingleKeySwitchToIf(code, block, iterator, theSwitch);
      return true;
    }

    // If there are more than 1 key, we use the following algorithm to find keys to combine.
    // First, scan through the keys forward and combine each packed interval with the
    // previous interval if it gives a net saving.
    // Secondly, go through all created intervals and combine the ones without a saving into
    // a single interval and keep a max number of packed switches.
    // Finally, go through all intervals and check if the switch or part of the switch
    // should be transformed to ifs.

    // Phase 1: Combine packed intervals.
    InternalOutputMode mode = options.getInternalOutputMode();
    int[] keys = theSwitch.getKeys();
    int maxNumberOfIfsOrSwitches = 10;
    PriorityQueue<Interval> biggestPackedSavings =
        new PriorityQueue<>((x, y) -> Long.compare(y.packedSavings(mode), x.packedSavings(mode)));
    Set<Interval> biggestPackedSet = new HashSet<>();
    List<Interval> intervals = new ArrayList<>();
    int previousKey = keys[0];
    IntList currentKeys = new IntArrayList();
    currentKeys.add(previousKey);
    Interval previousInterval = null;
    for (int i = 1; i < keys.length; i++) {
      int key = keys[i];
      if (((long) key - (long) previousKey) > 1) {
        Interval current = new Interval(currentKeys);
        Interval added = combineOrAddInterval(intervals, previousInterval, current);
        if (added != current && biggestPackedSet.contains(previousInterval)) {
          biggestPackedSet.remove(previousInterval);
          biggestPackedSavings.remove(previousInterval);
        }
        tryAddToBiggestSavings(
            biggestPackedSet, biggestPackedSavings, added, maxNumberOfIfsOrSwitches);
        previousInterval = added;
        currentKeys = new IntArrayList();
      }
      currentKeys.add(key);
      previousKey = key;
    }
    Interval current = new Interval(currentKeys);
    Interval added = combineOrAddInterval(intervals, previousInterval, current);
    if (added != current && biggestPackedSet.contains(previousInterval)) {
      biggestPackedSet.remove(previousInterval);
      biggestPackedSavings.remove(previousInterval);
    }
    tryAddToBiggestSavings(biggestPackedSet, biggestPackedSavings, added, maxNumberOfIfsOrSwitches);

    // Phase 2: combine sparse intervals into a single bin.
    // Check if we should save a space for a sparse switch, if so, remove the switch with
    // the smallest savings.
    if (biggestPackedSet.size() == maxNumberOfIfsOrSwitches
        && maxNumberOfIfsOrSwitches < intervals.size()) {
      biggestPackedSet.remove(biggestPackedSavings.poll());
    }
    Interval sparse = null;
    List<Interval> newSwitches = new ArrayList<>(maxNumberOfIfsOrSwitches);
    for (int i = 0; i < intervals.size(); i++) {
      Interval interval = intervals.get(i);
      if (biggestPackedSet.contains(interval)) {
        newSwitches.add(interval);
      } else if (sparse == null) {
        sparse = interval;
        newSwitches.add(sparse);
      } else {
        sparse.addInterval(interval);
      }
    }

    // Phase 3: at this point we are guaranteed to have the biggest saving switches
    // in newIntervals, potentially with a switch combining the remaining intervals.
    // Now we check to see if we can create any if's to reduce size.
    IntList outliers = new IntArrayList();
    int outliersAsIfSize =
        options.testing.enableSwitchToIfRewriting
            ? findIfsForCandidates(newSwitches, theSwitch, outliers)
            : 0;

    long newSwitchesSize = 0;
    List<IntList> newSwitchSequences = new ArrayList<>(newSwitches.size());
    for (Interval interval : newSwitches) {
      newSwitchesSize += interval.estimatedSize(mode);
      newSwitchSequences.add(interval.keys);
    }

    long currentSize = IntSwitch.estimatedSize(mode, theSwitch.getKeys());
    if (newSwitchesSize + outliersAsIfSize + codeUnitMargin() < currentSize) {
      convertSwitchToSwitchAndIfs(
          code, blockIterator, block, iterator, theSwitch, newSwitchSequences, outliers);
      return true;
    }
    return false;
  }

  // TODO(b/181732463): We currently disable switch-to-if rewritings for switches on $r8$classId
  //  field values (from horizontal class merging. See bug for more details.
  private boolean disableSwitchToIfRewritingForClassIdComparisons(IntSwitch theSwitch) {
    Value switchValue = theSwitch.value().getAliasedValue();
    if (!switchValue.isDefinedByInstructionSatisfying(Instruction::isInstanceGet)) {
      return false;
    }
    AppInfoWithLiveness appInfo = appView.appInfoWithLiveness();
    if (appInfo == null) {
      return false;
    }
    InstanceGet instanceGet = switchValue.getDefinition().asInstanceGet();
    SingleProgramFieldResolutionResult resolutionResult =
        appInfo.resolveField(instanceGet.getField()).asSingleProgramFieldResolutionResult();
    if (resolutionResult == null) {
      return false;
    }
    DexEncodedField resolvedField = resolutionResult.getResolvedField();
    return HorizontalClassMergerUtils.isClassIdField(appView, resolvedField);
  }

  private SwitchCaseEliminator removeUnnecessarySwitchCases(
      IRCode code,
      Switch theSwitch,
      InstructionListIterator iterator,
      SwitchCaseAnalyzer switchCaseAnalyzer) {
    BasicBlock defaultTarget = theSwitch.fallthroughBlock();
    SwitchCaseEliminator eliminator = new SwitchCaseEliminator(theSwitch, iterator);
    BasicBlockBehavioralSubsumption behavioralSubsumption =
        new BasicBlockBehavioralSubsumption(appView, code);

    // Compute the set of switch cases that can be removed.
    boolean hasSwitchCaseToDefaultRewrite = false;
    AbstractValue switchAbstractValue = theSwitch.value().getAbstractValue(appView, code.context());
    for (int i = 0; i < theSwitch.numberOfKeys(); i++) {
      BasicBlock targetBlock = theSwitch.targetBlock(i);

      if (switchCaseAnalyzer.switchCaseIsAlwaysHit(theSwitch, i)) {
        eliminator.markSwitchCaseAsAlwaysHit(i);
        break;
      }

      // This switch case can be removed if the behavior of the target block is equivalent to the
      // behavior of the default block, or if the switch case is unreachable.
      if (switchCaseAnalyzer.switchCaseIsUnreachable(theSwitch, switchAbstractValue, i)) {
        eliminator.markSwitchCaseForRemoval(i);
      } else if (behavioralSubsumption.isSubsumedBy(
          theSwitch.value(), targetBlock, defaultTarget)) {
        eliminator.markSwitchCaseForRemoval(i);
        hasSwitchCaseToDefaultRewrite = true;
      }
    }

    if (eliminator.isFallthroughLive()
        && !hasSwitchCaseToDefaultRewrite
        && switchCaseAnalyzer.switchFallthroughIsNeverHit(theSwitch, switchAbstractValue)) {
      eliminator.markSwitchFallthroughAsNeverHit();
    }

    eliminator.optimize();
    return eliminator;
  }

  private static class Interval {

    private final IntList keys = new IntArrayList();

    public Interval(IntList... allKeys) {
      assert allKeys.length > 0;
      for (IntList keys : allKeys) {
        assert keys.size() > 0;
        this.keys.addAll(keys);
      }
    }

    public int getMin() {
      return keys.getInt(0);
    }

    public int getMax() {
      return keys.getInt(keys.size() - 1);
    }

    public void addInterval(Interval other) {
      assert getMax() < other.getMin();
      keys.addAll(other.keys);
    }

    public long packedSavings(InternalOutputMode mode) {
      long packedTargets = (long) getMax() - (long) getMin() + 1;
      if (!IntSwitch.canBePacked(mode, packedTargets)) {
        return Long.MIN_VALUE + 1;
      }
      long sparseCost =
          IntSwitch.baseSparseSize(mode) + IntSwitch.sparsePayloadSize(mode, keys.size());
      long packedCost =
          IntSwitch.basePackedSize(mode) + IntSwitch.packedPayloadSize(mode, packedTargets);
      return sparseCost - packedCost;
    }

    public long estimatedSize(InternalOutputMode mode) {
      return IntSwitch.estimatedSize(mode, keys.toIntArray());
    }
  }

  private Interval combineOrAddInterval(
      List<Interval> intervals, Interval previous, Interval current) {
    // As a first iteration, we only combine intervals if their packed size is less than their
    // sparse counterpart. In CF we will have to add a load and a jump which add to the
    // stack map table (1 is the size of a same entry).
    InternalOutputMode mode = options.getInternalOutputMode();
    int penalty = mode.isGeneratingClassFiles() ? 3 + 1 : 0;
    if (previous == null) {
      intervals.add(current);
      return current;
    }
    Interval combined = new Interval(previous.keys, current.keys);
    long packedSavings = combined.packedSavings(mode);
    if (packedSavings <= 0
        || packedSavings < previous.estimatedSize(mode) + current.estimatedSize(mode) - penalty) {
      intervals.add(current);
      return current;
    } else {
      intervals.set(intervals.size() - 1, combined);
      return combined;
    }
  }

  private void tryAddToBiggestSavings(
      Set<Interval> biggestPackedSet,
      PriorityQueue<Interval> intervals,
      Interval toAdd,
      int maximumNumberOfSwitches) {
    assert !biggestPackedSet.contains(toAdd);
    long savings = toAdd.packedSavings(options.getInternalOutputMode());
    if (savings <= 0) {
      return;
    }
    if (intervals.size() < maximumNumberOfSwitches) {
      intervals.add(toAdd);
      biggestPackedSet.add(toAdd);
    } else if (savings > intervals.peek().packedSavings(options.getInternalOutputMode())) {
      intervals.add(toAdd);
      biggestPackedSet.add(toAdd);
      biggestPackedSet.remove(intervals.poll());
    }
  }

  private int codeUnitMargin() {
    return options.getInternalOutputMode().isGeneratingClassFiles() ? 3 : 1;
  }

  private int findIfsForCandidates(
      List<Interval> newSwitches, IntSwitch theSwitch, IntList outliers) {
    Set<Interval> switchesToRemove = new HashSet<>();
    InternalOutputMode mode = options.getInternalOutputMode();
    int outliersAsIfSize = 0;
    // The candidateForIfs is either an index to a switch that can be eliminated totally or a sparse
    // where removing a key may produce a greater saving. It is only if keys are small in the packed
    // switch that removing the keys makes sense (size wise).
    for (Interval candidate : newSwitches) {
      int maxIfBudget = 10;
      long switchSize = candidate.estimatedSize(mode);
      int sizeOfAllKeysAsIf = sizeForKeysWrittenAsIfs(theSwitch.value().outType(), candidate.keys);
      if (candidate.keys.size() <= maxIfBudget
          && sizeOfAllKeysAsIf < switchSize - codeUnitMargin()) {
        outliersAsIfSize += sizeOfAllKeysAsIf;
        switchesToRemove.add(candidate);
        outliers.addAll(candidate.keys);
        continue;
      }
      // One could do something clever here, but we use a simple algorithm that use the fact that
      // all keys are sorted in ascending order and that the smallest absolute value will give the
      // best saving.
      IntList candidateKeys = candidate.keys;
      int smallestPosition = -1;
      long smallest = Long.MAX_VALUE;
      for (int i = 0; i < candidateKeys.size(); i++) {
        long current = Math.abs((long) candidateKeys.getInt(i));
        if (current < smallest) {
          smallestPosition = i;
          smallest = current;
        }
      }
      // Add as many keys forward and backward as we have budget and we decrease in size.
      IntList ifKeys = new IntArrayList();
      ifKeys.add(candidateKeys.getInt(smallestPosition));
      long previousSavings = 0;
      long currentSavings =
          switchSize
              - sizeForKeysWrittenAsIfs(theSwitch.value().outType(), ifKeys)
              - IntSwitch.estimatedSparseSize(mode, candidateKeys.size() - ifKeys.size());
      int minIndex = smallestPosition - 1;
      int maxIndex = smallestPosition + 1;
      while (ifKeys.size() < maxIfBudget && currentSavings > previousSavings) {
        if (minIndex >= 0 && maxIndex < candidateKeys.size()) {
          long valMin = Math.abs((long) candidateKeys.getInt(minIndex));
          int valMax = Math.abs(candidateKeys.getInt(maxIndex));
          if (valMax <= valMin) {
            ifKeys.add(candidateKeys.getInt(maxIndex++));
          } else {
            ifKeys.add(candidateKeys.getInt(minIndex--));
          }
        } else if (minIndex >= 0) {
          ifKeys.add(candidateKeys.getInt(minIndex--));
        } else if (maxIndex < candidateKeys.size()) {
          ifKeys.add(candidateKeys.getInt(maxIndex++));
        } else {
          // No more elements to add as if's.
          break;
        }
        previousSavings = currentSavings;
        currentSavings =
            switchSize
                - sizeForKeysWrittenAsIfs(theSwitch.value().outType(), ifKeys)
                - IntSwitch.estimatedSparseSize(mode, candidateKeys.size() - ifKeys.size());
      }
      if (previousSavings >= currentSavings) {
        // Remove the last added key since it did not contribute to savings.
        int lastKey = ifKeys.getInt(ifKeys.size() - 1);
        ifKeys.removeInt(ifKeys.size() - 1);
        if (lastKey == candidateKeys.getInt(minIndex + 1)) {
          minIndex++;
        } else {
          maxIndex--;
        }
      }
      // Adjust pointers into the candidate keys.
      minIndex++;
      maxIndex--;
      if (ifKeys.size() > 0) {
        int ifsSize = sizeForKeysWrittenAsIfs(theSwitch.value().outType(), ifKeys);
        long newSwitchSize =
            IntSwitch.estimatedSparseSize(mode, candidateKeys.size() - ifKeys.size());
        if (newSwitchSize + ifsSize + codeUnitMargin() < switchSize) {
          candidateKeys.removeElements(minIndex, maxIndex);
          outliers.addAll(ifKeys);
          outliersAsIfSize += ifsSize;
        }
      }
    }
    newSwitches.removeAll(switchesToRemove);
    return outliersAsIfSize;
  }

  private int sizeForKeysWrittenAsIfs(ValueType type, Collection<Integer> keys) {
    int ifsSize = If.estimatedSize(options.getInternalOutputMode()) * keys.size();
    // In Cf we also require a load as well (and a stack map entry)
    if (options.getInternalOutputMode().isGeneratingClassFiles()) {
      ifsSize += keys.size() * (3 + 1);
    }
    for (int k : keys) {
      if (k != 0) {
        ifsSize += ConstNumber.estimatedSize(options.getInternalOutputMode(), type, k);
      }
    }
    return ifsSize;
  }

  /**
   * Covert the switch instruction to a sequence of if instructions checking for a specified set of
   * keys, followed by a new switch with the remaining keys.
   */
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  public void convertSwitchToSwitchAndIfs(
      IRCode code,
      ListIterator<BasicBlock> blocksIterator,
      BasicBlock originalBlock,
      InstructionListIterator iterator,
      IntSwitch theSwitch,
      List<IntList> switches,
      IntList keysToRemove) {

    Position position = theSwitch.getPosition();

    // Extract the information from the switch before removing it.
    Int2ReferenceSortedMap<BasicBlock> keyToTarget = theSwitch.getKeyToTargetMap();

    // Keep track of the current fallthrough, starting with the original.
    BasicBlock fallthroughBlock = theSwitch.fallthroughBlock();

    // Split the switch instruction into its own block and remove it.
    iterator.previous();
    BasicBlock originalSwitchBlock = iterator.split(code, blocksIterator);
    assert !originalSwitchBlock.hasCatchHandlers();
    assert originalSwitchBlock.getInstructions().size() == 1;
    assert originalBlock.exit().isGoto();
    theSwitch.moveDebugValues(originalBlock.exit());
    blocksIterator.remove();
    theSwitch.getBlock().detachAllSuccessors();
    BasicBlock block = theSwitch.getBlock().unlinkSinglePredecessor();
    assert theSwitch.getBlock().getPredecessors().size() == 0;
    assert theSwitch.getBlock().getSuccessors().size() == 0;
    assert block == originalBlock;

    // Collect the new blocks for adding to the block list.
    LinkedList<BasicBlock> newBlocks = new LinkedList<>();

    // Build the switch-blocks backwards, to always have the fallthrough block in hand.
    for (int i = switches.size() - 1; i >= 0; i--) {
      SwitchBuilder switchBuilder = new SwitchBuilder(position);
      switchBuilder.setValue(theSwitch.value());
      IntList keys = switches.get(i);
      for (int j = 0; j < keys.size(); j++) {
        int key = keys.getInt(j);
        switchBuilder.addKeyAndTarget(key, keyToTarget.get(key));
      }
      switchBuilder.setFallthrough(fallthroughBlock).setBlockNumber(code.getNextBlockNumber());
      BasicBlock newSwitchBlock = switchBuilder.build(code.metadata());
      newBlocks.addFirst(newSwitchBlock);
      fallthroughBlock = newSwitchBlock;
    }

    // Build the if-blocks backwards, to always have the fallthrough block in hand.
    for (int i = keysToRemove.size() - 1; i >= 0; i--) {
      int key = keysToRemove.getInt(i);
      BasicBlock peeledOffTarget = keyToTarget.get(key);
      IfBuilder ifBuilder = new IfBuilder(position, code);
      ifBuilder
          .setLeft(theSwitch.value())
          .setRight(key)
          .setTarget(peeledOffTarget)
          .setFallthrough(fallthroughBlock)
          .setBlockNumber(code.getNextBlockNumber());
      BasicBlock ifBlock = ifBuilder.build();
      newBlocks.addFirst(ifBlock);
      fallthroughBlock = ifBlock;
    }

    // Finally link the block before the original switch to the new block sequence.
    originalBlock.link(fallthroughBlock);

    // Finally add the blocks.
    newBlocks.forEach(blocksIterator::add);
  }

  // TODO(sgjesse); Move this somewhere else, and reuse it for some of the other switch rewritings.
  private abstract static class InstructionBuilder<T> {

    int blockNumber;
    final Position position;

    InstructionBuilder(Position position) {
      this.position = position;
    }

    abstract T self();

    T setBlockNumber(int blockNumber) {
      this.blockNumber = blockNumber;
      return self();
    }
  }

  private static class SwitchBuilder extends InstructionBuilder<SwitchBuilder> {

    private Value value;
    private final Int2ReferenceSortedMap<BasicBlock> keyToTarget = new Int2ReferenceAVLTreeMap<>();
    private BasicBlock fallthrough;

    SwitchBuilder(Position position) {
      super(position);
    }

    @Override
    SwitchBuilder self() {
      return this;
    }

    SwitchBuilder setValue(Value value) {
      this.value = value;
      return this;
    }

    SwitchBuilder addKeyAndTarget(int key, BasicBlock target) {
      keyToTarget.put(key, target);
      return this;
    }

    SwitchBuilder setFallthrough(BasicBlock fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }

    BasicBlock build(IRMetadata metadata) {
      final int NOT_FOUND = -1;
      Object2IntMap<BasicBlock> targetToSuccessorIndex = new Object2IntLinkedOpenHashMap<>();
      targetToSuccessorIndex.defaultReturnValue(NOT_FOUND);

      int[] keys = new int[keyToTarget.size()];
      int[] targetBlockIndices = new int[keyToTarget.size()];
      // Sort keys descending.
      int count = 0;
      IntIterator iter = keyToTarget.keySet().iterator();
      while (iter.hasNext()) {
        int key = iter.nextInt();
        BasicBlock target = keyToTarget.get(key);
        Integer targetIndex =
            targetToSuccessorIndex.computeIfAbsent(target, b -> targetToSuccessorIndex.size());
        keys[count] = key;
        targetBlockIndices[count] = targetIndex;
        count++;
      }
      Integer fallthroughIndex =
          targetToSuccessorIndex.computeIfAbsent(fallthrough, b -> targetToSuccessorIndex.size());
      IntSwitch newSwitch = new IntSwitch(value, keys, targetBlockIndices, fallthroughIndex);
      newSwitch.setPosition(position);
      BasicBlock newSwitchBlock = BasicBlock.createSwitchBlock(blockNumber, newSwitch, metadata);
      for (BasicBlock successor : targetToSuccessorIndex.keySet()) {
        newSwitchBlock.link(successor);
      }
      return newSwitchBlock;
    }
  }

  private static class IfBuilder extends InstructionBuilder<IfBuilder> {

    private final IRCode code;
    private Value left;
    private int right;
    private BasicBlock target;
    private BasicBlock fallthrough;

    IfBuilder(Position position, IRCode code) {
      super(position);
      this.code = code;
    }

    @Override
    IfBuilder self() {
      return this;
    }

    IfBuilder setLeft(Value left) {
      this.left = left;
      return this;
    }

    IfBuilder setRight(int right) {
      this.right = right;
      return this;
    }

    IfBuilder setTarget(BasicBlock target) {
      this.target = target;
      return this;
    }

    IfBuilder setFallthrough(BasicBlock fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }

    BasicBlock build() {
      assert target != null;
      assert fallthrough != null;
      If newIf;
      BasicBlock ifBlock;
      if (right != 0) {
        ConstNumber rightConst = code.createIntConstant(right);
        rightConst.setPosition(position);
        newIf = new If(IfType.EQ, ImmutableList.of(left, rightConst.dest()));
        ifBlock = BasicBlock.createIfBlock(blockNumber, newIf, code.metadata(), rightConst);
      } else {
        newIf = new If(IfType.EQ, left);
        ifBlock = BasicBlock.createIfBlock(blockNumber, newIf, code.metadata());
      }
      newIf.setPosition(position);
      ifBlock.link(target);
      ifBlock.link(fallthrough);
      return ifBlock;
    }
  }
}
