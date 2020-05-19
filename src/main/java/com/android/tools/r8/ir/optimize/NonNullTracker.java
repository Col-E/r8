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

public class NonNullTracker implements Assumer {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public NonNullTracker(AppView<? extends AppInfoWithClassHierarchy> appView) {
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
    timing.begin("Insert assume not null instructions");
    internalInsertAssumeInstructionsInBlocks(code, blockIterator, blockTester, timing);
    timing.end();
  }

  private void internalInsertAssumeInstructionsInBlocks(
      IRCode code,
      BasicBlockIterator blockIterator,
      Predicate<BasicBlock> blockTester,
      Timing timing) {
    timing.begin("Part 1: Compute non null values");
    NonNullValues nonNullValues = computeNonNullValues(code, blockIterator, blockTester);
    timing.end();
    if (nonNullValues.isEmpty()) {
      return;
    }

    timing.begin("Part 2: Remove redundant assume instructions");
    removeRedundantAssumeInstructions(nonNullValues);
    timing.end();

    timing.begin("Part 3: Compute dominated users");
    Map<Instruction, Set<Value>> redundantKeys =
        computeDominanceForNonNullValues(code, nonNullValues);
    timing.end();
    if (nonNullValues.isEmpty()) {
      return;
    }

    timing.begin("Part 4: Remove redundant dominated assume instructions");
    removeRedundantDominatedAssumeInstructions(nonNullValues, redundantKeys);
    timing.end();
    if (nonNullValues.isEmpty()) {
      return;
    }

    timing.begin("Part 5: Materialize assume instructions");
    materializeAssumeInstructions(code, nonNullValues);
    timing.end();
  }

  private NonNullValues computeNonNullValues(
      IRCode code, BasicBlockIterator blockIterator, Predicate<BasicBlock> blockTester) {
    NonNullValues.Builder nonNullValuesBuilder = new NonNullValues.Builder();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blockTester.test(block)) {
        computeNonNullValuesInBlock(code, blockIterator, block, nonNullValuesBuilder);
      }
    }
    return nonNullValuesBuilder.build();
  }

  private void computeNonNullValuesInBlock(
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      NonNullValues.Builder nonNullValuesBuilder) {
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
        if (nonNullValuesBuilder.isMaybeNull(inValue)
            && isNullableReferenceTypeWithOtherNonDebugUsers(inValue, current)) {
          nonNullValuesBuilder.addNonNullValueWithUnknownDominance(current, inValue);
          needsAssumeInstruction = true;
        }
      }

      Value outValue = current.outValue();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        if (invoke.hasOutValue() || !invoke.getInvokedMethod().proto.parameters.isEmpty()) {
          // Case (2) and (3).
          needsAssumeInstruction |=
              computeNonNullValuesFromSingleTarget(code, invoke, nonNullValuesBuilder);
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
              nonNullValuesBuilder.addNonNullValueKnownToDominateAllUsers(current, outValue);
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
          computeNonNullValuesInBlock(code, blockIterator, insertionBlock, nonNullValuesBuilder);
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
      if (nonNullValuesBuilder.isMaybeNull(lhs)
          && isNullableReferenceTypeWithOtherNonDebugUsers(lhs, ifInstruction)
          && ifInstruction.targetFromNonNullObject().getPredecessors().size() == 1) {
        nonNullValuesBuilder.addNonNullValueWithUnknownDominance(ifInstruction, lhs);
      }
    }
  }

  private boolean computeNonNullValuesFromSingleTarget(
      IRCode code, InvokeMethod invoke, NonNullValues.Builder nonNullValuesBuilder) {
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
      nonNullValuesBuilder.addNonNullValueKnownToDominateAllUsers(invoke, outValue);
      needsAssumeInstruction = true;
    }

    // Case (3), parameters that are not null after the invocation.
    BitSet nonNullParamOnNormalExits = optimizationInfo.getNonNullParamOnNormalExits();
    if (nonNullParamOnNormalExits != null) {
      int start = invoke.isInvokeMethodWithReceiver() ? 1 : 0;
      for (int i = start; i < invoke.arguments().size(); i++) {
        if (nonNullParamOnNormalExits.get(i)) {
          Value argument = invoke.getArgument(i);
          if (nonNullValuesBuilder.isMaybeNull(argument)
              && isNullableReferenceTypeWithOtherNonDebugUsers(argument, invoke)) {
            nonNullValuesBuilder.addNonNullValueWithUnknownDominance(invoke, argument);
            needsAssumeInstruction = true;
          }
        }
      }
    }
    return needsAssumeInstruction;
  }

  private void removeRedundantAssumeInstructions(NonNullValues nonNullValues) {
    nonNullValues.removeIf(
        (instruction, nonNullValue) -> {
          if (nonNullValue.isPhi()) {
            return false;
          }
          Instruction definition = nonNullValue.definition;
          return definition != instruction && nonNullValues.contains(definition, nonNullValue);
        });
  }

  private Map<Instruction, Set<Value>> computeDominanceForNonNullValues(
      IRCode code, NonNullValues nonNullValues) {
    Map<Instruction, Set<Value>> redundantKeys = new IdentityHashMap<>();
    LazyDominatorTree lazyDominatorTree = new LazyDominatorTree(code);
    Map<BasicBlock, Set<BasicBlock>> dominatedBlocksCache = new IdentityHashMap<>();
    nonNullValues.computeDominance(
        (instruction, nonNullValue) -> {
          Set<Value> alreadyNonNullValues = redundantKeys.get(instruction);
          if (alreadyNonNullValues != null && alreadyNonNullValues.contains(nonNullValue)) {
            // Returning redundant() will cause the entry (instruction, nonNullValue) to be removed.
            return NonNullDominance.redundant();
          }

          // If this value is non-null since its definition, then it is known to dominate all users.
          if (nonNullValue == instruction.outValue()) {
            return NonNullDominance.everything();
          }

          // If we learn that this value is known to be non-null in the same block as it is defined,
          // and it is not used between its definition and the instruction that performs the null
          // check, then the non-null-value is known to dominate all other users than the null check
          // itself.
          BasicBlock block = instruction.getBlock();
          if (nonNullValue.getBlock() == block
              && block.exit().isGoto()
              && !instruction.getBlock().hasCatchHandlers()) {
            InstructionIterator iterator = instruction.getBlock().iterator();
            if (!nonNullValue.isPhi()) {
              iterator.nextUntil(x -> x != nonNullValue.definition);
              iterator.previous();
            }
            boolean isUsedBeforeInstruction = false;
            while (iterator.hasNext()) {
              Instruction current = iterator.next();
              if (current == instruction) {
                break;
              }
              if (current.inValues().contains(nonNullValue)
                  || current.getDebugValues().contains(nonNullValue)) {
                isUsedBeforeInstruction = true;
                break;
              }
            }
            if (!isUsedBeforeInstruction) {
              return NonNullDominance.everythingElse();
            }
          }

          // Otherwise, we need a dominator tree to determine which users are dominated.
          BasicBlock insertionBlock = getInsertionBlock(instruction);

          assert nonNullValue.hasPhiUsers()
              || nonNullValue.uniqueUsers().stream().anyMatch(user -> user != instruction)
              || nonNullValue.isArgument();

          // Find all users of the original value that are dominated by either the current block
          // or the new split-off block. Since NPE can be explicitly caught, nullness should be
          // propagated through dominance.
          DominatorTree dominatorTree = lazyDominatorTree.get();
          Set<BasicBlock> dominatedBlocks =
              dominatedBlocksCache.computeIfAbsent(
                  insertionBlock, x -> dominatorTree.dominatedBlocks(x, Sets.newIdentityHashSet()));

          NonNullDominance.Builder dominance = NonNullDominance.builder(nonNullValue);
          for (Instruction user : nonNullValue.uniqueUsers()) {
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
                  .add(nonNullValue);
            }
          }
          for (Phi user : nonNullValue.uniquePhiUsers()) {
            IntList dominatedPredecessorIndices =
                findDominatedPredecessorIndexesInPhi(user, nonNullValue, dominatedBlocks);
            if (!dominatedPredecessorIndices.isEmpty()) {
              dominance.addDominatedPhiUser(user, dominatedPredecessorIndices);
            }
          }
          return dominance.build();
        });
    return redundantKeys;
  }

  private void removeRedundantDominatedAssumeInstructions(
      NonNullValues nonNullValues, Map<Instruction, Set<Value>> redundantKeys) {
    nonNullValues.removeAll(redundantKeys);
  }

  private void materializeAssumeInstructions(IRCode code, NonNullValues nonNullValues) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Map<BasicBlock, Map<Instruction, List<Instruction>>> pendingInsertions =
        new IdentityHashMap<>();
    nonNullValues.forEach(
        (instruction, nonNullValue, dominance) -> {
          BasicBlock block = instruction.getBlock();
          BasicBlock insertionBlock = getInsertionBlock(instruction);

          Value newValue =
              code.createValue(
                  nonNullValue.getType().asReferenceType().asMeetWithNotNull(),
                  nonNullValue.getLocalInfo());
          if (dominance.isEverything()) {
            nonNullValue.replaceUsers(newValue);
          } else if (dominance.isEverythingElse()) {
            nonNullValue.replaceSelectiveInstructionUsers(newValue, user -> user != instruction);
            nonNullValue.replacePhiUsers(newValue);
          } else if (dominance.isSomething()) {
            SomethingNonNullDominance somethingDominance = dominance.asSomething();
            somethingDominance
                .getDominatedPhiUsers()
                .forEach(
                    (user, indices) -> {
                      IntListIterator iterator = indices.iterator();
                      while (iterator.hasNext()) {
                        Value operand = user.getOperand(iterator.nextInt());
                        if (operand != nonNullValue) {
                          assert operand.isDefinedByInstructionSatisfying(
                              Instruction::isAssumeNonNull);
                          iterator.remove();
                        }
                      }
                    });
            nonNullValue.replaceSelectiveUsers(
                newValue,
                somethingDominance.getDominatedUsers(),
                somethingDominance.getDominatedPhiUsers());
          }
          affectedValues.addAll(newValue.affectedValues());

          Assume assumeInstruction =
              Assume.createAssumeNonNullInstruction(newValue, nonNullValue, instruction, appView);
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
      Phi user, Value knownToBeNonNullValue, Set<BasicBlock> dominatedBlocks) {
    assert user.getOperands().contains(knownToBeNonNullValue);
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
      if (operand == knownToBeNonNullValue && dominatedBlocks.contains(predecessor)) {
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

  static class NonNullValues {

    /**
     * A mapping from each instruction to the (in and out) values that are guaranteed to be non-null
     * by the instruction. Each non-null value is subsequently mapped to the set of users that it
     * dominates.
     */
    Map<Instruction, Map<Value, NonNullDominance>> nonNullValues;

    public NonNullValues(Map<Instruction, Map<Value, NonNullDominance>> nonNullValues) {
      this.nonNullValues = nonNullValues;
    }

    public static Builder builder() {
      return new Builder();
    }

    void computeDominance(BiFunction<Instruction, Value, NonNullDominance> function) {
      Iterator<Entry<Instruction, Map<Value, NonNullDominance>>> outerIterator =
          nonNullValues.entrySet().iterator();
      while (outerIterator.hasNext()) {
        Entry<Instruction, Map<Value, NonNullDominance>> outerEntry = outerIterator.next();
        Instruction instruction = outerEntry.getKey();
        Map<Value, NonNullDominance> dominancePerValue = outerEntry.getValue();
        Iterator<Entry<Value, NonNullDominance>> innerIterator =
            dominancePerValue.entrySet().iterator();
        while (innerIterator.hasNext()) {
          Entry<Value, NonNullDominance> innerEntry = innerIterator.next();
          Value nonNullValue = innerEntry.getKey();
          NonNullDominance dominance = innerEntry.getValue();
          if (dominance.isEverything()) {
            assert nonNullValue.isDefinedByInstructionSatisfying(
                definition -> definition.outValue() == nonNullValue);
            continue;
          }
          assert dominance.isUnknown();
          dominance = function.apply(instruction, nonNullValue);
          if ((dominance.isNothing() && !nonNullValue.isArgument()) || dominance.isUnknown()) {
            innerIterator.remove();
          } else {
            innerEntry.setValue(dominance);
          }
        }
        if (dominancePerValue.isEmpty()) {
          outerIterator.remove();
        }
      }
    }

    boolean contains(Instruction instruction, Value nonNullValue) {
      Map<Value, NonNullDominance> dominancePerValue = nonNullValues.get(instruction);
      return dominancePerValue != null && dominancePerValue.containsKey(nonNullValue);
    }

    boolean isEmpty() {
      return nonNullValues.isEmpty();
    }

    void forEach(TriConsumer<Instruction, Value, NonNullDominance> consumer) {
      nonNullValues.forEach(
          (instruction, dominancePerValue) ->
              dominancePerValue.forEach(
                  (nonNullValue, dominance) ->
                      consumer.accept(instruction, nonNullValue, dominance)));
    }

    void removeAll(Map<Instruction, Set<Value>> keys) {
      keys.forEach(
          (instruction, values) -> {
            Map<Value, NonNullDominance> dominancePerValue = nonNullValues.get(instruction);
            if (dominancePerValue != null) {
              values.forEach(dominancePerValue::remove);
              if (dominancePerValue.isEmpty()) {
                nonNullValues.remove(instruction);
              }
            }
          });
    }

    void removeIf(BiPredicate<Instruction, Value> predicate) {
      Iterator<Entry<Instruction, Map<Value, NonNullDominance>>> outerIterator =
          nonNullValues.entrySet().iterator();
      while (outerIterator.hasNext()) {
        Entry<Instruction, Map<Value, NonNullDominance>> outerEntry = outerIterator.next();
        Instruction instruction = outerEntry.getKey();
        Map<Value, NonNullDominance> dominancePerValue = outerEntry.getValue();
        Iterator<Entry<Value, NonNullDominance>> innerIterator =
            dominancePerValue.entrySet().iterator();
        while (innerIterator.hasNext()) {
          Value nonNullValue = innerIterator.next().getKey();
          if (predicate.test(instruction, nonNullValue)) {
            innerIterator.remove();
          }
        }
        if (dominancePerValue.isEmpty()) {
          outerIterator.remove();
        }
      }
    }

    static class Builder {

      private final Map<Instruction, Map<Value, NonNullDominance>> nonNullValues =
          new LinkedHashMap<>();

      // Used to avoid unnecessary block splitting during phase 1.
      private final Set<Value> nonNullValuesKnownToDominateAllUsers = Sets.newIdentityHashSet();

      private void add(Instruction instruction, Value nonNullValue, NonNullDominance dominance) {
        nonNullValues
            .computeIfAbsent(instruction, ignore -> new LinkedHashMap<>())
            .put(nonNullValue, dominance);
        if (dominance.isEverything()) {
          nonNullValuesKnownToDominateAllUsers.add(nonNullValue);
        }
      }

      void addNonNullValueKnownToDominateAllUsers(Instruction instruction, Value nonNullValue) {
        add(instruction, nonNullValue, NonNullDominance.everything());
      }

      void addNonNullValueWithUnknownDominance(Instruction instruction, Value nonNullValue) {
        add(instruction, nonNullValue, NonNullDominance.unknown());
      }

      public boolean isMaybeNull(Value value) {
        return !nonNullValuesKnownToDominateAllUsers.contains(value);
      }

      public NonNullValues build() {
        return new NonNullValues(nonNullValues);
      }
    }
  }

  abstract static class NonNullDominance {

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

    SomethingNonNullDominance asSomething() {
      return null;
    }

    boolean isUnknown() {
      return false;
    }

    public static Builder builder(Value nonNullValue) {
      return new Builder(nonNullValue);
    }

    public static EverythingNonNullDominance everything() {
      return EverythingNonNullDominance.getInstance();
    }

    public static EverythingElseNonNullDominance everythingElse() {
      return EverythingElseNonNullDominance.getInstance();
    }

    public static NothingNonNullDominance nothing() {
      return NothingNonNullDominance.getInstance();
    }

    public static UnknownNonNullDominance redundant() {
      return unknown();
    }

    public static SomethingNonNullDominance something(
        Set<Instruction> dominatedUsers, Map<Phi, IntList> dominatedPhiUsers) {
      return new SomethingNonNullDominance(dominatedUsers, dominatedPhiUsers);
    }

    public static UnknownNonNullDominance unknown() {
      return UnknownNonNullDominance.getInstance();
    }

    static class Builder {

      private final Value nonNullValue;

      private final Set<Instruction> dominatedUsers = Sets.newIdentityHashSet();
      private final Map<Phi, IntList> dominatedPhiUsers = new IdentityHashMap<>();

      private Builder(Value nonNullValue) {
        this.nonNullValue = nonNullValue;
      }

      void addDominatedUser(Instruction user) {
        assert nonNullValue.uniqueUsers().contains(user);
        assert !dominatedUsers.contains(user);
        dominatedUsers.add(user);
      }

      void addDominatedPhiUser(Phi user, IntList dominatedPredecessorIndices) {
        assert nonNullValue.uniquePhiUsers().contains(user);
        assert !dominatedPhiUsers.containsKey(user);
        dominatedPhiUsers.put(user, dominatedPredecessorIndices);
      }

      NonNullDominance build() {
        if (dominatedUsers.isEmpty() && dominatedPhiUsers.isEmpty()) {
          return nothing();
        }
        assert dominatedUsers.size() < nonNullValue.uniqueUsers().size()
            || dominatedPhiUsers.size() < nonNullValue.uniquePhiUsers().size();
        return something(dominatedUsers, dominatedPhiUsers);
      }
    }
  }

  static class EverythingNonNullDominance extends NonNullDominance {

    private static final EverythingNonNullDominance INSTANCE = new EverythingNonNullDominance();

    private EverythingNonNullDominance() {}

    public static EverythingNonNullDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isEverything() {
      return true;
    }
  }

  static class EverythingElseNonNullDominance extends NonNullDominance {

    private static final EverythingElseNonNullDominance INSTANCE =
        new EverythingElseNonNullDominance();

    private EverythingElseNonNullDominance() {}

    public static EverythingElseNonNullDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isEverythingElse() {
      return true;
    }
  }

  static class NothingNonNullDominance extends NonNullDominance {

    private static final NothingNonNullDominance INSTANCE = new NothingNonNullDominance();

    private NothingNonNullDominance() {}

    public static NothingNonNullDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isNothing() {
      return true;
    }
  }

  static class SomethingNonNullDominance extends NonNullDominance {

    private final Set<Instruction> dominatedUsers;
    private final Map<Phi, IntList> dominatedPhiUsers;

    SomethingNonNullDominance(
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
    SomethingNonNullDominance asSomething() {
      return this;
    }
  }

  static class UnknownNonNullDominance extends NonNullDominance {

    private static final UnknownNonNullDominance INSTANCE = new UnknownNonNullDominance();

    private UnknownNonNullDominance() {}

    public static UnknownNonNullDominance getInstance() {
      return INSTANCE;
    }

    @Override
    boolean isUnknown() {
      return true;
    }
  }
}
