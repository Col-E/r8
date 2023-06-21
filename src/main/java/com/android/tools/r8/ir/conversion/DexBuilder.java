// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import com.android.tools.r8.dex.code.DexFormat31t;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexGoto16;
import com.android.tools.r8.dex.code.DexGoto32;
import com.android.tools.r8.dex.code.DexIfEq;
import com.android.tools.r8.dex.code.DexIfEqz;
import com.android.tools.r8.dex.code.DexIfGe;
import com.android.tools.r8.dex.code.DexIfGez;
import com.android.tools.r8.dex.code.DexIfGt;
import com.android.tools.r8.dex.code.DexIfGtz;
import com.android.tools.r8.dex.code.DexIfLe;
import com.android.tools.r8.dex.code.DexIfLez;
import com.android.tools.r8.dex.code.DexIfLt;
import com.android.tools.r8.dex.code.DexIfLtz;
import com.android.tools.r8.dex.code.DexIfNe;
import com.android.tools.r8.dex.code.DexIfNez;
import com.android.tools.r8.dex.code.DexInstanceOf;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMove;
import com.android.tools.r8.dex.code.DexMove16;
import com.android.tools.r8.dex.code.DexMoveFrom16;
import com.android.tools.r8.dex.code.DexMoveObject;
import com.android.tools.r8.dex.code.DexMoveObject16;
import com.android.tools.r8.dex.code.DexMoveObjectFrom16;
import com.android.tools.r8.dex.code.DexMoveWide;
import com.android.tools.r8.dex.code.DexMoveWide16;
import com.android.tools.r8.dex.code.DexMoveWideFrom16;
import com.android.tools.r8.dex.code.DexNop;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexDebugEventBuilder;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadata;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.TrivialGotosCollapser;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOutputMode;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Builder object for constructing dex bytecode from the high-level IR.
 */
public class DexBuilder {

  public final AppView<?> appView;

  // The IR representation of the code to build.
  private final IRCode ir;

  // Extra information that should be attached to the bytecode instructions.
  private final BytecodeMetadata.Builder<DexInstruction> bytecodeMetadataBuilder;

  // The register allocator providing register assignments for the code to build.
  private final RegisterAllocator registerAllocator;

  private final InternalOptions options;
  private final MethodConversionOptions conversionOptions;

  // List of information about switch payloads that have to be created at the end of the
  // dex code.
  private final List<SwitchPayloadInfo> switchPayloadInfos = new ArrayList<>();

  // List of generated FillArrayData dex instructions.
  private final List<FillArrayDataInfo> fillArrayDataInfos = new ArrayList<>();

  // Set of if instructions that have offsets that are so large that they cannot be encoded in
  // the if instruction format.
  private final Set<BasicBlock> ifsNeedingRewrite = Sets.newIdentityHashSet();

  // Running bounds on offsets.
  private int maxOffset = 0;
  private int minOffset = 0;

  // Mapping from IR instructions to info for computing the dex translation. Use the
  // getInfo/setInfo methods to access the mapping.
  private Info[] instructionToInfo;

  // Keeps track of the previous non-fallthrough info added to the dex builder.
  private Info previousNonFallthroughInfo;

  // The number of ingoing and outgoing argument registers for the code.
  private int inRegisterCount = 0;
  private int outRegisterCount = 0;

  // Whether or not the generated code has a backwards branch.
  private boolean hasBackwardsBranch = false;

  BasicBlock nextBlock;

  public DexBuilder(
      IRCode ir,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      RegisterAllocator registerAllocator,
      InternalOptions options) {
    this(
        ir,
        bytecodeMetadataProvider,
        registerAllocator,
        options,
        ir.getConversionOptions());
  }

  public DexBuilder(
      IRCode ir,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      RegisterAllocator registerAllocator,
      InternalOptions options,
      MethodConversionOptions conversionOptions) {
    assert ir == null || conversionOptions == ir.getConversionOptions();
    this.appView = registerAllocator.getAppView();
    this.ir = ir;
    this.bytecodeMetadataBuilder = BytecodeMetadata.builder(bytecodeMetadataProvider);
    this.registerAllocator = registerAllocator;
    this.options = options;
    this.conversionOptions = conversionOptions;
    if (isBuildingForComparison()) {
      instructionToInfo = new Info[1];
    }
  }

  public static boolean identicalInstructionsAfterBuildingDexCode(
      com.android.tools.r8.ir.code.Instruction a,
      com.android.tools.r8.ir.code.Instruction b,
      RegisterAllocator allocator,
      MethodConversionOptions conversionOptions) {
    DexBuilder builder =
        new DexBuilder(
            null,
            BytecodeMetadataProvider.empty(),
            allocator,
            allocator.options(),
            conversionOptions);
    Info infoA = buildInfoForComparison(a, builder);
    Info infoB = buildInfoForComparison(b, builder);
    return infoA.identicalInstructions(infoB, builder);
  }

  private static Info buildInfoForComparison(
      com.android.tools.r8.ir.code.Instruction instruction, DexBuilder builder) {
    instruction.buildDex(builder);
    assert builder.instructionToInfo.length == 1;
    return builder.instructionToInfo[0];
  }

  private boolean isBuildingForComparison() {
    return ir == null;
  }

  private void reset() {
    switchPayloadInfos.clear();
    fillArrayDataInfos.clear();
    ifsNeedingRewrite.clear();
    maxOffset = 0;
    minOffset = 0;
    instructionToInfo = new Info[instructionNumberToIndex(ir.numberRemainingInstructions())];
    inRegisterCount = 0;
    outRegisterCount = 0;
    nextBlock = null;
  }

  /**
   * Build the dex instructions added to this builder.
   *
   * This is a two pass construction that will first compute concrete offsets and then construct
   * the concrete instructions.
   */
  public DexCode build() {
    int numberOfInstructions;
    int offset;

    do {
      // Rewrite ifs that are know from the previous iteration to have offsets that are too
      // large for the if encoding.
      rewriteIfs();

      // Remove redundant debug position instructions. They would otherwise materialize as
      // unnecessary nops.
      removeRedundantDebugPositions(ir);

      // Reset the state of the builder to start from scratch.
      reset();

      // Populate the builder info objects.
      numberOfInstructions = 0;

      ListIterator<BasicBlock> iterator = ir.listIterator();
      assert iterator.hasNext();
      BasicBlock block = iterator.next();
      do {
        nextBlock = iterator.hasNext() ? iterator.next() : null;
        block.buildDex(this);
        block = nextBlock;
      } while (block != null);

      // Compute offsets.
      offset = 0;
      for (com.android.tools.r8.ir.code.Instruction instruction : ir.instructions()) {
        Info info = getInfo(instruction);
        info.setOffset(offset);
        offset += info.computeSize(this);
        ++numberOfInstructions;
      }
    } while (!ifsNeedingRewrite.isEmpty());

    // Build instructions.
    DexDebugEventBuilder debugEventBuilder = new DexDebugEventBuilder(ir, options);
    List<DexInstruction> dexInstructions = new ArrayList<>(numberOfInstructions);
    int instructionOffset = 0;
    for (com.android.tools.r8.ir.code.Instruction irInstruction : ir.instructions()) {
      Info info = getInfo(irInstruction);
      int previousInstructionCount = dexInstructions.size();
      info.addInstructions(this, dexInstructions);
      int instructionStartOffset = instructionOffset;
      while (previousInstructionCount < dexInstructions.size()) {
        DexInstruction dexInstruction = dexInstructions.get(previousInstructionCount++);
        dexInstruction.setOffset(instructionOffset);
        instructionOffset += dexInstruction.getSize();
      }
      debugEventBuilder.add(instructionStartOffset, instructionOffset, irInstruction);
    }

    // Workaround dalvik tracing bug, where the dalvik tracing JIT can end up tracing
    // past the end of the instruction stream if the instruction streams ends with a throw.
    // See: b/117907456
    // We could have also changed the block order, however, moving the throwing block higher
    // led to larger code in all experiments (multiple gmscore version and R8 run on itself).
    if (options.canHaveTracingPastInstructionsStreamBug()
        && dexInstructions.get(dexInstructions.size() - 1) instanceof DexThrow
        && hasBackwardsBranch) {
      // This is the last in a series of different workarounds tried out.
      // Having an empty non reachable loop make some mediatek vms crash: b/119895393
      // Having a (unreachable) return null/return-void (type correct) trips up the constant
      // propagation in some vms: b/121355317
      // Having always a (unreachable) return-void causes Mediatek 4.4.2/4.4.4 to crash trying
      // to get a dominator for the unreachable code: b/128926846
      // Current implementation generates code that jumps over the throw, and then back to the throw
      // again.
      // throw v10
      // becomes:
      // goto +2
      // throw v10
      // goto -1
      // That way we have no unreachable code, and we never end in a throw. The tracer will still
      // trace to the throw, but after moving to the second goto it will trace back again and see
      // an instruction it has already seen.
      DexInstruction throwInstruction = dexInstructions.get(dexInstructions.size() - 1);
      offset = throwInstruction.getOffset();

      // Generate the new forward and backward gotos, update offsets.
      DexInstruction forward = new DexGoto(throwInstruction.getSize() + DexGoto.SIZE);
      DexInstruction backward = new DexGoto(-throwInstruction.getSize());
      forward.setOffset(offset);
      offset += forward.getSize();
      throwInstruction.setOffset(offset);
      offset += throwInstruction.getSize();
      backward.setOffset(offset);
      offset += backward.getSize();
      // Replace the throw in the instruction stream with goto(forward), throw, goto(backwards)
      dexInstructions.remove(dexInstructions.size()-1);
      dexInstructions.add(forward);
      dexInstructions.add(throwInstruction);
      dexInstructions.add(backward);
    }

    // Compute switch payloads.
    for (SwitchPayloadInfo switchPayloadInfo : switchPayloadInfos) {
      // Align payloads at even addresses.
      if (offset % 2 != 0) {
        DexNop nop = new DexNop();
        nop.setOffset(offset++);
        dexInstructions.add(nop);
      }
      // Create payload and add it to the instruction stream.
      DexNop payload = createSwitchPayload(switchPayloadInfo, offset);
      payload.setOffset(offset);
      offset += payload.getSize();
      dexInstructions.add(payload);
    }

    // Compute fill array data payloads.
    for (FillArrayDataInfo info : fillArrayDataInfos) {
      // Align payloads at even addresses.
      if (offset % 2 != 0) {
        DexNop nop = new DexNop();
        nop.setOffset(offset++);
        dexInstructions.add(nop);
      }
      // Create payload and add it to the instruction stream.
      DexFillArrayDataPayload payload = info.ir.createPayload();
      payload.setOffset(offset);
      info.dex.setPayloadOffset(offset - info.dex.getOffset());
      offset += payload.getSize();
      dexInstructions.add(payload);
    }

    // Construct try-catch info.
    TryInfo tryInfo = computeTryInfo();

    // Return the dex code.
    DexCode code =
        new DexCode(
            registerAllocator.registersUsed(),
            inRegisterCount,
            outRegisterCount,
            dexInstructions.toArray(DexInstruction.EMPTY_ARRAY),
            tryInfo.tries,
            tryInfo.handlers,
            debugEventBuilder.build(),
            bytecodeMetadataBuilder.build());

    return code;
  }

  private static boolean isTrivialFallthroughTarget(
      BasicBlock previousBlock, BasicBlock currentBlock) {
    return previousBlock.exit().isGoto()
        && currentBlock.getPredecessors().size() == 1
        && currentBlock.getPredecessors().get(0) == previousBlock;
  }

  private static void removeTrivialGotoBlocks(IRCode code) {
    for (int blockIndex = 1; blockIndex < code.blocks.size(); blockIndex++) {
      // We skip checking the entry block as it has no predecessors and must define the initial
      // position. Any subsequent block must be statically reachable and thus have predecessors.
      BasicBlock currentBlock = code.blocks.get(blockIndex);
      assert !currentBlock.getPredecessors().isEmpty();
      if (currentBlock.size() != 2) {
        continue;
      }
      DebugPosition debugPosition = currentBlock.entry().asDebugPosition();
      com.android.tools.r8.ir.code.Goto exit = currentBlock.exit().asGoto();
      if (debugPosition == null || exit == null || debugPosition.getPosition().isNone()) {
        continue;
      }
      boolean allMatch = true;
      Position position = debugPosition.getPosition();
      for (BasicBlock pred : currentBlock.getPredecessors()) {
        // If the block is a trivial loop it must remain.
        if (pred == currentBlock) {
          allMatch = false;
          break;
        }
        // If the position is already active on each predecessor exit it can be safely removed
        // (except for if/switch fallthrough cases guarded below).
        Position predExit = pred.exit().getPosition();
        if (!position.equals(predExit)) {
          allMatch = false;
          break;
        }
        // If this is a required fallthrough, we can only remove it if it targets the next block.
        // Note that this could fail for a given block but then become valid after an intermediate
        // block is removed. See the reset of blockIndex below for dealing with that case.
        if (isFallthroughTargetToNonFallthroughTarget(pred, currentBlock, blockIndex, code)) {
          allMatch = false;
          break;
        }
      }
      if (allMatch) {
        currentBlock.removeInstruction(debugPosition);
        TrivialGotosCollapser.unlinkTrivialGotoBlock(currentBlock, exit.getTarget());
        code.removeBlocks(Collections.singleton(currentBlock));
        // Having removed the block at blockIndex, the previous block may now be a trivial
        // fallthrough from an if/switch. Rewind to that point and retry. This avoids iterating to
        // a fixed point.
        blockIndex = Math.max(0, blockIndex - 2);
      }
    }
  }

  private static boolean isFallthroughTargetToNonFallthroughTarget(
      BasicBlock pred, BasicBlock current, int blockIndex, IRCode code) {
    JumpInstruction exit = pred.exit();
    BasicBlock fallthrough;
    if (exit.isIf()) {
      fallthrough = exit.asIf().fallthroughBlock();
    } else if (exit.isSwitch()) {
      fallthrough = exit.asSwitch().fallthroughBlock();
    } else {
      return false;
    }
    if (fallthrough != current) {
      return false;
    }
    if (blockIndex + 1 >= code.blocks.size()) {
      // The current block is a if/switch fallthrough target and there is no next-block.
      // The current jump is thus to a non-fallthrough block.
      return true;
    }
    BasicBlock nextBlock = code.blocks.get(blockIndex + 1);
    return current.exit().asGoto().getTarget() != nextBlock;
  }

  // Eliminates unneeded debug positions.
  //
  // After this pass all remaining debug positions mark places where we must ensure a materializing
  // instruction, eg, for two successive lines without intermediate instructions.
  public static void removeRedundantDebugPositions(IRCode code) {
    if (!code.metadata().mayHaveDebugPosition()) {
      return;
    }

    // We must start by removing any blocks that are already trivial fallthrough blocks with no
    // position change. With these removed it is then sound to make the fallthrough judgement when
    // determining if a goto will materialize or not.
    removeTrivialGotoBlocks(code);

    // Current position known to have a materializing instruction associated with it.
    Position currentMaterializedPosition = Position.none();

    // Current debug-position marker that is not yet known to have another instruction materializing
    // to the same position.
    DebugPosition unresolvedPosition = null;

    // Locals live at the debug-position marker. These must also be the same at a possible
    // materializing instruction with the same position for it to be sound to remove the marker.
    Int2ReferenceMap<DebugLocalInfo> localsAtUnresolvedPosition = null;

    // Compute the set of all positions that can be removed.
    // (Delaying removal to avoid ConcurrentModificationException).
    List<DebugPosition> toRemove = new ArrayList<>();

    for (int blockIndex = 0; blockIndex < code.blocks.size(); blockIndex++) {
      BasicBlock currentBlock = code.blocks.get(blockIndex);

      // Current materialized position must be updated to the position we can guarantee is emitted
      // in all predecessors. The position of a fallthrough predecessor is defined by
      // currentMaterializedPosition and unresolvedPosition (and not by the position of its exit!)
      // If this is the entry block or a trivial fall-through with no other predecessors the
      // materialized and unresolved positions remain unchanged.
      if (blockIndex != 0) {
        BasicBlock previousBlock = code.blocks.get(blockIndex - 1);
        if (!isTrivialFallthroughTarget(previousBlock, currentBlock)) {
          Position positionAtAllPredecessors = null;
          for (BasicBlock pred : currentBlock.getPredecessors()) {
            Position predExit;
            if (pred == previousBlock) {
              predExit =
                  unresolvedPosition != null
                      ? unresolvedPosition.getPosition()
                      : currentMaterializedPosition;
            } else {
              predExit = pred.exit().getPosition();
            }
            if (positionAtAllPredecessors == null) {
              positionAtAllPredecessors = predExit;
            } else if (!positionAtAllPredecessors.equals(predExit)) {
              positionAtAllPredecessors = Position.none();
              break;
            }
          }
          unresolvedPosition = null;
          currentMaterializedPosition = positionAtAllPredecessors;
        }
      }

      // Current locals.
      Int2ReferenceMap<DebugLocalInfo> locals =
          currentBlock.getLocalsAtEntry() != null
              ? new Int2ReferenceOpenHashMap<>(currentBlock.getLocalsAtEntry())
              : new Int2ReferenceOpenHashMap<>();

      // Next block to decide which gotos are fall-throughs.
      BasicBlock nextBlock =
          blockIndex + 1 < code.blocks.size() ? code.blocks.get(blockIndex + 1) : null;

      for (com.android.tools.r8.ir.code.Instruction instruction : currentBlock.getInstructions()) {
        if (instruction.isDebugPosition()) {
          if (unresolvedPosition == null
              && currentMaterializedPosition == instruction.getPosition()) {
            // Here we don't need to check locals state as the line is already active.
            toRemove.add(instruction.asDebugPosition());
            assert currentBlock.size() != 2
                    || currentBlock.exit().getPosition() != currentMaterializedPosition
                    || !currentBlock.exit().isGoto()
                    || currentBlock.exit().asGoto().getTarget() != nextBlock
                : "Unexpected trivial fallthrough block. This should be removed already.";
          } else if (unresolvedPosition != null
              && unresolvedPosition.getPosition() == instruction.getPosition()
              && locals.equals(localsAtUnresolvedPosition)) {
            // toRemove needs to be in instruction iteration order since the removal assumes that.
            // Therefore, we have to remove unresolvedPosition here and record the current
            // instruction as unresolved. Otherwise, if both of these instructions end up in
            // toRemove they will be out of order.
            toRemove.add(unresolvedPosition);
            unresolvedPosition = instruction.asDebugPosition();
          } else {
            unresolvedPosition = instruction.asDebugPosition();
            localsAtUnresolvedPosition = new Int2ReferenceOpenHashMap<>(locals);
          }
        } else {
          assert instruction.getPosition().isSome();
          if (instruction.isDebugLocalsChange()) {
            instruction.asDebugLocalsChange().apply(locals);
          } else if (!isNopInstruction(instruction, nextBlock)) {
            if (unresolvedPosition != null) {
              if (unresolvedPosition.getPosition() == instruction.getPosition()
                  && locals.equals(localsAtUnresolvedPosition)) {
                toRemove.add(unresolvedPosition);
              }
              unresolvedPosition = null;
              localsAtUnresolvedPosition = null;
            }
            currentMaterializedPosition = instruction.getPosition();
          }
        }
      }
    }
    // Remove all unneeded positions.
    if (!toRemove.isEmpty()) {
      InstructionListIterator it = code.instructionListIterator();
      int i = 0;
      while (it.hasNext() && i < toRemove.size()) {
        if (it.next() == toRemove.get(i)) {
          it.remove();
          ++i;
        }
      }
      assert i == toRemove.size();
    }
  }

  // Rewrite ifs with offsets that are too large for the if encoding. The rewriting transforms:
  //
  //
  // BB0: if condition goto BB_FAR_AWAY
  // BB1: ...
  //
  // to:
  //
  // BB0: if !condition goto BB1
  // BB2: goto BB_FAR_AWAY
  // BB1: ...
  private void rewriteIfs() {
    if (ifsNeedingRewrite.isEmpty()) {
      return;
    }
    ListIterator<BasicBlock> it = ir.listIterator();
    while (it.hasNext()) {
      BasicBlock block = it.next();
      if (ifsNeedingRewrite.contains(block)) {
        If theIf = block.exit().asIf();
        BasicBlock trueTarget = theIf.getTrueTarget();
        BasicBlock newBlock =
            BasicBlock.createGotoBlock(
                ir.getNextBlockNumber(), theIf.getPosition(), ir.metadata(), trueTarget);
        theIf.setTrueTarget(newBlock);
        newBlock.getMutablePredecessors().add(block);
        trueTarget.replacePredecessor(block, newBlock);
        theIf.invert();
        it.add(newBlock);
      }
    }
  }

  private void needsIfRewriting(BasicBlock block) {
    ifsNeedingRewrite.add(block);
  }

  public void requestOutgoingRegisters(int requiredRegisterCount) {
    if (requiredRegisterCount > outRegisterCount) {
      outRegisterCount = requiredRegisterCount;
    }
  }

  public int allocatedRegister(Value value, int instructionNumber) {
    return registerAllocator.getRegisterForValue(value, instructionNumber);
  }

  // Get the argument register for a value if it is an argument, otherwise returns the
  // allocated register at the instruction number.
  public int argumentOrAllocateRegister(Value value, int instructionNumber) {
    return registerAllocator.getArgumentOrAllocateRegisterForValue(value, instructionNumber);
  }

  public void addGoto(com.android.tools.r8.ir.code.Goto jump) {
    if (jump.getTarget() != nextBlock) {
      add(jump, new GotoInfo(jump));
    } else {
      addNothing(jump);
    }
  }

  private boolean needsNopBetweenMoveAndInstanceOf(DexInstanceOf instanceOf) {
    if (!options.canHaveArtInstanceOfVerifierBug()) {
      return false;
    }
    if (previousNonFallthroughInfo instanceof MoveInfo) {
      MoveInfo moveInfo = (MoveInfo) previousNonFallthroughInfo;
      int moveSrcRegister = moveInfo.srcRegister(this);
      int moveDestRegister = moveInfo.destRegister(this);
      // If the previous move materializes as a move and we have the pattern:
      //
      //  move vA, vB
      //  instance-of vB, vA, Type
      //
      // we insert a nop between the move and the instance-of instruction to make sure
      // that we do not trigger a verifier bug in Art. See b/120985556.
      if (moveSrcRegister != moveDestRegister
          && moveSrcRegister == instanceOf.A
          && moveDestRegister == instanceOf.B) {
        return true;
      }
    }
    return false;
  }

  public void addInstanceOf(com.android.tools.r8.ir.code.InstanceOf ir, DexInstanceOf instanceOf) {
    if (needsNopBetweenMoveAndInstanceOf(instanceOf)) {
      add(ir, new DexNop(), instanceOf);
    } else {
      add(ir, instanceOf);
    }
  }

  public void addIf(If branch) {
    assert nextBlock == branch.fallthroughBlock();
    add(branch, new IfInfo(branch));
  }

  public void addMove(Move move) {
    add(move, new MoveInfo(move));
  }

  public void addNothing(com.android.tools.r8.ir.code.Instruction instruction) {
    add(instruction, new FallThroughInfo(instruction));
  }

  private static boolean isNopInstruction(
      com.android.tools.r8.ir.code.Instruction instruction, BasicBlock nextBlock) {
    return instruction.isArgument()
        || instruction.isDebugLocalsChange()
        || isNonMaterializingConstNumber(instruction)
        || (instruction.isGoto() && instruction.asGoto().getTarget() == nextBlock);
  }

  private static boolean isNonMaterializingConstNumber(
      com.android.tools.r8.ir.code.Instruction instruction) {
    return instruction.isConstNumber()
        && !(instruction.outValue().isValueOnStack())
        && !(instruction.outValue().needsRegister());
  }

  public void addNop(com.android.tools.r8.ir.code.Instruction ir) {
    add(ir, new FixedSizeInfo(ir, new DexNop()));
  }

  public void addDebugPosition(DebugPosition position) {
    // Remaining debug positions always require we emit an actual nop instruction.
    // See removeRedundantDebugPositions.
    addNop(position);
  }

  public void add(com.android.tools.r8.ir.code.Instruction instr, DexInstruction dex) {
    assert !instr.isGoto();
    add(instr, new FixedSizeInfo(instr, dex));
    bytecodeMetadataBuilder.setMetadata(instr, dex);
  }

  public void add(com.android.tools.r8.ir.code.Instruction ir, DexInstruction... dex) {
    assert !ir.isGoto();
    add(ir, new MultiFixedSizeInfo(ir, dex));
  }

  public void addSwitch(IntSwitch s, DexFormat31t dex) {
    assert nextBlock == s.fallthroughBlock();
    switchPayloadInfos.add(new SwitchPayloadInfo(s, dex));
    add(s, dex);
  }

  public void addFillArrayData(NewArrayFilledData nafd, DexFillArrayData dex) {
    fillArrayDataInfos.add(new FillArrayDataInfo(nafd, dex));
    add(nafd, dex);
  }

  public void addArgument(Argument argument) {
    inRegisterCount += argument.outValue().requiredRegisters();
    add(argument, new FallThroughInfo(argument));
  }

  public void addReturn(Return ret, DexInstruction dex) {
    if (nextBlock != null
        && ret.identicalAfterRegisterAllocation(
            nextBlock.entry(), registerAllocator, conversionOptions)) {
      addNothing(ret);
    } else {
      add(ret, dex);
    }
  }

  private void add(com.android.tools.r8.ir.code.Instruction ir, Info info) {
    if (isBuildingForComparison()) {
      // We are building for instruction comparison, so just set the info.
      setSingleInfo(info);
      return;
    }
    assert ir != null;
    assert info != null;
    assert getInfo(ir) == null;
    info.setMinOffset(minOffset);
    info.setMaxOffset(maxOffset);
    minOffset += info.minSize();
    maxOffset += info.maxSize();
    setInfo(ir, info);
  }

  public static int instructionNumberToIndex(int instructionNumber) {
    return instructionNumber / IRCode.INSTRUCTION_NUMBER_DELTA;
  }

  // Helper used by the info objects.
  private Info getInfo(com.android.tools.r8.ir.code.Instruction instruction) {
    assert instruction.getNumber() >= 0;
    return instructionToInfo[instructionNumberToIndex(instruction.getNumber())];
  }

  private void setInfo(com.android.tools.r8.ir.code.Instruction instruction, Info info) {
    assert instruction.getNumber() >= 0;
    if (!(info instanceof FallThroughInfo)) {
      previousNonFallthroughInfo = info;
    }
    instructionToInfo[instructionNumberToIndex(instruction.getNumber())] = info;
  }

  private void setSingleInfo(Info info) {
    assert instructionToInfo.length == 1;
    instructionToInfo[0] = info;
  }

  private Info getTargetInfo(BasicBlock block) {
    InstructionIterator iterator = block.iterator();
    com.android.tools.r8.ir.code.Instruction instruction = null;
    while (iterator.hasNext()) {
      instruction = iterator.next();
      Info info = getInfo(instruction);
      if (!(info instanceof FallThroughInfo)) {
        return info;
      }
    }
    assert instruction != null;
    if (instruction.isReturn()) {
      assert getInfo(instruction) instanceof FallThroughInfo;
      return getTargetInfo(computeNextBlock(block));
    }
    assert instruction.isGoto();
    return getTargetInfo(instruction.asGoto().getTarget());
  }

  private BasicBlock computeNextBlock(BasicBlock block) {
    ListIterator<BasicBlock> it = ir.listIterator();
    BasicBlock current = it.next();
    while (current != block) {
      current = it.next();
    }
    return it.next();
  }

  // Helper for computing switch payloads.
  private DexNop createSwitchPayload(SwitchPayloadInfo info, int offset) {
    IntSwitch ir = info.ir;
    // Patch the payload offset in the generated switch instruction now
    // that the location is known.
    info.dex.setPayloadOffset(offset - getInfo(ir).getOffset());
    // Compute target offset for each of the keys based on the offset of the
    // first instruction in the block that the switch goes to for that key.
    int[] targetBlockIndices = ir.targetBlockIndices();
    int[] targets = new int[targetBlockIndices.length];
    for (int i = 0; i < targetBlockIndices.length; i++) {
      BasicBlock targetBlock = ir.targetBlock(i);
      com.android.tools.r8.ir.code.Instruction targetInstruction = targetBlock.entry();
      targets[i] = getInfo(targetInstruction).getOffset() - getInfo(ir).getOffset();
    }
    BasicBlock fallthroughBlock = ir.fallthroughBlock();
    com.android.tools.r8.ir.code.Instruction fallthroughTargetInstruction =
        fallthroughBlock.entry();
    int fallthroughTarget =
        getInfo(fallthroughTargetInstruction).getOffset() - getInfo(ir).getOffset();

    return ir.buildPayload(targets, fallthroughTarget, InternalOutputMode.DexIndexed);
  }

  // Helpers for computing the try items and handlers.

  private TryInfo computeTryInfo() {
    // Canonical map of handlers.
    BiMap<CatchHandlers<BasicBlock>, Integer> canonicalHandlers = HashBiMap.create();
    // Compute the list of try items and their handlers.
    List<TryItem> tryItems = computeTryItems(canonicalHandlers);
    // Compute handler sets before dex items which depend on the handler index.
    Try[] tries = getDexTryItems(tryItems, canonicalHandlers);
    TryHandler[] handlers = getDexTryHandlers(canonicalHandlers.inverse());
    return new TryInfo(tries, handlers);
  }

  private List<TryItem> computeTryItems(
      BiMap<CatchHandlers<BasicBlock>, Integer> handlerToIndex) {
    BiMap<Integer, CatchHandlers<BasicBlock>> indexToHandler = handlerToIndex.inverse();
    List<TryItem> tryItems = new ArrayList<>();
    List<BasicBlock> blocksWithHandlers = new ArrayList<>();
    TryItem currentTryItem = null;
    // Create try items with maximal ranges to get as much coalescing as possible. After coalescing
    // the try ranges are trimmed.
    for (BasicBlock block : ir.blocks) {
      CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
      // If this assert is hit, then the block contains no instruction that can throw. This is most
      // likely due to dead-code elimination or other optimizations that might now work on a refined
      // notion of what can throw. If so, the trivial blocks should either be removed or their catch
      // handlers deleted to reflect the simpler graph prior to building the dex code.
      assert handlers.isEmpty() || block.canThrow();
      if (!handlers.isEmpty()) {
        if (handlerToIndex.containsKey(handlers)) {
          handlers = indexToHandler.get(handlerToIndex.get(handlers));
        } else {
          handlerToIndex.put(handlers, handlerToIndex.size());
        }
        Info startInfo = getInfo(block.entry());
        Info endInfo = getInfo(block.exit());
        int start = startInfo.getOffset();
        int end = endInfo.getOffset() + endInfo.getSize();
        currentTryItem = new TryItem(handlers, start, end);
        tryItems.add(currentTryItem);
        blocksWithHandlers.add(block);
      } else if (currentTryItem != null && !block.canThrow()) {
        Info endInfo = getInfo(block.exit());
        // If the block only contains a goto there might not be an info for the exit instruction.
        if (endInfo != null) {
          currentTryItem.end = endInfo.getOffset() + endInfo.getSize();
        }
      } else {
        currentTryItem = null;
      }
    }

    // If there are no try items it is trivially coalesced.
    if (tryItems.isEmpty()) {
      return tryItems;
    }

    // Coalesce try blocks.
    tryItems.sort(TryItem::compareTo);
    List<TryItem> coalescedTryItems = new ArrayList<>(tryItems.size());
    TryItem item = null;
    for (int i = 0; i < tryItems.size(); ) {
      if (item != null) {
        item.end = trimEnd(blocksWithHandlers.get(i - 1));
      }
      item = tryItems.get(i);
      coalescedTryItems.add(item);
      // Trim the range start for non-throwing instructions when starting a new range.
      List<com.android.tools.r8.ir.code.Instruction> instructions = blocksWithHandlers.get(i)
          .getInstructions();
      for (com.android.tools.r8.ir.code.Instruction insn : instructions) {
        if (insn.instructionTypeCanThrow()) {
          item.start = getInfo(insn).getOffset();
          break;
        }
      }
      // Append all consecutive ranges that define the same handlers.
      ++i;
      while (i < tryItems.size()) {
        TryItem next = tryItems.get(i);
        if (item.end != next.start || !item.handlers.equals(next.handlers)) {
          break;
        }
        item.end = next.end;
        ++i;
      }
    }
    // Trim the last try range.
    int lastIndex = tryItems.size() - 1;
    item.end = trimEnd(blocksWithHandlers.get(lastIndex));
    return coalescedTryItems;
  }

  private int trimEnd(BasicBlock block) {
    // Trim the range end for non-throwing instructions when end has been computed.
    List<com.android.tools.r8.ir.code.Instruction> instructions = block.getInstructions();
    for (com.android.tools.r8.ir.code.Instruction insn : Lists.reverse(instructions)) {
      if (insn.instructionTypeCanThrow()) {
        Info info = getInfo(insn);
        return info.getOffset() + info.getSize();
      }
    }
    throw new Unreachable("Expected to find a possibly throwing instruction");
  }

  private static Try[] getDexTryItems(List<TryItem> tryItems,
      Map<CatchHandlers<BasicBlock>, Integer> catchHandlers) {
    Try[] tries = new Try[tryItems.size()];
    for (int i = 0; i < tries.length; ++i) {
      TryItem item = tryItems.get(i);
      Try dexTry = new Try(item.start, item.end - item.start, -1);
      dexTry.handlerIndex = catchHandlers.get(item.handlers);
      tries[i] = dexTry;
    }
    return tries;
  }

  private TryHandler[] getDexTryHandlers(Map<Integer, CatchHandlers<BasicBlock>> catchHandlers) {
    TryHandler[] handlers = new TryHandler[catchHandlers.size()];
    for (int j = 0; j < catchHandlers.size(); j++) {
      CatchHandlers<BasicBlock> handlerGroup = catchHandlers.get(j);
      int catchAllOffset = TryHandler.NO_HANDLER;
      List<TypeAddrPair> pairs = new ArrayList<>();
      for (int i = 0; i < handlerGroup.getGuards().size(); i++) {
        DexType type = handlerGroup.getGuards().get(i);
        BasicBlock target = handlerGroup.getAllTargets().get(i);
        int targetOffset = getInfo(target.entry()).getOffset();
        if (type == options.itemFactory.throwableType) {
          assert i == handlerGroup.getGuards().size() - 1;
          catchAllOffset = targetOffset;
        } else {
          pairs.add(new TypeAddrPair(type, targetOffset));
        }
      }
      TypeAddrPair[] pairsArray = pairs.toArray(new TypeAddrPair[]{});
      handlers[j] = new TryHandler(pairsArray, catchAllOffset);
    }
    return handlers;
  }

  public InternalOptions getOptions() {
    return options;
  }

  public RegisterAllocator getRegisterAllocator() {
    return registerAllocator;
  }

  public ProgramMethod getProgramMethod() {
    return registerAllocator.getProgramMethod();
  }

  // Dex instruction wrapper with information to compute instruction sizes and offsets for jumps.
  private static abstract class Info {

    private final com.android.tools.r8.ir.code.Instruction ir;
    // Concrete final offset of the instruction.
    private int offset = -1;
    // Lower and upper bound of the final offset.
    private int minOffset = -1;
    private int maxOffset = -1;

    public Info(com.android.tools.r8.ir.code.Instruction ir) {
      assert ir != null;
      this.ir = ir;
    }

    // Computes the final size of the instruction.
    // All instruction offsets up-to and including this instruction will be defined at this point.
    public abstract int computeSize(DexBuilder builder);

    // Materialize the actual construction.
    // All instruction offsets are known at this point.
    public abstract void addInstructions(DexBuilder builder, List<DexInstruction> instructions);

    // Lower bound on the size of the instruction.
    public abstract int minSize();

    // Upper bound on the size of the instruction.
    public abstract int maxSize();

    public abstract int getSize();

    public int getOffset() {
      assert offset >= 0 : this;
      return offset;
    }

    public void setOffset(int offset) {
      assert offset >= 0;
      this.offset = offset;
    }

    public int getMinOffset() {
      assert minOffset >= 0;
      return minOffset;
    }

    public void setMinOffset(int minOffset) {
      assert minOffset >= 0;
      this.minOffset = minOffset;
    }

    public int getMaxOffset() {
      assert maxOffset >= 0;
      return maxOffset;
    }

    public void setMaxOffset(int maxOffset) {
      assert maxOffset >= 0;
      this.maxOffset = maxOffset;
    }

    public com.android.tools.r8.ir.code.Instruction getIR() {
      return ir;
    }

    public abstract boolean identicalInstructions(Info other, DexBuilder builder);
  }

  private static class FixedSizeInfo extends Info {

    private final DexInstruction instruction;

    public FixedSizeInfo(com.android.tools.r8.ir.code.Instruction ir, DexInstruction instruction) {
      super(ir);
      this.instruction = instruction;
    }

    @Override
    public int getSize() {
      return instruction.getSize();
    }

    @Override
    public int minSize() {
      return instruction.getSize();
    }

    @Override
    public int maxSize() {
      return instruction.getSize();
    }

    @Override
    public int computeSize(DexBuilder builder) {
      instruction.setOffset(getOffset()); // for better printing of the dex code.
      return instruction.getSize();
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {
      instructions.add(instruction);
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      return other instanceof FixedSizeInfo
          && instruction.equals(((FixedSizeInfo) other).instruction);
    }
  }

  private static class MultiFixedSizeInfo extends Info {

    private final DexInstruction[] instructions;
    private final int size;

    public MultiFixedSizeInfo(
        com.android.tools.r8.ir.code.Instruction ir, DexInstruction[] instructions) {
      super(ir);
      this.instructions = instructions;
      int size = 0;
      for (DexInstruction instruction : instructions) {
        size += instruction.getSize();
      }
      this.size = size;
    }

    @Override
    public int computeSize(DexBuilder builder) {
      return size;
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {
      int offset = getOffset();
      for (DexInstruction instruction : this.instructions) {
        instructions.add(instruction);
        instruction.setOffset(offset);
        offset += instruction.getSize();
      }
    }

    @Override
    public int minSize() {
      return size;
    }

    @Override
    public int maxSize() {
      return size;
    }

    @Override
    public int getSize() {
      return size;
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      return other instanceof MultiFixedSizeInfo
          && Arrays.equals(instructions, ((MultiFixedSizeInfo) other).instructions);
    }
  }

  private static class FallThroughInfo extends Info {

    public FallThroughInfo(com.android.tools.r8.ir.code.Instruction ir) {
      super(ir);
    }

    @Override
    public int getSize() {
      return 0;
    }

    @Override
    public int computeSize(DexBuilder builder) {
      return 0;
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {}

    @Override
    public int minSize() {
      return 0;
    }

    @Override
    public int maxSize() {
      return 0;
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      return other instanceof FallThroughInfo;
    }
  }

  private static class GotoInfo extends Info {

    private int size = -1;

    public GotoInfo(com.android.tools.r8.ir.code.Goto jump) {
      super(jump);
    }

    private com.android.tools.r8.ir.code.Goto getJump() {
      return (com.android.tools.r8.ir.code.Goto) getIR();
    }

    @Override
    public int getSize() {
      assert size > 0;
      return size;
    }

    @Override
    public int minSize() {
      assert new DexGoto(42).getSize() == 1;
      return 1;
    }

    @Override
    public int maxSize() {
      assert new DexGoto32(0).getSize() == 3;
      return 3;
    }

    @Override
    public int computeSize(DexBuilder builder) {
      assert size < 0;
      com.android.tools.r8.ir.code.Goto jump = getJump();
      Info targetInfo = builder.getTargetInfo(jump.getTarget());
      // Trivial loop will be emitted as: nop & goto -1
      if (jump == targetInfo.getIR()) {
        size = 2;
        return size;
      }
      int maxOffset = getMaxOffset();
      int maxTargetOffset = targetInfo.getMaxOffset();
      int delta;
      if (maxTargetOffset < maxOffset) {
        // Backward branch: compute exact size (the target offset is set).
        delta = getOffset() - targetInfo.getOffset();
      } else {
        // Forward branch: over estimate the distance, but take into account the sizes
        // of instructions generated so far. That way the over estimation is only for the
        // instructions between this one and the target.
        int maxOverEstimation = maxOffset - getOffset();
        delta = (maxTargetOffset - maxOverEstimation) - getOffset();
      }
      if (delta <= Byte.MAX_VALUE) {
        size = 1;
      } else if (delta <= Short.MAX_VALUE) {
        size = 2;
      } else {
        size = 3;
      }
      if (targetInfo.getIR().isReturn() && targetInfo.getIR().getPosition().isNone()) {
        // Set the size to the min of the size of the return and the size of the goto. When
        // adding instructions, we use the return if the computed size matches the size of the
        // return.
        assert !(targetInfo instanceof FallThroughInfo);
        size = Math.min(targetInfo.getSize(), size);
      }
      assert size != 0;
      return size;
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {
      com.android.tools.r8.ir.code.Goto jump = getJump();
      int source = builder.getInfo(jump).getOffset();
      Info targetInfo = builder.getTargetInfo(jump.getTarget());
      int relativeOffset = targetInfo.getOffset() - source;
      if (relativeOffset < 0) {
        builder.hasBackwardsBranch = true;
      }
      // Emit a return if the target is a return and the size of the return is the computed
      // size of this instruction.
      Return ret = targetInfo.getIR().asReturn();
      if (ret != null && size == targetInfo.getSize() && ret.getPosition().isNone()) {
        DexInstruction dex = ret.createDexInstruction(builder);
        dex.setOffset(getOffset()); // for better printing of the dex code.
        instructions.add(dex);
      } else if (size == relativeOffset) {
        // We should never generate a goto targeting the next instruction. However, if we do
        // we replace it with nops. This works around a dalvik bug where the dalvik tracing
        // jit crashes on 'goto next instruction' on Android 4.1.1.
        // TODO(b/34726595): We currently do hit this case and we should see if we can avoid that.
        for (int i = 0; i < size; i++) {
          DexInstruction dex = new DexNop();
          assert dex.getSize() == 1;
          dex.setOffset(getOffset() + i); // for better printing of the dex code.
          instructions.add(dex);
        }
      } else {
        DexInstruction dex;
        switch (size) {
          case 1:
            assert relativeOffset != 0;
            dex = new DexGoto(relativeOffset);
            break;
          case 2:
            if (relativeOffset == 0) {
              DexNop nop = new DexNop();
              instructions.add(nop);
              dex = new DexGoto(-nop.getSize());
            } else {
              dex = new DexGoto16(relativeOffset);
            }
            break;
          case 3:
            dex = new DexGoto32(relativeOffset);
            break;
          default:
            throw new Unreachable("Unexpected size for goto instruction: " + size);
        }
        dex.setOffset(getOffset()); // for better printing of the dex code.
        instructions.add(dex);
      }
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      return other instanceof GotoInfo;
    }
  }

  public static class IfInfo extends Info {

    private int size = -1;

    public IfInfo(If branch) {
      super(branch);
    }

    private int getRegister(int operandIndex, DexBuilder builder) {
      If branch = getBranch();
      return builder.allocatedRegister(branch.inValues().get(operandIndex), branch.getNumber());
    }

    private int[] getRegisters(DexBuilder builder) {
      if (getBranch().isZeroTest()) {
        return new int[] {getRegister(0, builder)};
      }
      return new int[] {getRegister(0, builder), getRegister(1, builder)};
    }

    private If getBranch() {
      return (If) getIR();
    }

    private boolean branchesToSelf(DexBuilder builder) {
      If branch = getBranch();
      Info trueTargetInfo = builder.getTargetInfo(branch.getTrueTarget());
      return branch == trueTargetInfo.getIR();
    }

    private boolean offsetOutOfRange(DexBuilder builder) {
      Info targetInfo = builder.getTargetInfo(getBranch().getTrueTarget());
      int maxOffset = getMaxOffset();
      int maxTargetOffset = targetInfo.getMaxOffset();
      if (maxTargetOffset < maxOffset) {
        int relativeJumpOffset = targetInfo.getOffset() - getOffset();
        assert relativeJumpOffset < 0;
        return relativeJumpOffset < Short.MIN_VALUE;
      }
      // Forward branch: over estimate the distance, but take into account the sizes
      // of instructions generated so far. That way the over estimation is only for the
      // instructions between this one and the target.
      int maxOverEstimation = maxOffset - getOffset();
      return (maxTargetOffset - maxOverEstimation) - getOffset() > Short.MAX_VALUE;
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {
      If branch = getBranch();
      int source = builder.getInfo(branch).getOffset();
      int target = builder.getInfo(branch.getTrueTarget().entry()).getOffset();
      int relativeOffset = target - source;
      int register1 = getRegister(0, builder);

      if (relativeOffset < 0) {
        builder.hasBackwardsBranch = true;
      }

      if (size == 3) {
        assert branchesToSelf(builder);
        DexNop nop = new DexNop();
        relativeOffset -= nop.getSize();
        instructions.add(nop);
      }
      assert relativeOffset != 0;
      DexInstruction instruction = null;
      if (branch.isZeroTest()) {
        switch (getBranch().getType()) {
          case EQ:
            instruction = new DexIfEqz(register1, relativeOffset);
            break;
          case GE:
            instruction = new DexIfGez(register1, relativeOffset);
            break;
          case GT:
            instruction = new DexIfGtz(register1, relativeOffset);
            break;
          case LE:
            instruction = new DexIfLez(register1, relativeOffset);
            break;
          case LT:
            instruction = new DexIfLtz(register1, relativeOffset);
            break;
          case NE:
            instruction = new DexIfNez(register1, relativeOffset);
            break;
        }
      } else {
        int register2 = getRegister(1, builder);
        switch (getBranch().getType()) {
          case EQ:
            instruction = new DexIfEq(register1, register2, relativeOffset);
            break;
          case GE:
            instruction = new DexIfGe(register1, register2, relativeOffset);
            break;
          case GT:
            instruction = new DexIfGt(register1, register2, relativeOffset);
            break;
          case LE:
            instruction = new DexIfLe(register1, register2, relativeOffset);
            break;
          case LT:
            instruction = new DexIfLt(register1, register2, relativeOffset);
            break;
          case NE:
            instruction = new DexIfNe(register1, register2, relativeOffset);
            break;
        }
      }
      instruction.setOffset(getOffset());
      instructions.add(instruction);
    }

    @Override
    public int computeSize(DexBuilder builder) {
      if (offsetOutOfRange(builder)) {
        builder.needsIfRewriting(getBranch().getBlock());
      }
      size = branchesToSelf(builder) ? 3 : 2;
      return size;
    }

    @Override
    public int minSize() {
      return 2;
    }

    @Override
    public int maxSize() {
      return 3;
    }

    @Override
    public int getSize() {
      return size;
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      if (!(other instanceof IfInfo)) {
        return false;
      }
      IfInfo otherInfo = (IfInfo) other;
      return getBranch().getType() == otherInfo.getBranch().getType()
          && Arrays.equals(getRegisters(builder), otherInfo.getRegisters(builder));
    }
  }

  public static class MoveInfo extends Info {

    private int size = -1;

    public MoveInfo(Move move) {
      super(move);
    }

    private Move getMove() {
      return (Move) getIR();
    }

    public int srcRegister(DexBuilder builder) {
      return builder.argumentOrAllocateRegister(getMove().src(), getMove().getNumber());
    }

    public int destRegister(DexBuilder builder) {
      return builder.allocatedRegister(getMove().dest(), getMove().getNumber());
    }

    @Override
    public boolean identicalInstructions(Info other, DexBuilder builder) {
      if (!(other instanceof MoveInfo)) {
        return false;
      }
      MoveInfo moveInfo = (MoveInfo) other;
      return srcRegister(builder) == moveInfo.srcRegister(builder)
          && destRegister(builder) == moveInfo.destRegister(builder);
    }

    @Override
    public int computeSize(DexBuilder builder) {
      int srcRegister = srcRegister(builder);
      int destRegister = destRegister(builder);
      if (srcRegister == destRegister) {
        size = 1;
      } else if (srcRegister <= Constants.U4BIT_MAX && destRegister <= Constants.U4BIT_MAX) {
        size = 1;
      } else if (destRegister <= Constants.U8BIT_MAX) {
        size = 2;
      } else {
        size = 3;
      }
      return size;
    }

    @Override
    public void addInstructions(DexBuilder builder, List<DexInstruction> instructions) {
      Move move = getMove();
      TypeElement moveType = move.getOutType();
      int src = srcRegister(builder);
      int dest = destRegister(builder);
      DexInstruction instruction;
      switch (size) {
        case 1:
          if (src == dest) {
            instruction = new DexNop();
            break;
          }
          if (moveType.isSinglePrimitive()) {
            instruction = new DexMove(dest, src);
          } else if (moveType.isWidePrimitive()) {
            instruction = new DexMoveWide(dest, src);
          } else if (moveType.isReferenceType()) {
            instruction = new DexMoveObject(dest, src);
          } else {
            throw new Unreachable("Unexpected type: " + move.outType());
          }
          break;
        case 2:
          if (moveType.isSinglePrimitive()) {
            instruction = new DexMoveFrom16(dest, src);
          } else if (moveType.isWidePrimitive()) {
            instruction = new DexMoveWideFrom16(dest, src);
          } else if (moveType.isReferenceType()) {
            instruction = new DexMoveObjectFrom16(dest, src);
          } else {
            throw new Unreachable("Unexpected type: " + move.outType());
          }
          break;
        case 3:
          if (moveType.isSinglePrimitive()) {
            instruction = new DexMove16(dest, src);
          } else if (moveType.isWidePrimitive()) {
            instruction = new DexMoveWide16(dest, src);
          } else if (moveType.isReferenceType()) {
            instruction = new DexMoveObject16(dest, src);
          } else {
            throw new Unreachable("Unexpected type: " + move.outType());
          }
          break;
        default:
          throw new Unreachable("Unexpected size: " + size);
      }
      instruction.setOffset(getOffset());
      instructions.add(instruction);
    }

    @Override
    public int minSize() {
      assert new DexNop().getSize() == 1 && new DexMove(0, 0).getSize() == 1;
      return 1;
    }

    @Override
    public int maxSize() {
      assert new DexMove16(0, 0).getSize() == 3;
      return 3;
    }

    @Override
    public int getSize() {
      assert size > 0;
      return size;
    }
  }

  // Return-type wrapper for try-related data.
  private static class TryInfo {

    public final Try[] tries;
    public final TryHandler[] handlers;

    public TryInfo(Try[] tries, TryHandler[] handlers) {
      this.tries = tries;
      this.handlers = handlers;
    }
  }

  // Helper class for coalescing ranges for try blocks.
  private static class TryItem implements Comparable<TryItem> {

    public final CatchHandlers<BasicBlock> handlers;
    public int start;
    public int end;

    public TryItem(CatchHandlers<BasicBlock> handlers, int start, int end) {
      this.handlers = handlers;
      this.start = start;
      this.end = end;
    }

    @Override
    public int compareTo(TryItem other) {
      return Integer.compare(start, other.start);
    }
  }

  private static class SwitchPayloadInfo {

    public final IntSwitch ir;
    public final DexFormat31t dex;

    public SwitchPayloadInfo(IntSwitch ir, DexFormat31t dex) {
      this.ir = ir;
      this.dex = dex;
    }
  }

  private static class FillArrayDataInfo {

    public final NewArrayFilledData ir;
    public final DexFillArrayData dex;

    public FillArrayDataInfo(NewArrayFilledData ir, DexFillArrayData dex) {
      this.ir = ir;
      this.dex = dex;
    }
  }
}
