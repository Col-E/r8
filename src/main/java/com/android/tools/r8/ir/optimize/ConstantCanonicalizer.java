// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMap.FastSortedEntrySet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonicalize constants.
 */
public class ConstantCanonicalizer {

  // Threshold to limit the number of constant canonicalization.
  private static final int MAX_CANONICALIZED_CONSTANT = 22;

  private final AppView<?> appView;
  private final CodeRewriter codeRewriter;
  private final ProgramMethod context;
  private final IRCode code;

  private OptionalBool isAccessingVolatileField = OptionalBool.unknown();
  private Set<InstanceGet> ineligibleInstanceGetInstructions;

  public ConstantCanonicalizer(
      AppView<?> appView, CodeRewriter codeRewriter, ProgramMethod context, IRCode code) {
    this.appView = appView;
    this.codeRewriter = codeRewriter;
    this.context = context;
    this.code = code;
  }

  private ConstantCanonicalizer clear() {
    isAccessingVolatileField = OptionalBool.unknown();
    ineligibleInstanceGetInstructions = null;
    return this;
  }

  private boolean getOrComputeIsAccessingVolatileField() {
    if (isAccessingVolatileField.isUnknown()) {
      isAccessingVolatileField = OptionalBool.of(computeIsAccessingVolatileField());
    }
    return isAccessingVolatileField.isTrue();
  }

  private boolean computeIsAccessingVolatileField() {
    if (!appView.hasClassHierarchy()) {
      // Conservatively return true.
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoWithClassHierarchy();
    for (FieldInstruction fieldGet :
        code.<FieldInstruction>instructions(Instruction::isFieldInstruction)) {
      SingleFieldResolutionResult<?> resolutionResult =
          appInfo.resolveField(fieldGet.getField()).asSingleFieldResolutionResult();
      if (resolutionResult == null
          || resolutionResult.getResolvedField().getAccessFlags().isVolatile()) {
        return true;
      }
    }
    return false;
  }

  private Set<InstanceGet> getOrComputeIneligibleInstanceGetInstructions() {
    if (ineligibleInstanceGetInstructions == null) {
      ineligibleInstanceGetInstructions = computeIneligibleInstanceGetInstructions();
    }
    return ineligibleInstanceGetInstructions;
  }

  private Set<InstanceGet> computeIneligibleInstanceGetInstructions() {
    Set<InstanceGet> ineligibleInstanceGetInstructions = Sets.newIdentityHashSet();
    for (BasicBlock catchHandlerBlock : computeDirectAndIndirectCatchHandlerBlocks()) {
      for (InstanceGet instanceGet :
          catchHandlerBlock.<InstanceGet>getInstructions(Instruction::isInstanceGet)) {
        // If the receiver is defined in a block with catch handlers and the definition of the
        // receiver is not throwing (typically defined by an assume instruction or a phi), then we
        // cant split the block and copy the catch handlers, since the canonicalized constant would
        // then not be defined on the exceptional edge.
        Value object = instanceGet.object();
        if (!object.isDefinedByInstructionSatisfying(Instruction::instructionTypeCanThrow)
            && object.getBlock().hasCatchHandlers()) {
          ineligibleInstanceGetInstructions.add(instanceGet);
        }
      }
    }
    return ineligibleInstanceGetInstructions;
  }

  private Set<BasicBlock> computeDirectAndIndirectCatchHandlerBlocks() {
    WorkList<BasicBlock> catchHandlerBlocks = WorkList.newIdentityWorkList();
    code.getBlocks()
        .forEach(
            block -> catchHandlerBlocks.addIfNotSeen(block.getCatchHandlers().getAllTargets()));
    while (catchHandlerBlocks.hasNext()) {
      BasicBlock block = catchHandlerBlocks.next();
      catchHandlerBlocks.addIfNotSeen(block.getSuccessors());
    }
    return catchHandlerBlocks.getSeenSet();
  }

  public ConstantCanonicalizer canonicalize() {
    Object2ObjectLinkedOpenCustomHashMap<Instruction, List<Instruction>> valuesDefinedByConstant =
        new Object2ObjectLinkedOpenCustomHashMap<>(
            new Strategy<>() {

              @Override
              public int hashCode(Instruction candidate) {
                assert candidate.instructionTypeCanBeCanonicalized();
                switch (candidate.opcode()) {
                  case CONST_CLASS:
                    return candidate.asConstClass().getValue().hashCode();
                  case CONST_NUMBER:
                    return Long.hashCode(candidate.asConstNumber().getRawValue())
                        + 13 * candidate.outType().hashCode();
                  case CONST_STRING:
                    return candidate.asConstString().getValue().hashCode();
                  case DEX_ITEM_BASED_CONST_STRING:
                    return candidate.asDexItemBasedConstString().getItem().hashCode();
                  case INSTANCE_GET:
                  case STATIC_GET:
                    return candidate.asFieldGet().getField().hashCode();
                  default:
                    throw new Unreachable();
                }
              }

              @Override
              public boolean equals(Instruction a, Instruction b) {
                if (a == b) {
                  return true;
                }
                if (a == null || b == null || a.getClass() != b.getClass()) {
                  return false;
                }
                if (a.isInstanceGet() && a.getFirstOperand() != b.getFirstOperand()) {
                  return false;
                }
                return a.identicalNonValueNonPositionParts(b);
              }
            });

    // Collect usages of constants that can be canonicalized.
    for (Instruction instruction : code.instructions()) {
      if (isConstantCanonicalizationCandidate(instruction)) {
        valuesDefinedByConstant
            .computeIfAbsent(instruction, ignoreKey(ArrayList::new))
            .add(instruction);
      }
    }

    if (valuesDefinedByConstant.isEmpty()) {
      return clear();
    }

    // Double-check the entry block does not have catch handlers.
    // Otherwise, we need to split it before moving canonicalized const-string, which may throw.
    assert !code.entryBlock().hasCatchHandlers();
    FastSortedEntrySet<Instruction, List<Instruction>> entries =
        valuesDefinedByConstant.object2ObjectEntrySet();
    // Sort the most frequently used constant first and exclude constant use only one time, such
    // as the {@code MAX_CANONICALIZED_CONSTANT} will be canonicalized into the entry block.
    Iterator<Object2ObjectMap.Entry<Instruction, List<Instruction>>> iterator =
        entries.stream()
            .filter(a -> a.getValue().size() > 1)
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .limit(MAX_CANONICALIZED_CONSTANT)
            .iterator();
    if (!iterator.hasNext()) {
      return clear();
    }

    boolean shouldSimplifyIfs = false;

    // Insert instructions in the entry block.
    Map<InstructionOrPhi, List<Instruction>> pendingInsertions = new IdentityHashMap<>();
    do {
      Object2ObjectMap.Entry<Instruction, List<Instruction>> entry = iterator.next();
      Instruction canonicalizedConstant = entry.getKey();
      assert canonicalizedConstant.instructionTypeCanBeCanonicalized();
      Instruction newInstruction;
      if (canonicalizedConstant.getBlock().isEntry()) {
        newInstruction = canonicalizedConstant;
      } else {
        newInstruction = createMaterializingInstruction(canonicalizedConstant);
        InstructionOrPhi insertionPoint = getInsertionPointForCanonicalizedConstant(newInstruction);
        if (insertionPoint == null) {
          insertCanonicalizedConstantInEntryBlock(newInstruction);
        } else {
          // Record that this instruction needs to be inserted at the insertion position. Note that
          // the insertion position may later be moved if it is itself subject to canonicalization.
          addPendingInsertion(insertionPoint, newInstruction, pendingInsertions);
        }
      }
      // Remove the canonicalized instructions.
      for (Instruction oldInstruction : entry.getValue()) {
        if (oldInstruction != newInstruction) {
          oldInstruction.outValue().replaceUsers(newInstruction.outValue());
          oldInstruction
              .getBlock()
              .listIterator(code, oldInstruction)
              .removeOrReplaceByDebugLocalRead();

          // If the removed instruction is an insertion point for another constant, then record that
          // the constant should instead be inserted at the point where the removed instruction has
          // been moved to.
          for (Instruction pendingInsertion :
              removePendingInsertions(oldInstruction, pendingInsertions)) {
            addPendingInsertion(newInstruction, pendingInsertion, pendingInsertions);
          }
        }
      }
      shouldSimplifyIfs |= newInstruction.outValue().hasUserThatMatches(Instruction::isIf);
    } while (iterator.hasNext());

    // Insert instructions that cannot be inserted in the entry block.
    if (!pendingInsertions.isEmpty()) {
      BasicBlockIterator blockIterator = code.listIterator();
      while (blockIterator.hasNext()) {
        BasicBlock block = blockIterator.next();
        InstructionListIterator instructionIterator = block.listIterator(code);
        for (Phi insertionPoint : block.getPhis()) {
          instructionIterator =
              insertPendingInsertions(
                  blockIterator, instructionIterator, insertionPoint, pendingInsertions);
        }
        while (instructionIterator.hasNext()) {
          Instruction insertionPoint = instructionIterator.next();
          instructionIterator =
              insertPendingInsertions(
                  blockIterator, instructionIterator, insertionPoint, pendingInsertions);
        }
      }
    }

    assert pendingInsertions.isEmpty();

    shouldSimplifyIfs |= code.removeAllDeadAndTrivialPhis();

    if (shouldSimplifyIfs) {
      codeRewriter.simplifyIf(code);
    }

    assert code.isConsistentSSA(appView);
    return clear();
  }

  private void addPendingInsertion(
      InstructionOrPhi insertionPoint,
      Instruction newInstruction,
      Map<InstructionOrPhi, List<Instruction>> pendingInsertions) {
    pendingInsertions
        .computeIfAbsent(insertionPoint, ignoreKey(ArrayList::new))
        .add(newInstruction);
  }

  private InstructionListIterator insertPendingInsertions(
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InstructionOrPhi insertionPoint,
      Map<InstructionOrPhi, List<Instruction>> pendingInsertions) {
    List<Instruction> pendingInsertionsAtInsertionPoint =
        removePendingInsertions(insertionPoint, pendingInsertions);
    if (pendingInsertionsAtInsertionPoint.isEmpty()) {
      return instructionIterator;
    }
    WorkList<Instruction> worklist =
        WorkList.newIdentityWorkList(pendingInsertionsAtInsertionPoint);
    while (worklist.hasNext()) {
      Instruction newInstruction = worklist.next();
      List<Instruction> pendingInsertionsAfterNewInstruction =
          removePendingInsertions(newInstruction, pendingInsertions);
      if (pendingInsertionsAfterNewInstruction.isEmpty()) {
        instructionIterator =
            insertCanonicalizedConstantAtInsertionPoint(
                blockIterator, instructionIterator, insertionPoint, newInstruction);
      } else {
        // Process pending insertions before the current instruction to ensure the current
        // instruction ends up first in the instruction stream.
        worklist.addIfNotSeen(pendingInsertionsAfterNewInstruction);
        worklist.addIgnoringSeenSet(newInstruction);
      }
    }
    return instructionIterator;
  }

  private List<Instruction> removePendingInsertions(
      InstructionOrPhi insertionPoint, Map<InstructionOrPhi, List<Instruction>> pendingInsertions) {
    List<Instruction> pendingInstructionsAtInsertionPosition =
        pendingInsertions.remove(insertionPoint);
    return pendingInstructionsAtInsertionPosition != null
        ? pendingInstructionsAtInsertionPosition
        : Collections.emptyList();
  }

  private InstructionOrPhi getInsertionPointForCanonicalizedConstant(Instruction newInstruction) {
    switch (newInstruction.opcode()) {
      case CONST_CLASS:
      case CONST_NUMBER:
      case CONST_STRING:
      case DEX_ITEM_BASED_CONST_STRING:
      case STATIC_GET:
        // Insert in entry block.
        return null;
      case INSTANCE_GET:
        {
          InstanceGet instanceGet = newInstruction.asInstanceGet();
          Value object = instanceGet.object();
          if (object.isThis()) {
            return null;
          }
          if (object.isPhi()) {
            return object.asPhi();
          }
          Instruction definition = object.getDefinition();
          if (definition.isArgument()) {
            return code.getLastArgument();
          }
          if (definition.isNewInstance()) {
            InvokeDirect uniqueConstructorInvoke =
                definition.asNewInstance().getUniqueConstructorInvoke(appView.dexItemFactory());
            // This is guaranteed to be non-null by isConstantCanonicalizationCandidate.
            assert uniqueConstructorInvoke != null;
            return uniqueConstructorInvoke;
          }
          return definition;
        }
      default:
        throw new Unreachable();
    }
  }

  private Instruction createMaterializingInstruction(Instruction canonicalizedConstant) {
    switch (canonicalizedConstant.opcode()) {
      case CONST_CLASS:
        return ConstClass.copyOf(code, canonicalizedConstant.asConstClass());
      case CONST_NUMBER:
        return ConstNumber.copyOf(code, canonicalizedConstant.asConstNumber());
      case CONST_STRING:
        return ConstString.copyOf(code, canonicalizedConstant.asConstString());
      case DEX_ITEM_BASED_CONST_STRING:
        return DexItemBasedConstString.copyOf(
            code, canonicalizedConstant.asDexItemBasedConstString());
      case INSTANCE_GET:
        return InstanceGet.copyOf(code, canonicalizedConstant.asInstanceGet());
      case STATIC_GET:
        return StaticGet.copyOf(code, canonicalizedConstant.asStaticGet());
      default:
        throw new Unreachable();
    }
  }

  public boolean isConstantCanonicalizationCandidate(Instruction instruction) {
    // Interested only in instructions types that can be canonicalized, i.e., ConstClass,
    // ConstNumber, (DexItemBased)?ConstString, InstanceGet and StaticGet.
    switch (instruction.opcode()) {
      case CONST_CLASS:
        // Do not canonicalize ConstClass that may have side effects. Its original instructions
        // will not be removed by dead code remover due to the side effects.
        if (instruction.instructionMayHaveSideEffects(appView, context)) {
          return false;
        }
        break;
      case CONST_NUMBER:
        break;
      case CONST_STRING:
      case DEX_ITEM_BASED_CONST_STRING:
        break;
      case INSTANCE_GET:
        {
          InstanceGet instanceGet = instruction.asInstanceGet();
          if (instanceGet.instructionMayHaveSideEffects(appView, context)) {
            return false;
          }
          if (instanceGet.object().isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
            NewInstance newInstance = instanceGet.object().getDefinition().asNewInstance();
            if (newInstance.getUniqueConstructorInvoke(appView.dexItemFactory()) == null) {
              return false;
            }
          }
          if (!isReadOfEffectivelyFinalFieldOutsideInitializer(instanceGet)) {
            return false;
          }
          if (getOrComputeIneligibleInstanceGetInstructions().contains(instanceGet)) {
            return false;
          }
          break;
        }
      case STATIC_GET:
        {
          // Canonicalize effectively final fields that are guaranteed to be written before they are
          // read. This is only OK if the instruction cannot have side effects.
          StaticGet staticGet = instruction.asStaticGet();
          if (staticGet.instructionMayHaveSideEffects(appView, context)) {
            return false;
          }
          if (!isReadOfEffectivelyFinalFieldOutsideInitializer(staticGet)
              && !isEffectivelyFinalField(staticGet)) {
            return false;
          }
          break;
        }
      default:
        assert !instruction.instructionTypeCanBeCanonicalized() : instruction.toString();
        return false;
    }
    // Constants with local info must not be canonicalized and must be filtered.
    if (instruction.outValue().hasLocalInfo()) {
      return false;
    }
    // Do not canonicalize throwing instructions if there are monitor operations in the code.
    // That could lead to unbalanced locking and could lead to situations where OOM exceptions
    // could leave a synchronized method without unlocking the monitor.
    if (instruction.instructionTypeCanThrow() && code.metadata().mayHaveMonitorInstruction()) {
      return false;
    }
    // Constants that are used by invoke range are not canonicalized to be compliant with the
    // optimization splitRangeInvokeConstant that gives the register allocator more freedom in
    // assigning register to ranged invokes which can greatly reduce the number of register
    // needed (and thereby code size as well). Thus no need to do a transformation that should
    // be removed later by another optimization.
    if (constantUsedByInvokeRange(instruction)) {
      return false;
    }
    return true;
  }

  private boolean isReadOfEffectivelyFinalFieldOutsideInitializer(FieldGet fieldGet) {
    if (getOrComputeIsAccessingVolatileField()) {
      // A final field may be initialized concurrently. A requirement for this is that the field is
      // volatile. However, the reading or writing of another volatile field also allows for
      // concurrently initializing a non-volatile field. See also redundant field load elimination.
      return false;
    }
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();
    SingleFieldResolutionResult<?> resolutionResult =
        appViewWithClassHierarchy
            .appInfo()
            .resolveField(fieldGet.getField())
            .asSingleFieldResolutionResult();
    if (resolutionResult == null) {
      // Not known to be final.
      return false;
    }
    if (!resolutionResult.isSingleProgramFieldResolutionResult()) {
      // We can't rely on the final flag of non-program fields.
      return false;
    }
    ProgramField resolvedField = resolutionResult.getSingleProgramField();
    FieldAccessFlags accessFlags = resolvedField.getAccessFlags();
    assert !accessFlags.isVolatile();
    if (!resolvedField.isFinalOrEffectivelyFinal(appViewWithClassHierarchy)) {
      return false;
    }
    if (appView.getKeepInfo(resolvedField).isPinned(appView.options())) {
      // The final flag could be unset using reflection.
      return false;
    }
    if (context.getDefinition().isInitializer()
        && context.getAccessFlags().isStatic() == fieldGet.isStaticGet()) {
      if (context.getHolder() == resolvedField.getHolder()) {
        // If this is an initializer on the field's holder, then bail out, since the field value is
        // only known to be final after object/class creation.
        return false;
      }
      if (fieldGet.isInstanceGet()
          && appViewWithClassHierarchy
              .appInfo()
              .isSubtype(context.getHolder(), resolvedField.getHolder())) {
        // If an instance initializer reads a final instance field declared in a super class, we
        // cannot hoist the read above the parent constructor call.
        return false;
      }
    }
    if (!resolutionResult.getInitialResolutionHolder().isResolvable(appView)) {
      // If this field read is guarded by an API level check, hoisting of this field could lead to
      // a ClassNotFoundException on some API levels.
      return false;
    }
    return true;
  }

  private boolean isEffectivelyFinalField(StaticGet staticGet) {
    AbstractValue abstractValue = staticGet.outValue().getAbstractValue(appView, context);
    if (!abstractValue.isSingleFieldValue()) {
      return false;
    }
    SingleFieldValue singleFieldValue = abstractValue.asSingleFieldValue();
    DexType fieldHolderType = singleFieldValue.getField().getHolderType();
    if (context.getDefinition().isClassInitializer()
        && context.getHolderType() == fieldHolderType) {
      // Avoid that canonicalization inserts a read before the unique write in the class
      // initializer, as that would change the program behavior.
      return false;
    }
    DexClass fieldHolder = appView.definitionFor(fieldHolderType);
    return singleFieldValue.getField().lookupOnClass(fieldHolder) != null;
  }

  private void insertCanonicalizedConstantInEntryBlock(Instruction canonicalizedConstant) {
    BasicBlock entryBlock = code.entryBlock();
    // Insert the constant instruction at the start of the block right after the argument
    // instructions. It is important that the const instruction is put before any instruction
    // that can throw exceptions (since the value could be used on the exceptional edge).
    InstructionListIterator it = entryBlock.listIterator(code);
    while (it.hasNext()) {
      Instruction next = it.next();
      if (!next.isArgument()) {
        canonicalizedConstant.setPosition(code.getEntryPosition());
        it.previous();
        break;
      }
    }
    it.add(canonicalizedConstant);
  }

  private InstructionListIterator insertCanonicalizedConstantAtInsertionPoint(
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InstructionOrPhi insertionPoint,
      Instruction newInstruction) {
    // If the insertion point is a phi, then we're inserting the new instruction before all other
    // instructions in the block.
    assert !insertionPoint.isPhi() || !instructionIterator.hasPrevious();
    // If the insertion point is not a phi, the iterator is positioned immediately after the
    // insertion point.
    assert insertionPoint.isPhi() || instructionIterator.peekPrevious() == insertionPoint;
    newInstruction.setPosition(
        getPositionForCanonicalizationConstantAtInsertionPoint(insertionPoint, newInstruction));
    if (newInstruction.instructionTypeCanThrow()
        && insertionPoint.getBlock().hasCatchHandlers()
        && insertionPoint.getBlock().canThrow()) {
      // Split the block and rewind the block iterator to the insertion block.
      BasicBlock splitBlock =
          instructionIterator.splitCopyCatchHandlers(
              code, blockIterator, appView.options(), ignore -> insertionPoint.getBlock());
      if (insertionPoint.isPhi()) {
        // Add new instruction before the goto and position the instruction iterator before the
        // first instruction (i.e., at the phi position).
        assert insertionPoint.getBlock().getInstructions().size() == 1;
        instructionIterator.addBeforeAndPositionBeforeNewInstruction(newInstruction);
        assert !instructionIterator.hasPrevious();
      } else {
        // Add the new instruction after the insertion point. If the block containing the insertion
        // point can throw, we insert the new instruction in the beginning of the split block.
        // Otherwise, we insert it in the end of the insertion block.
        if (insertionPoint.getBlock().canThrow()) {
          assert !splitBlock.canThrow();
          splitBlock.listIterator(code).add(newInstruction);
        } else {
          assert splitBlock.canThrow();
          instructionIterator.addBeforeAndPositionBeforeNewInstruction(newInstruction);
        }
        instructionIterator.positionAfterPreviousInstruction(insertionPoint.asInstruction());
      }
    } else {
      instructionIterator.addAndPositionBeforeNewInstruction(newInstruction);
    }
    return instructionIterator;
  }

  private Position getPositionForCanonicalizationConstantAtInsertionPoint(
      InstructionOrPhi insertionPoint, Instruction newInstruction) {
    Position insertionPosition =
        insertionPoint.isPhi()
            ? insertionPoint.getBlock().getPosition()
            : insertionPoint.asInstruction().getPosition();
    if (newInstruction.instructionTypeCanThrow() && insertionPosition.isNone()) {
      return Position.syntheticNone();
    }
    return insertionPosition;
  }

  private static boolean constantUsedByInvokeRange(Instruction constant) {
    for (Instruction user : constant.outValue().uniqueUsers()) {
      if (user.isInvoke() && user.asInvoke().requiredArgumentRegisters() > 5) {
        return true;
      }
    }
    return false;
  }
}
