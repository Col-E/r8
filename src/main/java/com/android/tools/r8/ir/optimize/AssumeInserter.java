// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.Assume.NonNullAssumption;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
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
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TriConsumer;
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
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class AssumeInserter implements Assumer {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public AssumeInserter(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public void insertAssumeInstructions(IRCode code, Timing timing) {
    insertAssumeInstructionsInBlocks(code, code.listIterator(), alwaysTrue(), timing);
  }

  @Override
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
    Map<Instruction, Set<Value>> redundantKeys =
        computeDominanceForAssumedValues(code, assumedValues);
    timing.end();
    if (assumedValues.isEmpty()) {
      return;
    }

    timing.begin("Part 4: Remove redundant dominated assume instructions");
    removeRedundantDominatedAssumeInstructions(assumedValues, redundantKeys);
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
        if (assumedValuesBuilder.isMaybeNull(inValue)
            && isNullableReferenceTypeWithOtherNonDebugUsers(inValue, current)) {
          assumedValuesBuilder.addNonNullValueWithUnknownDominance(current, inValue);
          needsAssumeInstruction = true;
        }
      }

      Value outValue = current.outValue();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        if (invoke.hasOutValue() || !invoke.getInvokedMethod().proto.parameters.isEmpty()) {
          // Case (2) and (3).
          needsAssumeInstruction |=
              computeAssumedValuesFromSingleTarget(code, invoke, assumedValuesBuilder);
        }
      } else if (current.isFieldGet()) {
        // Case (4), field-get instructions that are guaranteed to read a non-null value.
        FieldInstruction fieldInstruction = current.asFieldInstruction();
        DexField field = fieldInstruction.getField();
        if (isNullableReferenceTypeWithNonDebugUsers(outValue)) {
          DexEncodedField encodedField = appView.appInfo().resolveField(field).getResolvedField();
          if (encodedField != null) {
            FieldOptimizationInfo optimizationInfo = encodedField.getOptimizationInfo();
            if (optimizationInfo.getDynamicUpperBoundType() != null
                && optimizationInfo.getDynamicUpperBoundType().isDefinitelyNotNull()) {
              assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(current, outValue);
              needsAssumeInstruction = true;
            }
          }
        }
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
      if (assumedValuesBuilder.isMaybeNull(lhs)
          && isNullableReferenceTypeWithOtherNonDebugUsers(lhs, ifInstruction)
          && ifInstruction.targetFromNonNullObject().getPredecessors().size() == 1) {
        assumedValuesBuilder.addNonNullValueWithUnknownDominance(ifInstruction, lhs);
      }
    }
  }

  private boolean computeAssumedValuesFromSingleTarget(
      IRCode code, InvokeMethod invoke, AssumedValues.Builder assumedValuesBuilder) {
    DexEncodedMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
    if (singleTarget == null) {
      return false;
    }

    boolean needsAssumeInstruction = false;
    MethodOptimizationInfo optimizationInfo = singleTarget.getOptimizationInfo();

    // Case (2), invocations that are guaranteed to return a non-null value.
    Value outValue = invoke.outValue();
    if (outValue != null
        && optimizationInfo.neverReturnsNull()
        && isNullableReferenceTypeWithNonDebugUsers(outValue)) {
      assumedValuesBuilder.addNonNullValueKnownToDominateAllUsers(invoke, outValue);
      needsAssumeInstruction = true;
    }

    // Case (3), parameters that are not null after the invocation.
    BitSet nonNullParamOnNormalExits = optimizationInfo.getNonNullParamOnNormalExits();
    if (nonNullParamOnNormalExits != null) {
      int start = invoke.isInvokeMethodWithReceiver() ? 1 : 0;
      for (int i = start; i < invoke.arguments().size(); i++) {
        if (nonNullParamOnNormalExits.get(i)) {
          Value argument = invoke.getArgument(i);
          if (assumedValuesBuilder.isMaybeNull(argument)
              && isNullableReferenceTypeWithOtherNonDebugUsers(argument, invoke)) {
            assumedValuesBuilder.addNonNullValueWithUnknownDominance(invoke, argument);
            needsAssumeInstruction = true;
          }
        }
      }
    }
    return needsAssumeInstruction;
  }

  private void removeRedundantAssumeInstructions(AssumedValues assumedValues) {
    assumedValues.removeIf(
        (instruction, assumedValue) -> {
          if (assumedValue.isPhi()) {
            return false;
          }
          Instruction definition = assumedValue.definition;
          return definition != instruction && assumedValues.contains(definition, assumedValue);
        });
  }

  private Map<Instruction, Set<Value>> computeDominanceForAssumedValues(
      IRCode code, AssumedValues assumedValues) {
    Map<Instruction, Set<Value>> redundantKeys = new IdentityHashMap<>();
    LazyDominatorTree lazyDominatorTree = new LazyDominatorTree(code);
    Map<BasicBlock, Set<BasicBlock>> dominatedBlocksCache = new IdentityHashMap<>();
    assumedValues.computeDominance(
        (instruction, assumedValue) -> {
          Set<Value> alreadyAssumedValues = redundantKeys.get(instruction);
          if (alreadyAssumedValues != null && alreadyAssumedValues.contains(assumedValue)) {
            // Returning redundant() will cause the entry (instruction, assumedValue) to be removed.
            return AssumedDominance.redundant();
          }

          // If this value is non-null since its definition, then it is known to dominate all users.
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
              redundantKeys
                  .computeIfAbsent(user, ignore -> Sets.newIdentityHashSet())
                  .add(assumedValue);
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
    return redundantKeys;
  }

  private void removeRedundantDominatedAssumeInstructions(
      AssumedValues assumedValues, Map<Instruction, Set<Value>> redundantKeys) {
    assumedValues.removeAll(redundantKeys);
  }

  private void materializeAssumeInstructions(IRCode code, AssumedValues assumedValues) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Map<BasicBlock, Map<Instruction, List<Instruction>>> pendingInsertions =
        new IdentityHashMap<>();
    assumedValues.forEach(
        (instruction, assumedValue, assumedValueInfo) -> {
          BasicBlock block = instruction.getBlock();
          BasicBlock insertionBlock = getInsertionBlock(instruction);

          AssumedDominance dominance = assumedValueInfo.getDominance();
          Value newValue =
              code.createValue(
                  assumedValue.getType().asReferenceType().asMeetWithNotNull(),
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
                          assert operand.isDefinedByInstructionSatisfying(
                              Instruction::isAssumeNonNull);
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

          Assume assumeInstruction =
              Assume.createAssumeNonNullInstruction(newValue, assumedValue, instruction, appView);
          assumeInstruction.setPosition(instruction.getPosition());
          if (insertionBlock != block) {
            insertionBlock.listIterator(code).add(assumeInstruction);
          } else {
            pendingInsertions
                .computeIfAbsent(block, ignore -> new IdentityHashMap<>())
                .computeIfAbsent(instruction, ignore -> new ArrayList<>())
                .add(assumeInstruction);
          }
        });
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
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
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
    return type.isReferenceType() && type.asReferenceType().isNullable();
  }

  private static boolean isNullableReferenceTypeWithNonDebugUsers(Value value) {
    return isNullableReferenceType(value) && value.numberOfAllNonDebugUsers() > 0;
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
    NonNullAssumption nonNullAssumption;

    AssumedValueInfo(AssumedDominance dominance) {
      this.dominance = dominance;
    }

    AssumedDominance getDominance() {
      return dominance;
    }

    void setDominance(AssumedDominance dominance) {
      this.dominance = dominance;
    }

    void setNotNull() {
      nonNullAssumption = NonNullAssumption.get();
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

    void computeDominance(BiFunction<Instruction, Value, AssumedDominance> function) {
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
          dominance = function.apply(instruction, assumedValue);
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

    boolean contains(Instruction instruction, Value assumedValue) {
      Map<Value, AssumedValueInfo> dominancePerValue = assumedValues.get(instruction);
      return dominancePerValue != null && dominancePerValue.containsKey(assumedValue);
    }

    boolean isEmpty() {
      return assumedValues.isEmpty();
    }

    void forEach(TriConsumer<Instruction, Value, AssumedValueInfo> consumer) {
      assumedValues.forEach(
          (instruction, dominancePerValue) ->
              dominancePerValue.forEach(
                  (assumedValue, assumedValueInfo) ->
                      consumer.accept(instruction, assumedValue, assumedValueInfo)));
    }

    void removeAll(Map<Instruction, Set<Value>> keys) {
      keys.forEach(
          (instruction, values) -> {
            Map<Value, AssumedValueInfo> dominancePerValue = assumedValues.get(instruction);
            if (dominancePerValue != null) {
              values.forEach(dominancePerValue::remove);
              if (dominancePerValue.isEmpty()) {
                assumedValues.remove(instruction);
              }
            }
          });
    }

    void removeIf(BiPredicate<Instruction, Value> predicate) {
      Iterator<Entry<Instruction, Map<Value, AssumedValueInfo>>> outerIterator =
          assumedValues.entrySet().iterator();
      while (outerIterator.hasNext()) {
        Entry<Instruction, Map<Value, AssumedValueInfo>> outerEntry = outerIterator.next();
        Instruction instruction = outerEntry.getKey();
        Map<Value, AssumedValueInfo> dominancePerValue = outerEntry.getValue();
        Iterator<Entry<Value, AssumedValueInfo>> innerIterator =
            dominancePerValue.entrySet().iterator();
        while (innerIterator.hasNext()) {
          Value assumedValue = innerIterator.next().getKey();
          if (predicate.test(instruction, assumedValue)) {
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
      private final Set<Value> assumedValuesKnownToDominateAllUsers = Sets.newIdentityHashSet();

      private void addNonNullValue(
          Instruction instruction, Value nonNullValue, AssumedDominance dominance) {
        assumedValues
            .computeIfAbsent(instruction, ignore -> new LinkedHashMap<>())
            .computeIfAbsent(nonNullValue, ignore -> new AssumedValueInfo(dominance))
            .setNotNull();
        if (dominance.isEverything()) {
          assumedValuesKnownToDominateAllUsers.add(nonNullValue);
        }
      }

      void addNonNullValueKnownToDominateAllUsers(Instruction instruction, Value nonNullValue) {
        addNonNullValue(instruction, nonNullValue, AssumedDominance.everything());
      }

      void addNonNullValueWithUnknownDominance(Instruction instruction, Value nonNullValue) {
        addNonNullValue(instruction, nonNullValue, AssumedDominance.unknown());
      }

      public boolean isMaybeNull(Value value) {
        return !assumedValuesKnownToDominateAllUsers.contains(value);
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
