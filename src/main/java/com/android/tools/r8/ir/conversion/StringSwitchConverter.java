// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.code.ConstNumber.asConstNumberOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Utility that given a {@link IRCode} object attempts to identity string-switch instructions.
 *
 * <p>For backwards compatibility and performance, javac compiles string-switch statements into two
 * subsequent switch statements as follows.
 *
 * <p>Code before javac transformation:
 *
 * <pre>
 *   switch (value) {
 *     case "A": [[CASE_A]]; break;
 *     case "B": [[CASE_B]]; break;
 *     default: [[FALLTHROUGH]]; break;
 *   }
 * </pre>
 *
 * Code after javac transformation:
 *
 * <pre>
 *   int hash = value.hashCode();
 *   int id = -1;
 *   switch (hash) {
 *     case 65:
 *       if (value.equals("A")) {
 *         id = 0;
 *       }
 *       break;
 *     case 66:
 *       if (value.equals("B")) {
 *         id = 1;
 *       }
 *       break;
 *   }
 *   switch (id) {
 *     case 0: [[CASE_A]]; break;
 *     case 1: [[CASE_B]]; break;
 *     default: [[FALLTHROUGH]]; break;
 *   }
 * </pre>
 *
 * The {@link StringSwitchConverter} identifies this pattern and replaces it with a single {@link
 * StringSwitch} instruction, such that the IR will look similar to the original Java code.
 *
 * <p>Note that this converter also identifies if the switch has been partly or fully rewritten to a
 * series of if-instructions, as in the following example:
 *
 * <pre>
 *   int hash = value.hashCode();
 *   int id = -1;
 *   if (hash == 65) {
 *     if (value.equals("A")) {
 *       id = 0;
 *     }
 *   } else if (hash == 66) {
 *     if (value.equals("B")) {
 *       id = 1;
 *     }
 *   }
 *   if (id == 0) {
 *     [[CASE_A]]
 *   } else if (id == 1) {
 *     [[CASE_B]]
 *   } else {
 *     [[FALLTHROUGH]]
 *   }
 * </pre>
 */
public class StringSwitchConverter {

  public static boolean convertToStringSwitchInstructions(
      IRCode code, DexItemFactory dexItemFactory) {
    List<BasicBlock> rewritingCandidates = getRewritingCandidates(code, dexItemFactory);
    if (rewritingCandidates != null) {
      boolean changed = false;
      for (BasicBlock block : rewritingCandidates) {
        if (convertRewritingCandidateToStringSwitchInstruction(code, block, dexItemFactory)) {
          changed = true;
        }
      }
      if (changed) {
        code.removeAllDeadAndTrivialPhis();
        code.removeUnreachableBlocks();
      }
      return changed;
    }
    return false;
  }

  private static List<BasicBlock> getRewritingCandidates(
      IRCode code, DexItemFactory dexItemFactory) {
    int markingColor = code.reserveMarkingColor();
    List<BasicBlock> rewritingCandidates = null;
    for (BasicBlock block : code.blocks) {
      if (block.isMarked(markingColor)) {
        // Already visited previously.
        continue;
      }
      block.mark(markingColor);

      // Check if the exit instruction of the current block is an if-instruction that compares the
      // hash code of a String value.
      if (!Utils.isComparisonOfStringHashValue(block.exit(), dexItemFactory)) {
        continue;
      }

      // If so, then repeatedly follow the fall-through target as long as this is the case.
      // This way, we find the last block in the chain of hash code comparisons. The fallthrough
      // target of that block is the block that will switch on the computed `id` variable.
      BasicBlock end = block;
      while (true) {
        BasicBlock fallthroughBlock = Utils.fallthroughBlock(end.exit());
        if (fallthroughBlock.isMarked(markingColor)) {
          // Already visited previously.
          end = null;
          break;
        }
        fallthroughBlock.mark(markingColor);

        if (Utils.isComparisonOfStringHashValue(fallthroughBlock.exit(), dexItemFactory)) {
          end = fallthroughBlock;
        } else {
          break;
        }
      }

      if (end == null) {
        continue;
      }

      // Record the final block in the chain as a rewriting candidate.
      if (rewritingCandidates == null) {
        rewritingCandidates = new ArrayList<>();
      }
      rewritingCandidates.add(end);
    }
    code.returnMarkingColor(markingColor);
    return rewritingCandidates;
  }

  private static boolean convertRewritingCandidateToStringSwitchInstruction(
      IRCode code, BasicBlock block, DexItemFactory dexItemFactory) {
    StringSwitchBuilderInfo info = StringSwitchBuilderInfo.builder(dexItemFactory).build(block);
    if (info != null) {
      info.createAndInsertStringSwitch(code);
      return true;
    }
    return false;
  }

  private static boolean isDefinedByStringHashCode(Value value, DexItemFactory dexItemFactory) {
    Value root = value.getAliasedValue();
    if (root.isPhi()) {
      return false;
    }
    Instruction definition = root.definition;
    return definition.isInvokeVirtual()
        && definition.asInvokeVirtual().getInvokedMethod() == dexItemFactory.stringMembers.hashCode;
  }

  static class StringSwitchBuilderInfo {

    static class Builder {

      private final DexItemFactory dexItemFactory;

      private Builder(DexItemFactory dexItemFactory) {
        this.dexItemFactory = dexItemFactory;
      }

      StringSwitchBuilderInfo build(BasicBlock block) {
        BasicBlock continuationBlock = Utils.fallthroughBlock(block.exit()).endOfGotoChain();
        IdToTargetMapping idToTargetMapping = IdToTargetMapping.builder().build(continuationBlock);
        if (idToTargetMapping == null) {
          return null;
        }

        if (idToTargetMapping.fallthroughBlock == null) {
          assert false : "Expected to find a fallthrough block";
          return null;
        }

        Value stringHashValue = Utils.getStringHashValueFromJump(block.exit(), dexItemFactory);
        Value stringValue = Utils.getStringValueFromHashValue(stringHashValue, dexItemFactory);
        StringToIdMapping stringToIdMapping =
            StringToIdMapping.builder(
                    continuationBlock, dexItemFactory, idToTargetMapping.idValue, stringValue)
                .build(block);
        if (stringToIdMapping == null) {
          return null;
        }

        if (stringToIdMapping.insertionBlock == null) {
          assert false : "Expected to find an insertion block";
          return null;
        }

        if (stringToIdMapping.mapping.size() != idToTargetMapping.mapping.size()) {
          return null;
        }

        Map<DexString, BasicBlock> stringToTargetMapping = new LinkedHashMap<>();
        for (DexString key : stringToIdMapping.mapping.keySet()) {
          int id = stringToIdMapping.mapping.getInt(key);
          BasicBlock target = idToTargetMapping.mapping.get(id);
          if (target == null) {
            return null;
          }
          stringToTargetMapping.put(key, target);
        }
        return new StringSwitchBuilderInfo(
            idToTargetMapping.fallthroughBlock,
            stringToIdMapping.insertionBlock,
            stringToTargetMapping,
            stringValue);
      }
    }

    private final BasicBlock fallthroughBlock;
    private final BasicBlock insertionBlock;
    private final Map<DexString, BasicBlock> mapping;
    private final Value value;

    StringSwitchBuilderInfo(
        BasicBlock fallthroughBlock,
        BasicBlock insertionBlock,
        Map<DexString, BasicBlock> mapping,
        Value value) {
      this.fallthroughBlock = fallthroughBlock;
      this.insertionBlock = insertionBlock;
      this.mapping = mapping;
      this.value = value;
    }

    static Builder builder(DexItemFactory dexItemFactory) {
      return new Builder(dexItemFactory);
    }

    void createAndInsertStringSwitch(IRCode code) {
      // Remove outgoing control flow edges from `insertionBlock`.
      for (BasicBlock successor : insertionBlock.getNormalSuccessors()) {
        successor.removePredecessor(insertionBlock, null);
      }
      insertionBlock.removeAllNormalSuccessors();

      // Build new StringSwitch instruction meanwhile inserting new control flow edges.
      DexString[] keys = new DexString[mapping.size()];
      int[] targetBlockIndices = new int[mapping.size()];
      Reference2IntMap<BasicBlock> emittedTargetBlockIndices = new Reference2IntOpenHashMap<>();
      int i = 0;
      int nextTargetBlockIndex = insertionBlock.numberOfCatchHandlers();
      for (Entry<DexString, BasicBlock> entry : mapping.entrySet()) {
        keys[i] = entry.getKey();
        BasicBlock targetBlock = entry.getValue();
        if (emittedTargetBlockIndices.containsKey(targetBlock)) {
          targetBlockIndices[i] = emittedTargetBlockIndices.getInt(targetBlock);
        } else {
          int targetBlockIndex = nextTargetBlockIndex;
          targetBlockIndices[i] = targetBlockIndex;
          emittedTargetBlockIndices.put(targetBlock, targetBlockIndex);
          insertionBlock.link(targetBlock);
          nextTargetBlockIndex++;
        }
        i++;
      }
      insertionBlock.link(fallthroughBlock);
      JumpInstruction exit = insertionBlock.exit();
      int fallthroughBlockIndex = nextTargetBlockIndex;
      exit.replace(new StringSwitch(value, keys, targetBlockIndices, fallthroughBlockIndex), code);
    }
  }

  static class StringToIdMapping {

    static class Builder {

      private final BasicBlock continuationBlock;
      private final DexItemFactory dexItemFactory;
      private final Phi intermediateIdValue;
      private final Value stringValue;

      Builder(
          BasicBlock continuationBlock,
          DexItemFactory dexItemFactory,
          Phi idValue,
          Value stringValue) {
        this.continuationBlock = continuationBlock;
        this.dexItemFactory = dexItemFactory;
        this.intermediateIdValue = getIntermediateIdValueOrElse(idValue, idValue);
        this.stringValue = stringValue;
      }

      // Finds the intermediate id value from the given (non-intermediate) id value. If the non-
      // intermediate id value v_id has the following structure, then v_id_intermediate is returned.
      //
      //   v_id_intermediate = phi(v1(0), v2(1), v_n(n-1))
      //   v_id = phi(v0(-1), v_id_intermediate)
      //
      // Normally, this intermediate value is not present, and the code will have the following
      // structure:
      //
      //   v_id = phi(v0(-1), v1(0), v2(1), v_n(n-1))
      private Phi getIntermediateIdValueOrElse(Phi idValue, Phi defaultValue) {
        if (idValue.getOperands().size() != 2) {
          return defaultValue;
        }
        Phi intermediateIdValue = null;
        for (Value operand : idValue.getOperands()) {
          if (operand.isPhi()) {
            if (intermediateIdValue != null) {
              return defaultValue;
            }
            intermediateIdValue = operand.asPhi();
          }
        }
        if (intermediateIdValue == null) {
          return defaultValue;
        }
        for (Value operand : intermediateIdValue.getOperands()) {
          if (operand.isPhi()) {
            return defaultValue;
          }
        }
        return intermediateIdValue;
      }

      // Attempts to build a mapping from strings to their ids starting from the given block. The
      // mapping is built by traversing the control flow graph upwards, so the given block is
      // expected to be the last block in the sequence of blocks that compare the hash code of the
      // string value to different constant values.
      //
      // If the given block (and its successor blocks) is on the form described by the following
      // grammar, then a non-empty mapping is returned. Otherwise, null is returned.
      //
      //   HASH_TO_STRING_MAPPING :=
      //           if (hashValue == <const-number>) {
      //             [[STRING_TO_ID_MAPPING]]
      //           } else {
      //             [[HASH_TO_STRING_MAPPING_OR_EXIT]]
      //           }
      //         | switch (hashValue) {
      //             case <const-number>:
      //               [[STRING_TO_ID_MAPPING]]; break;
      //             case <const-number>:
      //               [[STRING_TO_ID_MAPPING]]; break;
      //             ...
      //             default:
      //               [[HASH_TO_STRING_MAPPING_OR_EXIT]]; break;
      //           }
      //   STRING_TO_ID_MAPPING :=
      //           if (stringValue.equals(<const-string>)) {
      //             idValue = <const-number>;
      //           } else {
      //             [[STRING_TO_ID_MAPPING_OR_EXIT]]
      //           }
      //   EXIT := <any block>
      StringToIdMapping build(BasicBlock block) {
        return extend(null, block);
      }

      private StringToIdMapping extend(StringToIdMapping toBeExtended, BasicBlock block) {
        JumpInstruction exit = block.exit();
        if (exit.isIf()) {
          return extendWithIf(toBeExtended, exit.asIf());
        }
        if (exit.isIntSwitch()) {
          return extendWithSwitch(toBeExtended, exit.asIntSwitch());
        }
        return toBeExtended;
      }

      private StringToIdMapping extendWithPredecessor(
          StringToIdMapping toBeExtended, BasicBlock block) {
        boolean mayExtendWithPredecessor = true;
        for (Instruction instruction : block.getInstructions()) {
          if (instruction.isConstNumber() && instruction.outValue().onlyUsedInBlock(block)) {
            continue;
          }
          if (instruction.isInvokeVirtual()) {
            InvokeVirtual invoke = instruction.asInvokeVirtual();
            if (invoke.getInvokedMethod() == dexItemFactory.stringMembers.hashCode
                && invoke.getReceiver() == stringValue
                && (!invoke.hasOutValue() || invoke.outValue().onlyUsedInBlock(block))) {
              continue;
            }
          }
          if (instruction.isJumpInstruction()) {
            continue;
          }
          mayExtendWithPredecessor = false;
          break;
        }
        if (!mayExtendWithPredecessor) {
          return toBeExtended;
        }

        if (!block.hasUniquePredecessor()) {
          return toBeExtended;
        }

        BasicBlock predecessor = block.getUniquePredecessor().startOfGotoChain();
        return extend(toBeExtended, predecessor);
      }

      private StringToIdMapping extendWithIf(StringToIdMapping toBeExtended, If theIf) {
        if (theIf.getType() != IfType.EQ && theIf.getType() != IfType.NE) {
          // Not an extension of `toBeExtended`.
          return toBeExtended;
        }

        Value stringHashValue = Utils.getStringHashValueFromIf(theIf, dexItemFactory);
        if (stringHashValue == null
            || (toBeExtended != null
                && !Utils.isSameStringHashValue(stringHashValue, toBeExtended.stringHashValue))) {
          // Not an extension of `toBeExtended`.
          return toBeExtended;
        }

        // Now read the constant value that the hash is being compared to in this if-instruction.
        int hash;
        if (theIf.isZeroTest()) {
          hash = 0;
        } else {
          Value other = stringHashValue == theIf.lhs() ? theIf.rhs() : theIf.lhs();
          Value root = other.getAliasedValue();
          if (root.isPhi() || !root.definition.isConstNumber()) {
            // Not an extension of `toBeExtended`.
            return toBeExtended;
          }
          ConstNumber constNumberInstruction = root.definition.asConstNumber();
          hash = constNumberInstruction.getIntValue();
        }

        // Go into the true-target and record a mapping from all strings to their id.
        Reference2IntMap<DexString> extension = new Reference2IntLinkedOpenHashMap<>();
        BasicBlock ifEqualsHashTarget = Utils.getTrueTarget(theIf);
        if (!addMappingsForStringsWithHash(ifEqualsHashTarget, hash, extension)) {
          // Not a valid extension of `toBeExtended`.
          return toBeExtended;
        }

        if (toBeExtended == null) {
          toBeExtended = new StringToIdMapping(stringHashValue, dexItemFactory);
        }

        // Commit the extension to `toBeExtended`.
        for (DexString key : extension.keySet()) {
          toBeExtended.mapping.put(key, extension.getInt(key));
        }
        toBeExtended.insertionBlock = theIf.getBlock();
        return extendWithPredecessor(toBeExtended, theIf.getBlock());
      }

      private StringToIdMapping extendWithSwitch(
          StringToIdMapping toBeExtended, IntSwitch theSwitch) {
        Value stringHashValue = Utils.getStringHashValueFromSwitch(theSwitch, dexItemFactory);
        if (stringHashValue == null
            || (toBeExtended != null
                && !Utils.isSameStringHashValue(stringHashValue, toBeExtended.stringHashValue))) {
          // Not an extension of `toBeExtended`.
          return toBeExtended;
        }

        // Go into each switch case and record a mapping from all strings to their id.
        Reference2IntMap<DexString> extension = new Reference2IntLinkedOpenHashMap<>();
        for (int i = 0; i < theSwitch.numberOfKeys(); i++) {
          int hash = theSwitch.getKey(i);
          BasicBlock equalsHashTarget = theSwitch.targetBlock(i);
          if (!addMappingsForStringsWithHash(equalsHashTarget, hash, extension)) {
            // Not an extension of `toBeExtended`.
            return toBeExtended;
          }
        }

        if (toBeExtended == null) {
          toBeExtended = new StringToIdMapping(stringHashValue, dexItemFactory);
        }

        // Commit the extension to `toBeExtended`.
        for (DexString key : extension.keySet()) {
          toBeExtended.mapping.put(key, extension.getInt(key));
        }
        toBeExtended.insertionBlock = theSwitch.getBlock();
        return extendWithPredecessor(toBeExtended, theSwitch.getBlock());
      }

      private boolean addMappingsForStringsWithHash(
          BasicBlock block, int hash, Reference2IntMap<DexString> extension) {
        return addMappingsForStringsWithHash(block, hash, extension, Sets.newIdentityHashSet());
      }

      private boolean addMappingsForStringsWithHash(
          BasicBlock block,
          int hash,
          Reference2IntMap<DexString> extension,
          Set<BasicBlock> visited) {
        InstructionIterator instructionIterator = block.iterator();

        // The first instruction is expected to be a non-throwing const-string instruction, but this
        // may change due to canonicalization. If the string throws, it can't be decoded, and then
        // the string does not have a hash.
        Instruction first = instructionIterator.next();
        ConstString optionalString = first.asConstString();
        if (optionalString != null && optionalString.instructionInstanceCanThrow()) {
          return false;
        }

        // The next instruction must be an invoke-virtual that calls stringValue.equals() with a
        // constant string argument.
        InvokeVirtual theInvoke =
            first.isConstString()
                ? instructionIterator.next().asInvokeVirtual()
                : first.asInvokeVirtual();
        if (theInvoke == null
            || theInvoke.getInvokedMethod() != dexItemFactory.stringMembers.equals
            || theInvoke.getReceiver() != stringValue) {
          return false;
        }

        // If this block starts with a const-string instruction, then it should be passed as the
        // second argument to equals().
        if (optionalString != null && theInvoke.getArgument(1) != optionalString.outValue()) {
          assert false; // This should generally not happen.
          return false;
        }

        Value theString = theInvoke.getArgument(1).getAliasedValue();
        if (!theString.isDefinedByInstructionSatisfying(Instruction::isConstString)) {
          return false;
        }

        If theIf = instructionIterator.next().asIf();
        if (theIf == null) {
          return false;
        }
        if (theIf.getType() != IfType.EQ && theIf.getType() != IfType.NE) {
          return false;
        }

        try {
          DexString theStringValue = theString.definition.asConstString().getValue();
          if (theStringValue.decodedHashCode() == hash) {
            BasicBlock trueTarget = theIf.targetFromCondition(1);
            if (!addMappingForString(trueTarget, theStringValue, extension)) {
              return false;
            }
          }
        } catch (UTFDataFormatException e) {
          // It is already guaranteed that the string does not throw.
          throw new Unreachable();
        }

        BasicBlock fallthroughBlock = theIf.targetFromCondition(0).endOfGotoChain();
        if (fallthroughBlock == continuationBlock) {
          // Success, this actually jumps to the block where the id-to-target mapping starts.
          return true;
        }
        if (visited.add(fallthroughBlock)) {
          // Continue pattern matching from the fallthrough block.
          return addMappingsForStringsWithHash(fallthroughBlock, hash, extension, visited);
        }
        // Cyclic fallthrough chain.
        return false;
      }

      private boolean addMappingForString(
          BasicBlock block, DexString string, Reference2IntMap<DexString> extension) {
        InstructionIterator instructionIterator = block.iterator();
        ConstNumber constNumberInstruction;
        if (block.isTrivialGoto()) {
          if (block.getUniqueNormalSuccessor() != intermediateIdValue.getBlock()) {
            return false;
          }
          int predecessorIndex = intermediateIdValue.getBlock().getPredecessors().indexOf(block);
          constNumberInstruction =
              asConstNumberOrNull(intermediateIdValue.getOperand(predecessorIndex).definition);
        } else {
          constNumberInstruction = instructionIterator.next().asConstNumber();
        }
        if (constNumberInstruction == null
            || !intermediateIdValue.getOperands().contains(constNumberInstruction.outValue())) {
          return false;
        }

        Goto gotoContinuationBlock = instructionIterator.next().asGoto();
        if (gotoContinuationBlock == null
            || gotoContinuationBlock.getTarget().endOfGotoChain() != continuationBlock) {
          return false;
        }

        extension.putIfAbsent(string, constNumberInstruction.getIntValue());
        return true;
      }
    }

    // The block where the new string-switch instruction needs to be inserted.
    BasicBlock insertionBlock;

    // The hash value of interest.
    private final Value stringHashValue;

    private final Reference2IntMap<DexString> mapping = new Reference2IntLinkedOpenHashMap<>();

    private StringToIdMapping(Value stringHashValue, DexItemFactory dexItemFactory) {
      assert isDefinedByStringHashCode(stringHashValue, dexItemFactory);
      this.stringHashValue = stringHashValue;
    }

    static Builder builder(
        BasicBlock continuationBlock,
        DexItemFactory dexItemFactory,
        Phi idValue,
        Value stringValue) {
      return new Builder(continuationBlock, dexItemFactory, idValue, stringValue);
    }
  }

  static class IdToTargetMapping {

    static class Builder {

      // Attempts to build a mapping from string ids to target blocks from the given block. The
      // given block is expected to be the "root" of the id-comparisons.
      //
      // If the given block (and its successor blocks) is on the form described by the following
      // grammar, then a non-empty mapping is returned. Otherwise, null is returned.
      //
      //   ID_TO_TARGET_MAPPING :=
      //           if (result == <const-number>) {
      //             ...
      //           } else {
      //             [[ID_TO_TARGET_MAPPING]]
      //           }
      //         | switch (result) {
      //             case <const-number>:
      //               ...; break;
      //             case <const-number>:
      //               ...; break;
      //             ...
      //             default:
      //               [[ID_TO_TARGET_MAPPING]]; break;
      //           }
      //         | [[EXIT]]
      //
      //   EXIT := <any block>
      IdToTargetMapping build(BasicBlock block) {
        return extend(null, block);
      }

      private static IdToTargetMapping setFallthroughBlock(
          IdToTargetMapping toBeExtended, BasicBlock fallthroughBlock) {
        if (toBeExtended != null) {
          toBeExtended.fallthroughBlock = fallthroughBlock;
        }
        return toBeExtended;
      }

      private IdToTargetMapping extend(IdToTargetMapping toBeExtended, BasicBlock block) {
        BasicBlock end = block.endOfGotoChain();
        if (end == null) {
          // Not an extension of `toBeExtended` (cyclic goto chain).
          return setFallthroughBlock(toBeExtended, block);
        }
        int numberOfInstructions = end.getInstructions().size();
        if (numberOfInstructions == 1) {
          JumpInstruction exit = end.exit();
          if (exit.isIf()) {
            return extendWithIf(toBeExtended, exit.asIf(), block);
          }
          if (exit.isIntSwitch()) {
            return extendWithSwitch(toBeExtended, exit.asIntSwitch(), block);
          }
        }
        if (numberOfInstructions == 2) {
          Instruction entry = end.entry();
          Instruction exit = end.exit();
          if (entry.isConstNumber() && entry.outValue().onlyUsedInBlock(end) && exit.isIf()) {
            return extendWithIf(toBeExtended, exit.asIf(), block);
          }
        }
        // Not an extension of `toBeExtended`.
        return setFallthroughBlock(toBeExtended, block);
      }

      private IdToTargetMapping extendWithIf(
          IdToTargetMapping toBeExtended, If theIf, BasicBlock fallthroughBlock) {
        IfType type = theIf.getType();
        if (type != IfType.EQ && type != IfType.NE) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, fallthroughBlock);
        }

        // Read the `id` value. This value is known to be a phi, so just look for a phi.
        Phi idValue = null;
        Value lhs = theIf.lhs();
        if (lhs.isPhi()) {
          idValue = lhs.asPhi();
        } else if (!theIf.isZeroTest()) {
          Value rhs = theIf.rhs();
          if (rhs.isPhi()) {
            idValue = rhs.asPhi();
          }
        }

        if (idValue == null
            || !idValue.getType().isInt()
            || (toBeExtended != null && idValue != toBeExtended.idValue)) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, fallthroughBlock);
        }

        // Now read the constant value that `id` is being compared to in this if-instruction.
        int id;
        if (theIf.isZeroTest()) {
          id = 0;
        } else {
          Value other = idValue == theIf.lhs() ? theIf.rhs() : theIf.lhs();
          Value root = other.getAliasedValue();
          if (root.isPhi() || !root.definition.isConstNumber()) {
            // Not an extension of `toBeExtended`.
            return setFallthroughBlock(toBeExtended, fallthroughBlock);
          }
          ConstNumber constNumberInstruction = root.definition.asConstNumber();
          id = constNumberInstruction.getIntValue();
        }

        if (toBeExtended == null) {
          toBeExtended = new IdToTargetMapping(idValue);
        }

        // Extend the current mapping. Intentionally using putIfAbsent to prevent that dead code
        // becomes live.
        toBeExtended.mapping.putIfAbsent(id, Utils.getTrueTarget(theIf));
        return extend(toBeExtended, Utils.fallthroughBlock(theIf));
      }

      private IdToTargetMapping extendWithSwitch(
          IdToTargetMapping toBeExtended, IntSwitch theSwitch, BasicBlock fallthroughBlock) {
        Value switchValue = theSwitch.value();
        if (!switchValue.isPhi() || (toBeExtended != null && switchValue != toBeExtended.idValue)) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, fallthroughBlock);
        }

        Phi idValue = switchValue.asPhi();
        if (toBeExtended == null) {
          toBeExtended = new IdToTargetMapping(idValue);
        }

        // Extend the current mapping. Intentionally using putIfAbsent to prevent that dead code
        // becomes live.
        theSwitch.forEachCase(toBeExtended.mapping::putIfAbsent);
        return extend(toBeExtended, theSwitch.fallthroughBlock());
      }
    }

    private BasicBlock fallthroughBlock;
    private final Phi idValue;
    private final Int2ReferenceMap<BasicBlock> mapping = new Int2ReferenceOpenHashMap<>();

    private IdToTargetMapping(Phi idValue) {
      this.idValue = idValue;
    }

    static Builder builder() {
      return new Builder();
    }
  }

  static class Utils {

    static BasicBlock getTrueTarget(If theIf) {
      assert theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE;
      return theIf.getType() == IfType.EQ ? theIf.getTrueTarget() : theIf.fallthroughBlock();
    }

    static BasicBlock fallthroughBlock(JumpInstruction exit) {
      if (exit.isIf()) {
        If theIf = exit.asIf();
        return theIf.getType() == IfType.EQ ? theIf.fallthroughBlock() : theIf.getTrueTarget();
      }
      if (exit.isIntSwitch()) {
        return exit.asIntSwitch().fallthroughBlock();
      }
      throw new Unreachable();
    }

    static Value getStringHashValueFromJump(
        JumpInstruction instruction, DexItemFactory dexItemFactory) {
      if (instruction.isIf()) {
        return getStringHashValueFromIf(instruction.asIf(), dexItemFactory);
      }
      if (instruction.isIntSwitch()) {
        return getStringHashValueFromSwitch(instruction.asIntSwitch(), dexItemFactory);
      }
      return null;
    }

    static Value getStringHashValueFromIf(If theIf, DexItemFactory dexItemFactory) {
      Value lhs = theIf.lhs();
      if (isDefinedByStringHashCode(lhs, dexItemFactory)) {
        return lhs;
      } else if (!theIf.isZeroTest()) {
        Value rhs = theIf.rhs();
        if (isDefinedByStringHashCode(rhs, dexItemFactory)) {
          return rhs;
        }
      }
      return null;
    }

    static Value getStringHashValueFromSwitch(IntSwitch theSwitch, DexItemFactory dexItemFactory) {
      Value switchValue = theSwitch.value();
      if (isDefinedByStringHashCode(switchValue, dexItemFactory)) {
        return switchValue;
      }
      return null;
    }

    static Value getStringValueFromHashValue(Value stringHashValue, DexItemFactory dexItemFactory) {
      assert isDefinedByStringHashCode(stringHashValue, dexItemFactory);
      return stringHashValue.definition.asInvokeVirtual().getReceiver();
    }

    static boolean isComparisonOfStringHashValue(
        JumpInstruction instruction, DexItemFactory dexItemFactory) {
      return getStringHashValueFromJump(instruction, dexItemFactory) != null;
    }

    static boolean isSameStringHashValue(Value value, Value other) {
      return value == other
          || value.definition.asInvokeVirtual().getReceiver()
              == other.definition.asInvokeVirtual().getReceiver();
    }
  }
}
