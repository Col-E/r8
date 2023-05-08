// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.lenscoderewriter;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class NullCheckInserter {

  public static NullCheckInserter create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCode code,
      NonIdentityGraphLens graphLens,
      GraphLens codeLens) {
    NonIdentityGraphLens previousLens =
        graphLens.find(lens -> lens.isArgumentPropagatorGraphLens() || lens == codeLens);
    if (previousLens != null
        && previousLens != codeLens
        && previousLens.isArgumentPropagatorGraphLens()) {
      return new NullCheckInserterImpl(appView.withLiveness(), code, graphLens);
    }
    return new EmptyNullCheckInserter();
  }

  public abstract void insertNullCheckForInvokeReceiverIfNeeded(
      InvokeMethod invoke, InvokeMethod rewrittenInvoke, MethodLookupResult lookup);

  public abstract void processWorklist();

  static class NullCheckInserterImpl extends NullCheckInserter {

    private final AppView<AppInfoWithLiveness> appView;
    private final IRCode code;
    private final NonIdentityGraphLens graphLens;

    private final Map<InvokeStatic, Value> worklist = new IdentityHashMap<>();

    NullCheckInserterImpl(
        AppView<AppInfoWithLiveness> appView, IRCode code, NonIdentityGraphLens graphLens) {
      this.appView = appView;
      this.code = code;
      this.graphLens = graphLens;
    }

    @Override
    public void insertNullCheckForInvokeReceiverIfNeeded(
        InvokeMethod invoke, InvokeMethod rewrittenInvoke, MethodLookupResult lookup) {
      // If the invoke has been staticized, then synthesize a null check for the receiver.
      if (!invoke.isInvokeMethodWithReceiver() || !rewrittenInvoke.isInvokeStatic()) {
        return;
      }

      ArgumentInfo receiverArgumentInfo =
          lookup.getPrototypeChanges().getArgumentInfoCollection().getArgumentInfo(0);
      if (!receiverArgumentInfo.isRemovedReceiverInfo()) {
        return;
      }

      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      TypeElement receiverType = receiver.getType();
      if (receiverType.isDefinitelyNotNull()) {
        return;
      }

      // A parameter with users is only subject to effectively unused argument removal if it is
      // guaranteed to be non-null.
      if (receiver.isDefinedByInstructionSatisfying(Instruction::isUnusedArgument)) {
        return;
      }

      worklist.put(rewrittenInvoke.asInvokeStatic(), receiver);
    }

    @Override
    public void processWorklist() {
      if (worklist.isEmpty()) {
        return;
      }

      BasicBlockIterator blockIterator = code.listIterator();
      while (blockIterator.hasNext()) {
        BasicBlock block = blockIterator.next();
        InstructionListIterator instructionIterator = block.listIterator(code);
        while (instructionIterator.hasNext()) {
          Instruction instruction = instructionIterator.next();
          if (!instruction.isInvokeStatic()) {
            continue;
          }

          InvokeStatic invoke = instruction.asInvokeStatic();
          if (!worklist.containsKey(invoke)) {
            continue;
          }

          // Don't insert null checks for effectively unread fields.
          Value receiver = worklist.get(invoke);
          if (isReadOfEffectivelyUnreadField(receiver)) {
            continue;
          }

          instructionIterator.previous();

          Position nullCheckPosition =
              invoke
                  .getPosition()
                  .getOutermostCallerMatchingOrElse(
                      Position::isRemoveInnerFramesIfThrowingNpe, invoke.getPosition());
          if (nullCheckPosition.isRemoveInnerFramesIfThrowingNpe()) {
            // We've found an outermost removeInnerFrames for an invoke with receiver. Assume we
            // have call chain: inline -> callerInline -> callerCallerInline
            // where callerInline.isRemoveInnerFramesIfThrowingNpe() == true.
            // inline must therefore have an immediate use of the receiver which is tracked in
            // callerInline such that the frame can be removed when retracing.
            // When synthesizing a nullCheck for the receiver on inline, the check should actually
            // fail in the callerInline. We therefore use this as the new position.
            // Since the exception is now moved out to callerInline, we should no longer strip the
            // topmost frame if we see an NPE in inline, so we update the position on this invoke to
            // inline -> callerInline' -> callerCallerInline
            // where callerInline.isRemoveInnerFramesIfThrowingNpe() == false;
            Position newCallerPositionTail =
                nullCheckPosition
                    .builderWithCopy()
                    .setRemoveInnerFramesIfThrowingNpe(false)
                    .build();
            invoke.forceOverwritePosition(
                invoke.getPosition().replacePosition(nullCheckPosition, newCallerPositionTail));
            // We can then use pos2 (newCallerPositionTail) as the new null-check position.
            nullCheckPosition = newCallerPositionTail;
          }
          instructionIterator.insertNullCheckInstruction(
              appView, code, blockIterator, receiver, nullCheckPosition);
          // Reset the block iterator.
          if (invoke.getBlock().hasCatchHandlers()) {
            BasicBlock splitBlock = invoke.getBlock();
            BasicBlock previousBlock = blockIterator.previousUntil(b -> b == splitBlock);
            assert previousBlock == splitBlock;
            blockIterator.next();
            instructionIterator = splitBlock.listIterator(code);
          }

          Instruction next = instructionIterator.next();
          assert next == invoke;
        }
      }
    }

    private boolean isReadOfEffectivelyUnreadField(Value value) {
      if (value.isPhi()) {
        boolean hasSeenReadOfEffectivelyUnreadField = false;
        WorkList<Phi> reachablePhis = WorkList.newIdentityWorkList(value.asPhi());
        while (reachablePhis.hasNext()) {
          Phi currentPhi = reachablePhis.next();
          for (Value operand : currentPhi.getOperands()) {
            if (operand.isPhi()) {
              reachablePhis.addIfNotSeen(operand.asPhi());
            } else if (!isReadOfEffectivelyUnreadField(operand.getDefinition())) {
              return false;
            } else {
              hasSeenReadOfEffectivelyUnreadField = true;
            }
          }
        }
        assert hasSeenReadOfEffectivelyUnreadField;
        return true;
      } else {
        return isReadOfEffectivelyUnreadField(value.getDefinition());
      }
    }

    private boolean isReadOfEffectivelyUnreadField(Instruction instruction) {
      if (instruction.isFieldGet()) {
        FieldGet fieldGet = instruction.asFieldGet();
        DexField field = fieldGet.getField();
        // This needs to map the field all the way to the final graph lens.
        DexField rewrittenField = appView.graphLens().lookupField(field, graphLens);
        FieldResolutionResult resolutionResult = appView.appInfo().resolveField(rewrittenField);
        return resolutionResult.isSingleFieldResolutionResult()
            && !appView.appInfo().isFieldRead(resolutionResult.getResolutionPair());
      }
      return false;
    }
  }

  static class EmptyNullCheckInserter extends NullCheckInserter {

    @Override
    public void insertNullCheckForInvokeReceiverIfNeeded(
        InvokeMethod invoke, InvokeMethod rewrittenInvoke, MethodLookupResult lookup) {
      // Intentionally empty.
    }

    @Override
    public void processWorklist() {
      // Intentionally empty.
    }
  }
}
