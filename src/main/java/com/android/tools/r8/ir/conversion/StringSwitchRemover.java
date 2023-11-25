// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isClassNameValue;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceRBTreeMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.io.UTFDataFormatException;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class StringSwitchRemover {

  private final AppView<?> appView;
  private final IdentifierNameStringMarker identifierNameStringMarker;
  private final ClassTypeElement stringType;

  StringSwitchRemover(AppView<?> appView, IdentifierNameStringMarker identifierNameStringMarker) {
    this.appView = appView;
    this.identifierNameStringMarker = identifierNameStringMarker;
    this.stringType = TypeElement.stringClassType(appView, definitelyNotNull());
  }

  public void run(IRCode code) {
    if (!code.metadata().mayHaveStringSwitch()) {
      assert Streams.stream(code.instructions()).noneMatch(Instruction::isStringSwitch);
      return;
    }

    if (!prepareForStringSwitchRemoval(code)) {
      return;
    }

    Set<BasicBlock> newBlocksWithStrings = Sets.newIdentityHashSet();

    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      StringSwitch theSwitch = block.exit().asStringSwitch();
      if (theSwitch != null) {
        try {
          SingleStringSwitchRemover remover;
          if (theSwitch.numberOfKeys() < appView.options().minimumStringSwitchSize
              || hashCodeOfKeysMayChangeAfterMinification(theSwitch)) {
            remover =
                new SingleEqualityBasedStringSwitchRemover(
                    code, blockIterator, block, theSwitch, newBlocksWithStrings);
          } else {
            remover =
                new SingleHashBasedStringSwitchRemover(
                    code, blockIterator, block, theSwitch, newBlocksWithStrings);
          }
          remover.removeStringSwitch();
        } catch (UTFDataFormatException e) {
          // The keys of a string-switch should never fail to decode.
          throw new Unreachable();
        }
      }
    }

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInBlocks(code, newBlocksWithStrings);
    }

    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  // Returns true if minification is enabled and the switch value is guaranteed to be a class name.
  // In this case, we can't use the hash codes of the keys before minification, because they will
  // (potentially) change as a result of minification. Therefore, we currently emit a sequence of
  // if-equals checks for such switches.
  //
  // TODO(b/154483187): This should also use the hash-based string switch elimination.
  private boolean hashCodeOfKeysMayChangeAfterMinification(StringSwitch theSwitch) {
    return appView.options().isMinifying()
        && isClassNameValue(theSwitch.value(), appView.dexItemFactory());
  }

  @SuppressWarnings("UnusedVariable")
  private boolean prepareForStringSwitchRemoval(IRCode code) {
    boolean hasStringSwitch = false;
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      for (BasicBlock predecessor : block.getNormalPredecessors()) {
        StringSwitch exit = predecessor.exit().asStringSwitch();
        if (exit != null) {
          hasStringSwitch = true;
          if (block == exit.fallthroughBlock()) {
            // After the elimination of this string-switch instruction, there will be two
            // fallthrough blocks: one for the instruction that switches on the hash value and one
            // for the instruction that switches on the string id value.
            //
            // The existing fallthrough block will be the fallthrough block for the switch on the
            // hash value. Note that we can't use this block for the switch on the id value since
            // that would lead to critical edges.
            BasicBlock hashSwitchFallthroughBlock = block;

            // The `hashSwitchFallthroughBlock` will jump to the switch on the string id value.
            // This block will have multiple predecessors, hence the need for the split-edge
            // block.
            BasicBlock idSwitchBlock =
                hashSwitchFallthroughBlock.listIterator(code).split(code, blockIterator);

            // Split again such that `idSwitchBlock` becomes a block consisting of a single goto
            // instruction that targets a block that is identical to the original fallthrough
            // block of the string-switch instruction.
            BasicBlock idSwitchFallthroughBlock =
                idSwitchBlock.listIterator(code).split(code, blockIterator);
            break;
          }
        }
      }
    }

    return hasStringSwitch;
  }

  private abstract static class SingleStringSwitchRemover {

    final IRCode code;
    final ListIterator<BasicBlock> blockIterator;
    final Set<BasicBlock> newBlocksWithStrings;

    final Position position;
    final Value stringValue;

    private SingleStringSwitchRemover(
        IRCode code,
        ListIterator<BasicBlock> blockIterator,
        StringSwitch theSwitch,
        Set<BasicBlock> newBlocksWithStrings) {
      this.code = code;
      this.blockIterator = blockIterator;
      this.newBlocksWithStrings = newBlocksWithStrings;
      this.position = theSwitch.getPosition();
      this.stringValue = theSwitch.value();
    }

    abstract void removeStringSwitch();
  }

  private class SingleEqualityBasedStringSwitchRemover extends SingleStringSwitchRemover {

    private final BasicBlock block;
    private final BasicBlock fallthroughBlock;

    private final Map<DexString, BasicBlock> structure;

    private SingleEqualityBasedStringSwitchRemover(
        IRCode code,
        ListIterator<BasicBlock> blockIterator,
        BasicBlock block,
        StringSwitch theSwitch,
        Set<BasicBlock> newBlocksWithStrings) {
      super(code, blockIterator, theSwitch, newBlocksWithStrings);
      this.block = block;
      this.fallthroughBlock = theSwitch.fallthroughBlock();
      this.structure = createStructure(theSwitch);
    }

    private Map<DexString, BasicBlock> createStructure(StringSwitch theSwitch) {
      Map<DexString, BasicBlock> result = new LinkedHashMap<>();
      theSwitch.forEachCase(result::put);
      return result;
    }

    @Override
    void removeStringSwitch() {
      // Remove outgoing control flow edges from the block containing the string switch.
      for (BasicBlock successor : block.getNormalSuccessors()) {
        successor.removePredecessor(block, null);
      }
      block.removeAllNormalSuccessors();
      Set<BasicBlock> blocksTargetedByMultipleSwitchCases = Sets.newIdentityHashSet();
      {
        Set<BasicBlock> seenBefore = SetUtils.newIdentityHashSet(structure.size());
        for (BasicBlock targetBlock : structure.values()) {
          if (!seenBefore.add(targetBlock)) {
            blocksTargetedByMultipleSwitchCases.add(targetBlock);
          }
        }
      }
      // Create a String.equals() check for each case in the string-switch instruction.
      BasicBlock previous = null;
      for (Entry<DexString, BasicBlock> entry : structure.entrySet()) {
        ConstString constStringInstruction =
            new ConstString(code.createValue(stringType), entry.getKey());
        constStringInstruction.setPosition(position);
        InvokeVirtual invokeInstruction =
            new InvokeVirtual(
                appView.dexItemFactory().stringMembers.equals,
                code.createValue(PrimitiveTypeElement.getInt()),
                ImmutableList.of(stringValue, constStringInstruction.outValue()));
        invokeInstruction.setPosition(position);
        If ifInstruction = new If(IfType.NE, invokeInstruction.outValue());
        ifInstruction.setPosition(Position.none());
        BasicBlock targetBlock = entry.getValue();
        if (blocksTargetedByMultipleSwitchCases.contains(targetBlock)) {
          // Need an intermediate block to avoid critical edges.
          BasicBlock intermediateBlock =
              BasicBlock.createGotoBlock(
                  code.getNextBlockNumber(), Position.none(), code.metadata());
          intermediateBlock.link(targetBlock);
          blockIterator.add(intermediateBlock);
          newBlocksWithStrings.add(intermediateBlock);
          targetBlock = intermediateBlock;
        }
        BasicBlock newBlock =
            BasicBlock.createIfBlock(
                code.getNextBlockNumber(),
                ifInstruction,
                code.metadata(),
                constStringInstruction,
                invokeInstruction);
        newBlock.link(targetBlock);
        blockIterator.add(newBlock);
        newBlocksWithStrings.add(newBlock);
        if (previous == null) {
          // Replace the string-switch instruction by a goto instruction.
          block.exit().replace(new Goto(newBlock), code);
          block.link(newBlock);
        } else {
          // Set the fallthrough block for the previously added if-instruction.
          previous.link(newBlock);
        }
        previous = newBlock;
      }
      assert previous != null;
      // Set the fallthrough block for the last if-instruction.
      previous.link(fallthroughBlock);
    }
  }

  private class SingleHashBasedStringSwitchRemover extends SingleStringSwitchRemover {

    private final BasicBlock hashSwitchBlock;
    private final BasicBlock hashSwitchFallthroughBlock;
    private final BasicBlock idSwitchBlock;
    private final BasicBlock idSwitchFallthroughBlock;

    Int2ReferenceMap<Map<DexString, BasicBlock>> structure;

    private int nextStringId;

    private SingleHashBasedStringSwitchRemover(
        IRCode code,
        ListIterator<BasicBlock> blockIterator,
        BasicBlock hashSwitchBlock,
        StringSwitch theSwitch,
        Set<BasicBlock> newBlocksWithStrings)
        throws UTFDataFormatException {
      super(code, blockIterator, theSwitch, newBlocksWithStrings);
      this.hashSwitchBlock = hashSwitchBlock;
      this.hashSwitchFallthroughBlock = theSwitch.fallthroughBlock();
      this.idSwitchBlock = theSwitch.fallthroughBlock().getUniqueNormalSuccessor();
      this.idSwitchFallthroughBlock = idSwitchBlock.getUniqueNormalSuccessor();
      this.structure = createStructure(theSwitch);
    }

    private int getAndIncrementNextBlockNumber() {
      return code.getNextBlockNumber();
    }

    private Int2ReferenceMap<Map<DexString, BasicBlock>> createStructure(StringSwitch theSwitch)
        throws UTFDataFormatException {
      Int2ReferenceMap<Map<DexString, BasicBlock>> result = new Int2ReferenceRBTreeMap<>();
      theSwitch.forEachCase(
          (key, target) -> {
            int hashCode = key.decodedHashCode();
            if (result.containsKey(hashCode)) {
              result.get(hashCode).put(key, target);
            } else {
              Map<DexString, BasicBlock> cases = new LinkedHashMap<>();
              cases.put(key, target);
              result.put(hashCode, cases);
            }
          });
      return result;
    }

    @Override
    void removeStringSwitch() {
      // Remove outgoing control flow edges from the block containing the string switch.
      for (BasicBlock successor : hashSwitchBlock.getNormalSuccessors()) {
        successor.removePredecessor(hashSwitchBlock, null);
      }
      hashSwitchBlock.removeAllNormalSuccessors();

      // 1. Insert `int id = -1`.
      InstructionListIterator instructionIterator =
          hashSwitchBlock.listIterator(code, hashSwitchBlock.size());
      instructionIterator.previous();

      Phi idPhi = code.createPhi(idSwitchBlock, TypeElement.getInt());
      Value notFoundIdValue =
          instructionIterator.insertConstIntInstruction(code, appView.options(), -1);
      idPhi.appendOperand(notFoundIdValue);

      // 2. Insert `int hashCode = stringValue.hashCode()`.
      InvokeVirtual hashInvoke =
          new InvokeVirtual(
              appView.dexItemFactory().stringMembers.hashCode,
              code.createValue(TypeElement.getInt()),
              ImmutableList.of(stringValue));
      hashInvoke.setPosition(position);
      instructionIterator.add(hashInvoke);

      // 3. Create all the target blocks of the hash switch.
      //
      // Say that the string switch instruction contains the keys "X" and "Y" and assume that "X"
      // and "Y" have the same hash code. Then this will create code that looks like:
      //
      //   Block N:
      //     boolean equalsX = stringValue.equals("X")
      //     if equalsX then goto block N+1 else goto block N+2
      //   Block N+1:
      //     id = 0
      //     goto <id-switch-block>
      //   Block N+2:
      //     boolean equalsY = stringValue.equals("Y")
      //     if equalsY then goto block N+3 else goto block N+4
      //   Block N+3:
      //     id = 1
      //     goto <id-switch-block>
      //   Block N+4:
      //     goto <id-switch-block>
      createHashSwitchTargets(idPhi, notFoundIdValue);
      hashSwitchBlock.link(hashSwitchFallthroughBlock);

      // 4. Insert `switch (hashValue)`.
      IntSwitch hashSwitch = createHashSwitch(hashInvoke.outValue());
      instructionIterator.next();
      instructionIterator.replaceCurrentInstruction(hashSwitch);

      // 5. Link `idSwitchBlock` with all of its target blocks.
      Reference2IntMap<BasicBlock> targetBlockIndices = new Reference2IntOpenHashMap<>();
      targetBlockIndices.defaultReturnValue(-1);
      idSwitchBlock.getMutableSuccessors().clear();
      for (Map<DexString, BasicBlock> cases : structure.values()) {
        for (BasicBlock target : cases.values()) {
          int targetIndex = targetBlockIndices.getInt(target);
          if (targetIndex == -1) {
            targetBlockIndices.put(target, idSwitchBlock.getSuccessors().size());
            idSwitchBlock.link(target);
          }
        }
      }
      idSwitchBlock.getMutableSuccessors().add(idSwitchFallthroughBlock);

      // 6. Insert `switch (idValue)`.
      IntSwitch idSwitch = createIdSwitch(idPhi, targetBlockIndices);
      InstructionListIterator idSwitchBlockInstructionIterator = idSwitchBlock.listIterator(code);
      idSwitchBlockInstructionIterator.next();
      idSwitchBlockInstructionIterator.replaceCurrentInstruction(idSwitch);
    }

    private IntSwitch createHashSwitch(Value hashValue) {
      int[] hashSwitchKeys = structure.keySet().toArray(new int[0]);
      int[] hashSwitchTargetIndices = new int[hashSwitchKeys.length];
      for (int i = 0, offset = hashSwitchBlock.numberOfExceptionalSuccessors();
          i < hashSwitchTargetIndices.length;
          i++) {
        hashSwitchTargetIndices[i] = i + offset;
      }
      int hashSwitchFallthroughIndex = hashSwitchBlock.getSuccessors().size() - 1;
      return new IntSwitch(
          hashValue, hashSwitchKeys, hashSwitchTargetIndices, hashSwitchFallthroughIndex);
    }

    private void createHashSwitchTargets(Phi idPhi, Value notFoundIdValue) {
      for (Map<DexString, BasicBlock> cases : structure.values()) {
        // Create the target block for the hash switch.
        BasicBlock hashBlock =
            BasicBlock.createGotoBlock(getAndIncrementNextBlockNumber(), position, code.metadata());
        blockIterator.add(hashBlock);
        hashSwitchBlock.link(hashBlock);

        // Position the block iterator at the newly created block.
        BasicBlock previous = blockIterator.previous();
        assert previous == hashBlock;
        blockIterator.next();

        BasicBlock current = hashBlock;
        for (Entry<DexString, BasicBlock> entry : cases.entrySet()) {
          current.getMutableSuccessors().clear();

          // Insert `String key = <entry.getKey()>`.
          InstructionListIterator instructionIterator = current.listIterator(code);
          Value keyValue =
              instructionIterator.insertConstStringInstruction(appView, code, entry.getKey());
          newBlocksWithStrings.add(current);

          // Insert `boolean equalsKey = stringValue.equals(key)`.
          InvokeVirtual equalsInvoke =
              new InvokeVirtual(
                  appView.dexItemFactory().stringMembers.equals,
                  code.createValue(TypeElement.getInt()),
                  ImmutableList.of(stringValue, keyValue));
          equalsInvoke.setPosition(position);
          instructionIterator.add(equalsInvoke);

          // Create a new block for the success case.
          BasicBlock equalsKeyBlock =
              BasicBlock.createGotoBlock(
                  getAndIncrementNextBlockNumber(), position, code.metadata(), idSwitchBlock);
          idSwitchBlock.getMutablePredecessors().add(equalsKeyBlock);
          blockIterator.add(equalsKeyBlock);
          current.link(equalsKeyBlock);

          // Insert `int id = <nextStringId++>`.
          Value idValue =
              equalsKeyBlock
                  .listIterator(code)
                  .insertConstIntInstruction(code, appView.options(), nextStringId++);
          idPhi.appendOperand(idValue);

          // Create a new block for the failure case.
          BasicBlock continuationBlock =
              BasicBlock.createGotoBlock(
                  getAndIncrementNextBlockNumber(), position, code.metadata(), idSwitchBlock);
          blockIterator.add(continuationBlock);
          current.link(continuationBlock);

          // Insert `if (equalsKey) goto <id-switch-block> else goto <continuation-block>`.
          instructionIterator.next();
          instructionIterator.replaceCurrentInstruction(new If(IfType.NE, equalsInvoke.outValue()));

          current = continuationBlock;
        }
        idPhi.appendOperand(notFoundIdValue);
        idSwitchBlock.getMutablePredecessors().add(current);
      }
    }

    private IntSwitch createIdSwitch(Phi idPhi, Reference2IntMap<BasicBlock> targetBlockIndices) {
      int numberOfCases = nextStringId;
      int[] keys = ArrayUtils.createIdentityArray(numberOfCases);
      int[] targetIndices = new int[numberOfCases];
      int i = 0;
      for (Map<DexString, BasicBlock> cases : structure.values()) {
        for (Entry<DexString, BasicBlock> entry : cases.entrySet()) {
          targetIndices[i++] = targetBlockIndices.getInt(entry.getValue());
        }
      }
      int fallthroughIndex = targetBlockIndices.size();
      return new IntSwitch(idPhi, keys, targetIndices, fallthroughIndex);
    }
  }
}
