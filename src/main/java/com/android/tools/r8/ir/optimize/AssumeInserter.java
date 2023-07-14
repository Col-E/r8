// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.LazyDominatorTree;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfoLookup;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TriFunction;
import com.android.tools.r8.utils.TriPredicate;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AssumeInserter {

  private final AppView<AppInfoWithLiveness> appView;
  private final boolean keepRedundantBlocks;

  public AssumeInserter(AppView<AppInfoWithLiveness> appView) {
    this(appView, false);
  }

  public AssumeInserter(AppView<AppInfoWithLiveness> appView, boolean keepRedundantBlocks) {
    this.appView = appView;
    this.keepRedundantBlocks = keepRedundantBlocks;
  }

  public void insertAssumeInstructions(IRCode code, Timing timing) {
    insertAssumeInstructionsInBlocks(code, code.listIterator(), alwaysTrue(), timing);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  public void insertAssumeInstructionsInBlocks(
      IRCode code,
      BasicBlockIterator blockIterator,
      Predicate<BasicBlock> blockTester,
      Timing timing) {
    timing.begin("Insert assume instructions");
    internalInsertAssumeInstructionsInBlocks(code, blockIterator, blockTester, timing);
    timing.end();
  }

  private void internalInsertAssumeInstructionsInBlocks(
      IRCode code,
      BasicBlockIterator blockIterator,
      Predicate<BasicBlock> blockTester,
      Timing timing) {
    timing.begin("Part 1: Compute assumed values");
    AssumedValues assumedValues = computeAssumedValues(code, blockIterator, blockTester);
    timing.end();
    if (assumedValues.isEmpty()) {
      return;
    }

    timing.begin("Part 2: Remove redundant assume instructions");
    removeRedundantAssumeInstructions(assumedValues);
    timing.end();

    timing.begin("Part 3: Compute dominated users");
    Map<Instruction, Map<Value, AssumedValueInfo>> redundantAssumedValues =
        computeDominanceForAssumedValues(code, assumedValues);
    timing.end();
    if (assumedValues.isEmpty()) {
      return;
    }

    timing.begin("Part 4: Remove redundant dominated assume instructions");
    removeRedundantDominatedAssumeInstructions(assumedValues, redundantAssumedValues);
    timing.end();
    if (assumedValues.isEmpty()) {
      return;
    }

    timing.begin("Part 5: Materialize assume instructions");
    materializeAssumeInstructions(code, assumedValues);
    timing.end();
  }

  private AssumedValues computeAssumedValues(
      IRCode code, BasicBlockIterator blockIterator, Predicate<BasicBlock> blockTester) {
    AssumedValues.Builder assumedValuesBuilder = new AssumedValues.Builder();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blockTester.test(block)) {
        computeAssumedValuesInBlock(code, blockIterator, block, assumedValuesBuilder);
      }
    }
    return assumedValuesBuilder.build();
  }

  private void computeAssumedValuesInBlock(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      AssumedValues.Builder assumedValuesBuilder) {
    // Add non-null after
    // 1) instructions that implicitly indicate receiver/array is not null.
    // 2) invocations that are guaranteed to return a non-null value.
    // 3) parameters that are not null after the invocation.
    // 4) field-get instructions that are guaranteed to read a non-null value.
    InstructionListIterator instructionIterator = block.listIterator(code);
    while (instructionIterator.hasNext()) {
      Instruction current = instructionIterator.next();
      boolean needsAssumeInstruction = false;

      // Case (1), instructions that implicitly indicate receiver/array is not null.
      if (current.throwsOnNullInput()) {
        Value inValue = current.getNonNullInput();
        if (assumedValuesBuilder.isMaybeNullAndNotNullType(inValue)
            && isNullableReferenceTypeWithOtherNonDebugUsers(inValue, current)) {
          assumedValuesBuilder.addNonNullValueWithUnknownDominance(current, inValue);
          needsAssumeInstruction = true;
        }
      }

      if (current.isInvokeMethod()) {
        // Case (2) and (3).
        needsAssumeInstruction |=
            computeAssumedValuesForInvokeMethod(
                code, current.asInvokeMethod(), assumedValuesBuilder);
      } else if (current.isFieldGet()) {
        // Case (4), field-get instructions that are guaranteed to read a non-null value.
        needsAssumeInstruction |=
            computeAssumedValuesForFieldGet(current.asFieldInstruction(), assumedValuesBuilder);
      }

      // If we need to insert an assume instruction into a block with catch handlers, we split the
      // block such that the IR is ready for the insertion of the assume instruction.
      //
      // This splitting could in principle be deferred until we materialize the assume instructions,
      // but then we would need to rewind the basic block iterator to the beginning, and scan over
      // the instructions another time, splitting the blocks as needed.
      if (block.hasCatchHandlers()) {
        if (needsAssumeInstruction) {
          BasicBlock insertionBlock = instructionIterator.split(code, blockIterator);
          assert !instructionIterator.hasNext();
          assert instructionIterator.peekPrevious().isGoto();
          assert blockIterator.peekPrevious() == insertionBlock;
          computeAssumedValuesInBlock(code, blockIterator, insertionBlock, assumedValuesBuilder);
          return;
        }
        if (current.instructionTypeCanThrow()) {
          break;
        }
      }
    }

    If ifInstruction = block.exit().asIf();
    if (ifInstruction != null && ifInstruction.isNonTrivialNullTest()) {
      Value lhs = ifInstruction.lhs();
      if (assumedValuesBuilder.isMaybeNullAndNotNullType(lhs)
          && isNullableReferenceTypeWithOtherNonDebugUsers(lhs, ifInstruction)
          && ifInstruction.targetFromNonNullObject().getPredecessors().size() == 1) {
        assumedValuesBuilder.addNonNullValueWithUnknownDominance(ifInstruction, lhs);
      }
    }
  }

  private boolean computeAssumedValuesForInvokeMethod(
      IRCode code, InvokeMethod invoke, AssumedValues.Builder assumedValuesBuilder) {
    if (!invoke.hasOutValue() && invoke.getInvokedMethod().proto.parameters.isEmpty()) {
      return false;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.holder.isArrayType()
        && invokedMethod.match(appView.dexItemFactory().objectMembers.clone)) {
      return computeAssumedValuesFromArrayClone(invoke, assumedValuesBuilder);
    }

    return computeAssumedValuesFromSingleTarget(code, invoke, assumedValuesBuilder);
  }

  private boolean computeAssumedValuesFromArrayClone(
      InvokeMethod invoke, AssumedValues.Builder assumedValuesBuilder) {
    Value outValue = invoke.outValue();
    if (outValue == null || !outValue.hasNonDebugUsers()) {
      return false;
    }

    DynamicTypeWithUpperBound dynamicType =
        invoke.getInvokedMethod().getHolderType().toDynamicType(appView, definitelyNotNull());
    assumedValuesBuilder.addAssumedValueKnownToDominateAllUsers(invoke, outValue, dynamicType);
    return true;
  }

  private boolean computeAssumedValuesFromSingleTarget(
      IRCode code, InvokeMethod invoke, AssumedValues.Builder assumedValuesBuilder) {
    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormatLegacy(invoke.getInvokedMethod())
            .asSingleResolution();
    if (resolutionResult == null) {
      return false;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
    if (invoke.hasUsedOutValue() && invoke.getOutType().isReferenceType()) {
      AssumeInfo assumeInfo =
          AssumeInfoLookup.lookupAssumeInfo(appView, resolutionResult, singleTarget);
      if (assumeInfo.getAssumeType().getNullability().isDefinitelyNotNull()) {
        assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(invoke, invoke.outValue());
      }
    }

    if (singleTarget == null) {
      return false;
    }

    boolean needsAssumeInstruction = false;
    MethodOptimizationInfo optimizationInfo = singleTarget.getDefinition().getOptimizationInfo();

    // Case (2), invocations that are guaranteed to return a non-null value.
    if (invoke.hasUsedOutValue()) {
      needsAssumeInstruction =
          computeAssumedValuesForOutValue(
              invoke, optimizationInfo.getDynamicType(), assumedValuesBuilder);
    }

    // Case (3), parameters that are not null after the invocation.
    BitSet nonNullParamOnNormalExits = optimizationInfo.getNonNullParamOnNormalExits();
    if (nonNullParamOnNormalExits != null) {
      int start = invoke.isInvokeMethodWithReceiver() ? 1 : 0;
      for (int i = start; i < invoke.arguments().size(); i++) {
        if (nonNullParamOnNormalExits.get(i)) {
          Value argument = invoke.getArgument(i);
          if (assumedValuesBuilder.isMaybeNullAndNotNullType(argument)
              && isNullableReferenceTypeWithOtherNonDebugUsers(argument, invoke)) {
            assumedValuesBuilder.addNonNullValueWithUnknownDominance(invoke, argument);
            needsAssumeInstruction = true;
          }
        }
      }
    }
    return needsAssumeInstruction;
  }

  private boolean computeAssumedValuesForFieldGet(
      FieldInstruction fieldGet, AssumedValues.Builder assumedValuesBuilder) {
    if (fieldGet.hasUnusedOutValue()) {
      return false;
    }

    SingleFieldResolutionResult<?> resolutionResult =
        appView.appInfo().resolveField(fieldGet.getField()).asSingleFieldResolutionResult();
    if (resolutionResult == null) {
      return false;
    }

    DexClassAndField field = resolutionResult.getResolutionPair();

    if (field.getType().isReferenceType()) {
      AssumeInfo assumeInfo = appView.getAssumeInfoCollection().get(field);
      if (assumeInfo.getAssumeType().getNullability().isDefinitelyNotNull()) {
        assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(fieldGet, fieldGet.outValue());
      }
    }

    FieldOptimizationInfo optimizationInfo = field.getDefinition().getOptimizationInfo();
    return computeAssumedValuesForOutValue(
        fieldGet, optimizationInfo.getDynamicType(), assumedValuesBuilder);
  }

  private boolean computeAssumedValuesForOutValue(
      Instruction instruction,
      DynamicType dynamicType,
      AssumedValues.Builder assumedValuesBuilder) {
    Value outValue = instruction.outValue();
    // Do not insert dynamic type information if it does not refine the static type.
    if (dynamicType.isUnknown()) {
      return false;
    }

    // Insert an assume-not-null instruction if the dynamic type only refines the nullability.
    if (dynamicType.isNotNullType()) {
      assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(instruction, outValue);
      return true;
    }

    DynamicTypeWithUpperBound dynamicTypeWithUpperBound = dynamicType.asDynamicTypeWithUpperBound();
    DynamicTypeWithUpperBound staticType = DynamicType.create(appView, outValue.getType());
    if (!dynamicTypeWithUpperBound.strictlyLessThan(staticType, appView)) {
      return false;
    }

    if (!dynamicTypeWithUpperBound.getNullability().isMaybeNull()
        && dynamicTypeWithUpperBound.withNullability(Nullability.maybeNull()).equals(staticType)) {
      assert dynamicTypeWithUpperBound.getNullability().isDefinitelyNotNull();
      assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(instruction, outValue);
    } else {
      assumedValuesBuilder.addAssumedValueKnownToDominateAllUsers(
          instruction, outValue, dynamicTypeWithUpperBound);
    }
    return true;
  }

  private void removeRedundantAssumeInstructions(AssumedValues assumedValues) {
    assumedValues.removeIf(
        (instruction, assumedValue, assumedValueInfo) -> {
          // Assumed values with dynamic type information are never redundant.
          if (assumedValueInfo.hasDynamicTypeInfoIgnoringNullability()) {
            return false;
          }

          assert assumedValueInfo.isNonNull();

          // Otherwise, it is redundant if it is defined by another instruction that guarantees its
          // non-nullness.
          if (assumedValue.isPhi()) {
            return false;
          }

          Instruction definition = assumedValue.getDefinition();
          if (definition == instruction) {
            return false;
          }

          AssumedValueInfo otherAssumedValueInfo =
              assumedValues.getAssumedValueInfo(definition, assumedValue);
          if (otherAssumedValueInfo == null) {
            return false;
          }

          if (!otherAssumedValueInfo.isNonNull()) {
            // This is not redundant, but we can strengthen it with the dynamic type information
            // from the other assume instruction.
            assumedValueInfo.setDynamicType(
                otherAssumedValueInfo
                    .getDynamicType()
                    .withNullability(Nullability.definitelyNotNull()));
            return false;
          }

          return true;
        });
  }

  private Map<Instruction, Map<Value, AssumedValueInfo>> computeDominanceForAssumedValues(
      IRCode code, AssumedValues assumedValues) {
    Map<Instruction, Map<Value, AssumedValueInfo>> redundantAssumedValues = new IdentityHashMap<>();
    LazyDominatorTree lazyDominatorTree = new LazyDominatorTree(code);
    Map<BasicBlock, Set<BasicBlock>> dominatedBlocksCache = new IdentityHashMap<>();
    assumedValues.computeDominance(
        (instruction, assumedValue, assumedValueInfo) -> {
          Map<Value, AssumedValueInfo> alreadyAssumedValues =
              redundantAssumedValues.get(instruction);
          if (alreadyAssumedValues != null) {
            AssumedValueInfo alreadyAssumedValueInfo = alreadyAssumedValues.get(assumedValue);
            if (alreadyAssumedValueInfo != null) {
              if (assumedValueInfo.isSubsumedBy(alreadyAssumedValueInfo)) {
                // Returning redundant() will cause the entry (instruction, assumedValue) to be
                // removed.
                return AssumedDominance.redundant();
              }

              // This assume value is dominated by the other assume value, so strengthen this one.
              assumedValueInfo.strengthenWith(alreadyAssumedValueInfo);
            }
          }

          // If this value is the out-value of some instruction it is known to dominate all users.
          if (assumedValue == instruction.outValue()) {
            return AssumedDominance.everything();
          }

          // If we learn that this value is known to be non-null in the same block as it is defined,
          // and it is not used between its definition and the instruction that performs the null
          // check, then the non-null-value is known to dominate all other users than the null check
          // itself.
          BasicBlock block = instruction.getBlock();
          if (assumedValue.getBlock() == block
              && block.exit().isGoto()
              && !instruction.getBlock().hasCatchHandlers()) {
            InstructionIterator iterator = instruction.getBlock().iterator();
            if (!assumedValue.isPhi()) {
              iterator.nextUntil(x -> x != assumedValue.definition);
              iterator.previous();
            }
            boolean isUsedBeforeInstruction = false;
            while (iterator.hasNext()) {
              Instruction current = iterator.next();
              if (current == instruction) {
                break;
              }
              if (current.inValues().contains(assumedValue)
                  || current.getDebugValues().contains(assumedValue)) {
                isUsedBeforeInstruction = true;
                break;
              }
            }
            if (!isUsedBeforeInstruction) {
              return AssumedDominance.everythingElse();
            }
          }

          // Otherwise, we need a dominator tree to determine which users are dominated.
          BasicBlock insertionBlock = getInsertionBlock(instruction);

          assert assumedValue.hasPhiUsers()
              || assumedValue.uniqueUsers().stream().anyMatch(user -> user != instruction)
              || assumedValue.isArgument();

          // Find all users of the original value that are dominated by either the current block
          // or the new split-off block. Since NPE can be explicitly caught, nullness should be
          // propagated through dominance.
          DominatorTree dominatorTree = lazyDominatorTree.get();
          Set<BasicBlock> dominatedBlocks =
              dominatedBlocksCache.computeIfAbsent(
                  insertionBlock, x -> dominatorTree.dominatedBlocks(x, Sets.newIdentityHashSet()));

          AssumedDominance.Builder dominance = AssumedDominance.builder(assumedValue);
          for (Instruction user : assumedValue.uniqueUsers()) {
            if (user != instruction && dominatedBlocks.contains(user.getBlock())) {
              if (user.getBlock() == insertionBlock && insertionBlock == block) {
                Instruction first = block.iterator().nextUntil(x -> x == instruction || x == user);
                assert first != null;
                if (first == user) {
                  continue;
                }
              }
              dominance.addDominatedUser(user);

              // Record that there is no need to insert an assume instruction for the non-null-value
              // after the given user in case the user is also a null check for the non-null-value.
              redundantAssumedValues
                  .computeIfAbsent(user, ignore -> new IdentityHashMap<>())
                  .put(assumedValue, assumedValueInfo);
            }
          }
          for (Phi user : assumedValue.uniquePhiUsers()) {
            IntList dominatedPredecessorIndices =
                findDominatedPredecessorIndexesInPhi(user, assumedValue, dominatedBlocks);
            if (!dominatedPredecessorIndices.isEmpty()) {
              dominance.addDominatedPhiUser(user, dominatedPredecessorIndices);
            }
          }
          return dominance.build();
        });
    return redundantAssumedValues;
  }

  private void removeRedundantDominatedAssumeInstructions(
      AssumedValues assumedValues,
      Map<Instruction, Map<Value, AssumedValueInfo>> redundantAssumedValues) {
    assumedValues.removeAll(redundantAssumedValues);
  }

  private void materializeAssumeInstructions(IRCode code, AssumedValues assumedValues) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Map<BasicBlock, Map<Instruction, List<Instruction>>> pendingInsertions =
        new IdentityHashMap<>();

    // We materialize the assume instructions in two steps. First, we materialize all the assume
    // instructions that do not dominate everything. These assume instructions can refine previous
    // assume instructions, so we materialize those first as they are "stronger".
    //
    // Example:
    //   1. Object value = getNullableValueWithDynamicType();
    //   2. Object nullableValueWithDynamicType = assume(value, ...)
    //   3. checkNotNull(value);
    //   4. Object nonNullValueWithDynamicType = assume(value, ...)
    //   5. return value;
    //
    // In this example, we first materialize the assume instruction in line 4, and replace the
    // dominated use of `value` in line 5 by the new assumed value `nonNullValueWithDynamicType`.
    // Afterwards, we materialize the assume instruction in line 2, and replace all remaining users
    // of `value` by `nullableValueWithDynamicType`.
    //
    // Result:
    //   1. Object value = getNullableValueWithDynamicType();
    //   2. Object nullableValueWithDynamicType = assume(value, ...)
    //   3. checkNotNull(nullableValueWithDynamicType);
    //   4. Object nonNullValueWithDynamicType = assume(value, ...)
    //   5. return nonNullValueWithDynamicType;
    materializeSelectedAssumeInstructions(
        code,
        assumedValues,
        affectedValues,
        pendingInsertions,
        assumedValueInfo -> !assumedValueInfo.dominance.isEverything());
    materializeSelectedAssumeInstructions(
        code,
        assumedValues,
        affectedValues,
        pendingInsertions,
        assumedValueInfo -> assumedValueInfo.dominance.isEverything());
    pendingInsertions.forEach(
        (block, pendingInsertionsPerInstruction) -> {
          InstructionListIterator instructionIterator = block.listIterator(code);
          while (instructionIterator.hasNext() && !pendingInsertionsPerInstruction.isEmpty()) {
            Instruction instruction = instructionIterator.next();
            List<Instruction> pendingAssumeInstructions =
                pendingInsertionsPerInstruction.remove(instruction);
            if (pendingAssumeInstructions != null) {
              pendingAssumeInstructions.forEach(instructionIterator::add);
            }
          }
        });
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView, code)
          .setKeepRedundantBlocksAfterAssumeRemoval(keepRedundantBlocks)
          .narrowingWithAssumeRemoval(affectedValues);
    }
  }

  private void materializeSelectedAssumeInstructions(
      IRCode code,
      AssumedValues assumedValues,
      Set<Value> affectedValues,
      Map<BasicBlock, Map<Instruction, List<Instruction>>> pendingInsertions,
      Predicate<AssumedValueInfo> predicate) {
    assumedValues.removeIf(
        (instruction, assumedValue, assumedValueInfo) -> {
          if (!predicate.test(assumedValueInfo)) {
            return false;
          }

          BasicBlock block = instruction.getBlock();
          BasicBlock insertionBlock = getInsertionBlock(instruction);

          AssumedDominance dominance = assumedValueInfo.getDominance();
          Value newValue =
              assumedValueInfo.isNull()
                  ? code.createValue(TypeElement.getNull())
                  : code.createValue(
                      assumedValueInfo.isNonNull()
                          ? assumedValue.getType().asReferenceType().asMeetWithNotNull()
                          : assumedValue.getType(),
                      assumedValue.getLocalInfo());
          if (dominance.isEverything()) {
            assumedValue.replaceUsers(newValue);
          } else if (dominance.isEverythingElse()) {
            assumedValue.replaceSelectiveInstructionUsers(newValue, user -> user != instruction);
            assumedValue.replacePhiUsers(newValue);
          } else if (dominance.isSomething()) {
            SomethingAssumedDominance somethingDominance = dominance.asSomething();
            somethingDominance
                .getDominatedPhiUsers()
                .forEach(
                    (user, indices) -> {
                      IntListIterator iterator = indices.iterator();
                      while (iterator.hasNext()) {
                        Value operand = user.getOperand(iterator.nextInt());
                        if (operand != assumedValue) {
                          assert operand.isDefinedByInstructionSatisfying(Instruction::isAssume);
                          iterator.remove();
                        }
                      }
                    });
            assumedValue.replaceSelectiveUsers(
                newValue,
                somethingDominance.getDominatedUsers(),
                somethingDominance.getDominatedPhiUsers());
          }
          affectedValues.addAll(newValue.affectedValues());

          Instruction assumeInstruction;
          if (assumedValueInfo.isNull()) {
            assumeInstruction = new ConstNumber(newValue, 0);
          } else {
            assumeInstruction =
                Assume.create(
                    assumedValueInfo.dynamicType,
                    newValue,
                    assumedValue,
                    instruction,
                    appView,
                    code.context());
          }
          assumeInstruction.setPosition(instruction.getPosition());
          if (insertionBlock != block) {
            insertionBlock.listIterator(code).add(assumeInstruction);
          } else {
            pendingInsertions
                .computeIfAbsent(block, ignore -> new IdentityHashMap<>())
                .computeIfAbsent(instruction, ignore -> new ArrayList<>())
                .add(assumeInstruction);
          }
          return true;
        });
  }

  private BasicBlock getInsertionBlock(Instruction instruction) {
    if (instruction.isIf()) {
      return instruction.asIf().targetFromNonNullObject();
    }
    BasicBlock block = instruction.getBlock();
    if (block.hasCatchHandlers()) {
      return block.exit().asGoto().getTarget();
    }
    return block;
  }

  private IntList findDominatedPredecessorIndexesInPhi(
      Phi user, Value assumedValue, Set<BasicBlock> dominatedBlocks) {
    assert user.getOperands().contains(assumedValue);
    List<Value> operands = user.getOperands();
    List<BasicBlock> predecessors = user.getBlock().getPredecessors();
    assert operands.size() == predecessors.size();

    IntList predecessorIndexes = new IntArrayList();
    int index = 0;
    Iterator<Value> operandIterator = operands.iterator();
    Iterator<BasicBlock> predecessorIterator = predecessors.iterator();
    while (operandIterator.hasNext() && predecessorIterator.hasNext()) {
      Value operand = operandIterator.next();
      BasicBlock predecessor = predecessorIterator.next();
      // When this phi is chosen to be known-to-be-non-null value,
      // check if the corresponding predecessor is dominated by the block where non-null is added.
      if (operand == assumedValue && dominatedBlocks.contains(predecessor)) {
        predecessorIndexes.add(index);
      }

      index++;
    }
    return predecessorIndexes;
  }

  private static boolean isNullableReferenceType(Value value) {
    TypeElement type = value.getType();
    return type.isReferenceType()
        && type.asReferenceType().isNullable()
        && !type.nullability().isDefinitelyNull();
  }

  private static boolean isNullableReferenceTypeWithOtherNonDebugUsers(
      Value value, Instruction ignore) {
    if (isNullableReferenceType(value)) {
      if (value.hasPhiUsers()) {
        return true;
      }
      for (Instruction user : value.uniqueUsers()) {
        if (user != ignore) {
          return true;
        }
      }
    }
    return false;
  }

  static class AssumedValueInfo {

    AssumedDominance dominance;
    DynamicType dynamicType = DynamicType.unknown();

    AssumedValueInfo(AssumedDominance dominance) {
      this.dominance = dominance;
    }

    AssumedDominance getDominance() {
      return dominance;
    }

    void setDominance(AssumedDominance dominance) {
      this.dominance = dominance;
    }

    boolean hasDynamicTypeInfoIgnoringNullability() {
      return dynamicType.isDynamicTypeWithUpperBound() && !dynamicType.isUnknown();
    }

    DynamicType getDynamicType() {
      return dynamicType;
    }

    void setDynamicType(DynamicType dynamicType) {
      assert dynamicType != null;
      assert this.dynamicType.isNotNullType()
          ? dynamicType.getNullability().isDefinitelyNotNull()
          : this.dynamicType.isUnknown();
      this.dynamicType = dynamicType;
    }

    Nullability getNullability() {
      return dynamicType.getNullability();
    }

    boolean isNull() {
      return getNullability().isDefinitelyNull();
    }

    boolean isNonNull() {
      return getNullability().isDefinitelyNotNull();
    }

    void setNotNull() {
      if (dynamicType.isUnknown()) {
        dynamicType = DynamicType.definitelyNotNull();
      } else {
        dynamicType = dynamicType.withNullability(Nullability.definitelyNotNull());
      }
    }

    boolean isSubsumedBy(AssumedValueInfo other) {
      return !hasDynamicTypeInfoIgnoringNullability() && other.isNonNull();
    }

    void strengthenWith(AssumedValueInfo info) {
      if (info.isNonNull()) {
        setNotNull();
      }
      if (!hasDynamicTypeInfoIgnoringNullability()
          && info.hasDynamicTypeInfoIgnoringNullability()) {
        setDynamicType(info.getDynamicType().withNullability(getNullability()));
      }
    }
  }

  static class AssumedValues {

    /**
     * A mapping from each instruction to the (in and out) values that are guaranteed to be non-null
     * by the instruction. Each non-null value is subsequently mapped to the set of users that it
     * dominates.
     */
    Map<Instruction, Map<Value, AssumedValueInfo>> assumedValues;

    public AssumedValues(Map<Instruction, Map<Value, AssumedValueInfo>> assumedValues) {
      this.assumedValues = assumedValues;
    }

    public static Builder builder() {
      return new Builder();
    }

    void computeDominance(
        TriFunction<Instruction, Value, AssumedValueInfo, AssumedDominance> function) {
      Iterator<Entry<Instruction, Map<Value, AssumedValueInfo>>> outerIterator =
          assumedValues.entrySet().iterator();
      while (outerIterator.hasNext()) {
        Entry<Instruction, Map<Value, AssumedValueInfo>> outerEntry = outerIterator.next();
        Instruction instruction = outerEntry.getKey();
        Map<Value, AssumedValueInfo> dominancePerValue = outerEntry.getValue();
        Iterator<Entry<Value, AssumedValueInfo>> innerIterator =
            dominancePerValue.entrySet().iterator();
        while (innerIterator.hasNext()) {
          Entry<Value, AssumedValueInfo> innerEntry = innerIterator.next();
          Value assumedValue = innerEntry.getKey();
          AssumedValueInfo assumedValueInfo = innerEntry.getValue();
          AssumedDominance dominance = assumedValueInfo.dominance;
          if (dominance.isEverything()) {
            assert assumedValue.isDefinedByInstructionSatisfying(
                definition -> definition.outValue() == assumedValue);
            continue;
          }
          assert dominance.isUnknown();
          dominance = function.apply(instruction, assumedValue, assumedValueInfo);
          if ((dominance.isNothing() && !assumedValue.isArgument()) || dominance.isUnknown()) {
            innerIterator.remove();
          } else {
            assumedValueInfo.setDominance(dominance);
          }
        }
        if (dominancePerValue.isEmpty()) {
          outerIterator.remove();
        }
      }
    }

    AssumedValueInfo getAssumedValueInfo(Instruction instruction, Value assumedValue) {
      Map<Value, AssumedValueInfo> dominancePerValue = assumedValues.get(instruction);
      return dominancePerValue != null ? dominancePerValue.get(assumedValue) : null;
    }

    boolean isEmpty() {
      return assumedValues.isEmpty();
    }

    void removeAll(Map<Instruction, Map<Value, AssumedValueInfo>> keys) {
      keys.forEach(
          (instruction, redundantAssumedValues) -> {
            Map<Value, AssumedValueInfo> dominancePerValue = assumedValues.get(instruction);
            if (dominancePerValue != null) {
              redundantAssumedValues.keySet().forEach(dominancePerValue::remove);
              if (dominancePerValue.isEmpty()) {
                assumedValues.remove(instruction);
              }
            }
          });
    }

    void removeIf(TriPredicate<Instruction, Value, AssumedValueInfo> predicate) {
      Iterator<Entry<Instruction, Map<Value, AssumedValueInfo>>> outerIterator =
          assumedValues.entrySet().iterator();
      while (outerIterator.hasNext()) {
        Entry<Instruction, Map<Value, AssumedValueInfo>> outerEntry = outerIterator.next();
        Instruction instruction = outerEntry.getKey();
        Map<Value, AssumedValueInfo> dominancePerValue = outerEntry.getValue();
        Iterator<Entry<Value, AssumedValueInfo>> innerIterator =
            dominancePerValue.entrySet().iterator();
        while (innerIterator.hasNext()) {
          Entry<Value, AssumedValueInfo> innerEntry = innerIterator.next();
          Value assumedValue = innerEntry.getKey();
          AssumedValueInfo assumedValueInfo = innerEntry.getValue();
          if (predicate.test(instruction, assumedValue, assumedValueInfo)) {
            innerIterator.remove();
          }
        }
        if (dominancePerValue.isEmpty()) {
          outerIterator.remove();
        }
      }
    }

    static class Builder {

      private final Map<Instruction, Map<Value, AssumedValueInfo>> assumedValues =
          new LinkedHashMap<>();

      // Used to avoid unnecessary block splitting during phase 1.
      private final Set<Value> nonNullValuesKnownToDominateAllUsers = Sets.newIdentityHashSet();

      private void updateAssumedValueInfo(
          Instruction instruction,
          Value assumedValue,
          AssumedDominance dominance,
          Consumer<AssumedValueInfo> consumer) {
        AssumedValueInfo assumedValueInfo =
            assumedValues
                .computeIfAbsent(instruction, ignore -> new LinkedHashMap<>())
                .computeIfAbsent(assumedValue, ignore -> new AssumedValueInfo(dominance));
        consumer.accept(assumedValueInfo);
        if (dominance.isEverything() && assumedValueInfo.isNonNull()) {
          nonNullValuesKnownToDominateAllUsers.add(assumedValue);
        }
      }

      void addAssumedValueKnownToDominateAllUsers(
          Instruction instruction, Value assumedValue, DynamicTypeWithUpperBound dynamicType) {
        updateAssumedValueInfo(
            instruction,
            assumedValue,
            AssumedDominance.everything(),
            assumedValueInfo -> assumedValueInfo.setDynamicType(dynamicType));
      }

      void addNonNullValueKnownToDominateAllUsers(Instruction instruction, Value nonNullValue) {
        updateAssumedValueInfo(
            instruction, nonNullValue, AssumedDominance.everything(), AssumedValueInfo::setNotNull);
      }

      void addNonNullValueWithUnknownDominance(Instruction instruction, Value nonNullValue) {
        updateAssumedValueInfo(
            instruction, nonNullValue, AssumedDominance.unknown(), AssumedValueInfo::setNotNull);
      }

      public boolean isMaybeNullAndNotNullType(Value value) {
        return !nonNullValuesKnownToDominateAllUsers.contains(value)
            && !value.getType().isNullType();
      }

      public AssumedValues build() {
        return new AssumedValues(assumedValues);
      }
    }
  }

  abstract static class AssumedDominance {

    boolean isEverything() {
      return false;
    }

    boolean isEverythingElse() {
      return false;
    }

    boolean isNothing() {
      return false;
    }

    boolean isSomething() {
      return false;
    }

    SomethingAssumedDominance asSomething() {
      return null;
    }

    boolean isUnknown() {
      return false;
    }

    public static Builder builder(Value assumedValue) {
      return new Builder(assumedValue);
    }

    public static EverythingAssumedDominance everything() {
      return EverythingAssumedDominance.getInstance();
    }

    public static EverythingElseAssumedDominance everythingElse() {
      return EverythingElseAssumedDominance.getInstance();
    }

    public static NothingAssumedDominance nothing() {
      return NothingAssumedDominance.getInstance();
    }

    public static UnknownAssumedDominance redundant() {
      return unknown();
    }

    public static SomethingAssumedDominance something(
        Set<Instruction> dominatedUsers, Map<Phi, IntList> dominatedPhiUsers) {
      return new SomethingAssumedDominance(dominatedUsers, dominatedPhiUsers);
    }

    public static UnknownAssumedDominance unknown() {
      return UnknownAssumedDominance.getInstance();
    }

    static class Builder {

      private final Value assumedValue;

      private final Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
      private final Map<Phi, IntList> dominatedPhiUsers = new IdentityHashMap<>();

      private Builder(Value assumedValue) {
        this.assumedValue = assumedValue;
      }

      void addDominatedUser(Instruction user) {
        assert assumedValue.uniqueUsers().contains(user);
        assert !dominatedUsers.contains(user);
        dominatedUsers.add(user);
      }

      void addDominatedPhiUser(Phi user, IntList dominatedPredecessorIndices) {
        assert assumedValue.uniquePhiUsers().contains(user);
        assert !dominatedPhiUsers.containsKey(user);
        dominatedPhiUsers.put(user, dominatedPredecessorIndices);
      }

      AssumedDominance build() {
        if (dominatedUsers.isEmpty() && dominatedPhiUsers.isEmpty()) {
          return nothing();
        }
        assert dominatedUsers.size() < assumedValue.uniqueUsers().size()
            || dominatedPhiUsers.size() < assumedValue.uniquePhiUsers().size();
        return something(dominatedUsers, dominatedPhiUsers);
      }
    }
  }

  static class EverythingAssumedDominance extends AssumedDominance {

    private static final EverythingAssumedDominance INSTANCE = new EverythingAssumedDominance();

    private EverythingAssumedDominance() {}

    public static EverythingAssumedDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isEverything() {
      return true;
    }
  }

  static class EverythingElseAssumedDominance extends AssumedDominance {

    private static final EverythingElseAssumedDominance INSTANCE =
        new EverythingElseAssumedDominance();

    private EverythingElseAssumedDominance() {}

    public static EverythingElseAssumedDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isEverythingElse() {
      return true;
    }
  }

  static class NothingAssumedDominance extends AssumedDominance {

    private static final NothingAssumedDominance INSTANCE = new NothingAssumedDominance();

    private NothingAssumedDominance() {}

    public static NothingAssumedDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isNothing() {
      return true;
    }
  }

  static class SomethingAssumedDominance extends AssumedDominance {

    private final Set<Instruction> dominatedUsers;
    private final Map<Phi, IntList> dominatedPhiUsers;

    SomethingAssumedDominance(
        Set<Instruction> dominatedUsers, Map<Phi, IntList> dominatedPhiUsers) {
      this.dominatedUsers = dominatedUsers;
      this.dominatedPhiUsers = dominatedPhiUsers;
    }

    public Set<Instruction> getDominatedUsers() {
      return dominatedUsers;
    }

    public Map<Phi, IntList> getDominatedPhiUsers() {
      return dominatedPhiUsers;
    }

    @Override
    boolean isSomething() {
      return true;
    }

    @Override
    SomethingAssumedDominance asSomething() {
      return this;
    }
  }

  static class UnknownAssumedDominance extends AssumedDominance {

    private static final UnknownAssumedDominance INSTANCE = new UnknownAssumedDominance();

    private UnknownAssumedDominance() {}

    public static UnknownAssumedDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isUnknown() {
      return true;
    }
  }
}
