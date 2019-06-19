// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.List;

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
class StringSwitchConverter {

  static void convertToStringSwitchInstructions(IRCode code, DexItemFactory dexItemFactory) {
    List<BasicBlock> rewritingCandidates = getRewritingCandidates(code, dexItemFactory);
    if (rewritingCandidates != null) {
      boolean changed = false;
      for (BasicBlock block : rewritingCandidates) {
        if (convertRewritingCandidateToStringSwitchInstruction(block)) {
          changed = true;
        }
      }
      if (changed) {
        code.hasStringSwitch = true;
        code.removeAllTrivialPhis();
        code.removeUnreachableBlocks();
      }
    }
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

  private static boolean convertRewritingCandidateToStringSwitchInstruction(BasicBlock block) {
    StringSwitchBuilderInfo info = StringSwitchBuilderInfo.builder().build(block);
    if (info != null) {
      info.createAndInsertStringSwitch();
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
        && definition.asInvokeVirtual().getInvokedMethod() == dexItemFactory.stringMethods.hashCode;
  }

  static class StringSwitchBuilderInfo {

    static class Builder {

      private Builder() {}

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

        // TODO(b/135559645): Build mapping from every string key of the switch to its id, in order
        //  to be able to build the string-switch instruction.
        return null;
      }
    }

    static Builder builder() {
      return new Builder();
    }

    void createAndInsertStringSwitch() {
      // TODO(b/135559645): Add StringSwitch instruction to IR.
      throw new Unimplemented();
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
            return extendWithIf(toBeExtended, exit.asIf());
          }
          if (exit.isSwitch()) {
            return extendWithSwitch(toBeExtended, exit.asSwitch());
          }
        }
        if (numberOfInstructions == 2) {
          Instruction entry = end.entry();
          Instruction exit = end.exit();
          if (entry.isConstNumber() && entry.outValue().onlyUsedInBlock(end) && exit.isIf()) {
            return extendWithIf(toBeExtended, exit.asIf());
          }
        }
        // Not an extension of `toBeExtended`.
        return setFallthroughBlock(toBeExtended, block);
      }

      private IdToTargetMapping extendWithIf(IdToTargetMapping toBeExtended, If theIf) {
        If.Type type = theIf.getType();
        if (type != If.Type.EQ && type != If.Type.NE) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, theIf.getBlock());
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

        if (idValue == null || (toBeExtended != null && idValue != toBeExtended.idValue)) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, theIf.getBlock());
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
            return setFallthroughBlock(toBeExtended, theIf.getBlock());
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

      private IdToTargetMapping extendWithSwitch(IdToTargetMapping toBeExtended, Switch theSwitch) {
        Value switchValue = theSwitch.value();
        if (!switchValue.isPhi() || (toBeExtended != null && switchValue != toBeExtended.idValue)) {
          // Not an extension of `toBeExtended`.
          return setFallthroughBlock(toBeExtended, theSwitch.getBlock());
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
      assert theIf.getType() == If.Type.EQ || theIf.getType() != If.Type.NE;
      return theIf.getType() == If.Type.EQ ? theIf.getTrueTarget() : theIf.fallthroughBlock();
    }

    static BasicBlock fallthroughBlock(JumpInstruction exit) {
      if (exit.isIf()) {
        If theIf = exit.asIf();
        return theIf.getType() == If.Type.EQ ? theIf.fallthroughBlock() : theIf.getTrueTarget();
      }
      if (exit.isSwitch()) {
        return exit.asSwitch().fallthroughBlock();
      }
      throw new Unreachable();
    }

    static Value getStringHashValueFromJump(
        JumpInstruction instruction, DexItemFactory dexItemFactory) {
      if (instruction.isIf()) {
        return getStringHashValueFromIf(instruction.asIf(), dexItemFactory);
      }
      if (instruction.isSwitch()) {
        return getStringHashValueFromSwitch(instruction.asSwitch(), dexItemFactory);
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

    static Value getStringHashValueFromSwitch(Switch theSwitch, DexItemFactory dexItemFactory) {
      Value switchValue = theSwitch.value();
      if (isDefinedByStringHashCode(switchValue, dexItemFactory)) {
        return switchValue;
      }
      return null;
    }

    static boolean isComparisonOfStringHashValue(
        JumpInstruction instruction, DexItemFactory dexItemFactory) {
      return getStringHashValueFromJump(instruction, dexItemFactory) != null;
    }
  }
}
