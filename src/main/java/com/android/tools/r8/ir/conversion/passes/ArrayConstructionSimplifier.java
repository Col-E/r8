// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.LinearFlowInstructionListIterator;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.RewriteArrayOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Replace new-array followed by stores of constants to all entries with new-array and
 * fill-array-data / filled-new-array.
 *
 * <p>The format of the new-array and its puts must be of the form:
 *
 * <pre>
 *   v0 <- new-array T vSize
 *   ...
 *   array-put v0 vValue1 vIndex1
 *   ...
 *   array-put v0 vValueN vIndexN
 * </pre>
 *
 * <p>The flow between the array v0 and its puts must be linear with no other uses of v0 besides the
 * array-put instructions, thus any no intermediate instruction (... above) must use v0 and also
 * cannot have catch handlers that would transfer out control (those could then have uses of v0).
 *
 * <p>The allocation of the new-array can itself have catch handlers, in which case, those are to
 * remain active on the translated code. Translated code can have two forms.
 *
 * <p>The first is using the original array allocation and filling in its data if it can be encoded:
 *
 * <pre>
 *   v0 <- new-array T vSize
 *   filled-array-data v0
 *   ...
 *   ...
 * </pre>
 *
 * The data payload is encoded directly in the instruction so no dependencies are needed for filling
 * the data array. Thus, the fill is inserted at the point of the allocation. If the allocation has
 * catch handlers its block must be split and the handlers put on the fill instruction too. This is
 * correct only because there are no exceptional transfers in (...) that could observe the early
 * initialization of the data.
 *
 * <p>The second is using filled-new-array and has the form:
 *
 * <pre>
 * ...
 * ...
 * v0 <- filled-new-array T vValue1 ... vValueN
 * </pre>
 *
 * Here the correctness relies on no exceptional transfers in (...) that could observe the missing
 * allocation of the array. The late allocation ensures that the values are available at allocation
 * time. If the original allocation has catch handlers then the new allocation needs to link those
 * too. In general that may require splitting the block twice so that the new allocation is the
 * single throwing instruction in its block.
 */
public class ArrayConstructionSimplifier extends CodeRewriterPass<AppInfo> {

  public ArrayConstructionSimplifier(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ArrayConstructionSimplifier";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    WorkList<BasicBlock> worklist = WorkList.newIdentityWorkList(code.blocks);
    while (worklist.hasNext()) {
      BasicBlock block = worklist.next();
      hasChanged |= simplifyArrayConstructionBlock(block, worklist, code, appView.options());
    }
    if (hasChanged) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  private boolean simplifyArrayConstructionBlock(
      BasicBlock block, WorkList<BasicBlock> worklist, IRCode code, InternalOptions options) {
    boolean hasChanged = false;
    RewriteArrayOptions rewriteOptions = options.rewriteArrayOptions();
    InstructionListIterator it = block.listIterator(code);
    while (it.hasNext()) {
      FilledArrayCandidate candidate = computeFilledArrayCandidate(it.next(), rewriteOptions);
      if (candidate == null) {
        continue;
      }
      FilledArrayConversionInfo info =
          computeConversionInfo(
              code, candidate, new LinearFlowInstructionListIterator(code, block, it.nextIndex()));
      if (info == null) {
        continue;
      }

      Instruction instructionAfterCandidate = it.peekNext();
      NewArrayEmpty newArrayEmpty = candidate.newArrayEmpty;
      DexType arrayType = newArrayEmpty.type;
      int size = candidate.size;
      Set<Instruction> instructionsToRemove = SetUtils.newIdentityHashSet(size + 1);
      assert newArrayEmpty.getLocalInfo() == null;
      Instruction lastArrayPut = info.lastArrayPutIterator.peekPrevious();
      Value invokeValue = code.createValue(newArrayEmpty.getOutType(), null);
      NewArrayFilled invoke =
          new NewArrayFilled(arrayType, invokeValue, Arrays.asList(info.values));
      invoke.setPosition(lastArrayPut.getPosition());
      for (Value value : newArrayEmpty.inValues()) {
        value.removeUser(newArrayEmpty);
      }
      newArrayEmpty.outValue().replaceUsers(invokeValue);
      instructionsToRemove.add(newArrayEmpty);

      boolean originalAllocationPointHasHandlers = block.hasCatchHandlers();
      boolean insertionPointHasHandlers = lastArrayPut.getBlock().hasCatchHandlers();

      if (!insertionPointHasHandlers && !originalAllocationPointHasHandlers) {
        info.lastArrayPutIterator.add(invoke);
      } else {
        BasicBlock insertionBlock = info.lastArrayPutIterator.split(code);
        if (originalAllocationPointHasHandlers) {
          if (!insertionBlock.isTrivialGoto()) {
            BasicBlock blockAfterInsertion = insertionBlock.listIterator(code).split(code);
            assert insertionBlock.isTrivialGoto();
            worklist.addIfNotSeen(blockAfterInsertion);
          }
          insertionBlock.moveCatchHandlers(block);
        } else {
          worklist.addIfNotSeen(insertionBlock);
        }
        insertionBlock.listIterator(code).add(invoke);
      }

      instructionsToRemove.addAll(info.arrayPutsToRemove);
      Set<BasicBlock> visitedBlocks = Sets.newIdentityHashSet();
      for (Instruction instruction : instructionsToRemove) {
        BasicBlock ownerBlock = instruction.getBlock();
        // If owner block is null, then the instruction has been removed already. We can't rely on
        // just having the block pointer nulled, so the visited blocks guards reprocessing.
        if (ownerBlock != null && visitedBlocks.add(ownerBlock)) {
          InstructionListIterator removeIt = ownerBlock.listIterator(code);
          while (removeIt.hasNext()) {
            if (instructionsToRemove.contains(removeIt.next())) {
              removeIt.removeOrReplaceByDebugLocalRead();
            }
          }
        }
      }

      // The above has invalidated the block iterator so reset it and continue.
      it = block.listIterator(code, instructionAfterCandidate);
      hasChanged = true;
    }
    if (hasChanged) {
      code.removeRedundantBlocks();
    }

    return hasChanged;
  }

  private static class FilledArrayConversionInfo {

    Value[] values;
    List<ArrayPut> arrayPutsToRemove;
    LinearFlowInstructionListIterator lastArrayPutIterator;

    public FilledArrayConversionInfo(int size) {
      values = new Value[size];
      arrayPutsToRemove = new ArrayList<>(size);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private FilledArrayConversionInfo computeConversionInfo(
      IRCode code, FilledArrayCandidate candidate, LinearFlowInstructionListIterator it) {
    NewArrayEmpty newArrayEmpty = candidate.newArrayEmpty;
    assert it.peekPrevious() == newArrayEmpty;
    Value arrayValue = newArrayEmpty.outValue();
    int size = candidate.size;

    // aput-object allows any object for arrays of interfaces, but new-filled-array fails to verify
    // if types require a cast.
    // TODO(b/246971330): Check if adding a checked-cast would have the same observable result. E.g.
    //   if aput-object throws a ClassCastException if given an object that does not implement the
    //   desired interface, then we could add check-cast instructions for arguments we're not sure
    //   about.
    DexType elementType = newArrayEmpty.type.toArrayElementType(dexItemFactory);
    boolean needsTypeCheck =
        !elementType.isPrimitiveType() && elementType != dexItemFactory.objectType;

    FilledArrayConversionInfo info = new FilledArrayConversionInfo(size);
    Value[] values = info.values;
    int remaining = size;
    Set<Instruction> users = newArrayEmpty.outValue().uniqueUsers();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      BasicBlock block = instruction.getBlock();
      // If we encounter an instruction that can throw an exception we need to bail out of the
      // optimization so that we do not transform half-initialized arrays into fully initialized
      // arrays on exceptional edges. If the block has no handlers it is not observable so
      // we perform the rewriting.
      if (block.hasCatchHandlers()
          && instruction.instructionInstanceCanThrow(appView, code.context())) {
        return null;
      }
      if (!users.contains(instruction)) {
        // If any instruction can transfer control between the new-array and the last array put
        // then it is not safe to move the new array to the point of the last put.
        if (block.hasCatchHandlers() && instruction.instructionTypeCanThrow()) {
          return null;
        }
        continue;
      }
      ArrayPut arrayPut = instruction.asArrayPut();
      // If the initialization sequence is broken by another use we cannot use a fill-array-data
      // instruction.
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        return null;
      }
      int index = arrayPut.indexIfConstAndInBounds(values.length);
      if (index < 0 || values[index] != null) {
        return null;
      }
      if (arrayPut.instructionInstanceCanThrow(appView, code.context())) {
        return null;
      }
      Value value = arrayPut.value();
      if (needsTypeCheck && !value.isAlwaysNull(appView)) {
        DexType valueDexType = value.getType().asReferenceType().toDexType(dexItemFactory);
        if (elementType.isArrayType()) {
          if (elementType != valueDexType) {
            return null;
          }
        } else if (valueDexType.isArrayType()) {
          // isSubtype asserts for this case.
          return null;
        } else if (valueDexType.isNullValueType()) {
          // Assume instructions can cause value.isAlwaysNull() == false while the DexType is null.
          // TODO(b/246971330): Figure out how to write a test in SimplifyArrayConstructionTest
          //   that hits this case.
        } else {
          // TODO(b/246971330): When in d8 mode, we might still be able to see if this is true for
          //   library types (which this helper does not do).
          if (appView.isSubtype(valueDexType, elementType).isPossiblyFalse()) {
            return null;
          }
        }
      }
      info.arrayPutsToRemove.add(arrayPut);
      values[index] = value;
      --remaining;
      if (remaining == 0) {
        info.lastArrayPutIterator = it;
        return info;
      }
    }
    return null;
  }

  private static class FilledArrayCandidate {

    final NewArrayEmpty newArrayEmpty;
    final int size;

    public FilledArrayCandidate(NewArrayEmpty newArrayEmpty, int size) {
      assert size > 0;
      this.newArrayEmpty = newArrayEmpty;
      this.size = size;
    }
  }

  private FilledArrayCandidate computeFilledArrayCandidate(
      Instruction instruction, RewriteArrayOptions options) {
    NewArrayEmpty newArrayEmpty = instruction.asNewArrayEmpty();
    if (newArrayEmpty == null) {
      return null;
    }
    if (instruction.getLocalInfo() != null) {
      return null;
    }
    if (!newArrayEmpty.size().isConstant()) {
      return null;
    }
    int size = newArrayEmpty.size().getConstInstruction().asConstNumber().getIntValue();
    if (!options.isPotentialSize(size)) {
      return null;
    }
    return new FilledArrayCandidate(newArrayEmpty, size);
  }
}
