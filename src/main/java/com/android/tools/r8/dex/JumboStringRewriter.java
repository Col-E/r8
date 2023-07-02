// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.graph.DexCode.TryHandler.NO_HANDLER;
import static com.android.tools.r8.graph.DexDebugEventBuilder.addDefaultEventWithAdvancePcIfNecessary;

import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstStringJumbo;
import com.android.tools.r8.dex.code.DexFormat21t;
import com.android.tools.r8.dex.code.DexFormat22t;
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
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNop;
import com.android.tools.r8.dex.code.DexSwitchPayload;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;

public class JumboStringRewriter {

  private static class TryTargets {
    private DexInstruction start;
    private DexInstruction end;
    private final boolean endsAfterLastInstruction;

    TryTargets(DexInstruction start, DexInstruction end, boolean endsAfterLastInstruction) {
      assert start != null;
      assert end != null;
      this.start = start;
      this.end = end;
      this.endsAfterLastInstruction = endsAfterLastInstruction;
    }

    void replaceTarget(DexInstruction target, DexInstruction newTarget) {
      if (start == target) {
        start = newTarget;
      }
      if (end == target) {
        end = newTarget;
      }
    }

    int getStartOffset() {
      return start.getOffset();
    }

    int getStartToEndDelta() {
      if (endsAfterLastInstruction) {
        return end.getOffset() + end.getSize() - start.getOffset();
      }
      return end.getOffset() - start.getOffset();
    }
  }

  private final DexEncodedMethod method;
  private final DexString firstJumboString;
  private final BooleanSupplier materializeInfoForNativePc;
  private final DexItemFactory factory;
  private final Map<DexInstruction, List<DexInstruction>> instructionTargets =
      new IdentityHashMap<>();
  private EventBasedDebugInfo debugEventBasedInfo = null;
  private final Int2ReferenceMap<DexInstruction> debugEventTargets =
      new Int2ReferenceOpenHashMap<>();
  private final Map<DexInstruction, DexInstruction> payloadToSwitch = new IdentityHashMap<>();
  private final Map<Try, TryTargets> tryTargets = new IdentityHashMap<>();
  private final Int2ReferenceMap<DexInstruction> tryRangeStartAndEndTargets =
      new Int2ReferenceOpenHashMap<>();
  private final Map<TryHandler, List<DexInstruction>> handlerTargets = new IdentityHashMap<>();

  public JumboStringRewriter(
      DexEncodedMethod method,
      DexString firstJumboString,
      BooleanSupplier materializeInfoForNativePc,
      DexItemFactory factory) {
    this.method = method;
    this.firstJumboString = firstJumboString;
    this.materializeInfoForNativePc = materializeInfoForNativePc;
    this.factory = factory;
  }

  private DexCode getCode() {
    return method.getCode().asDexCode();
  }

  public DexCode rewrite() {
    // Build maps from everything in the code that uses offsets or direct addresses to reference
    // instructions to the actual instruction referenced.
    recordTargets();
    // Expand the code by rewriting jumbo strings and branching instructions.
    List<DexInstruction> newInstructions = expandCode();
    // Commit to the new instruction offsets and update instructions, try-catch structures
    // and debug info with the new offsets.
    rewriteInstructionOffsets(newInstructions);
    Try[] newTries = rewriteTryOffsets();
    TryHandler[] newHandlers = rewriteHandlerOffsets();
    DexDebugInfo newDebugInfo = rewriteDebugInfoOffsets();
    // Set the new code on the method.
    DexCode oldCode = getCode();
    DexCode newCode =
        new DexCode(
            oldCode.registerSize,
            oldCode.incomingRegisterSize,
            oldCode.outgoingRegisterSize,
            newInstructions.toArray(DexInstruction.EMPTY_ARRAY),
            newTries,
            newHandlers,
            newDebugInfo);
    // As we have rewritten the code, we now know that its highest string index that is not
    // a jumbo-string is firstJumboString (actually the previous string, but we do not have that).
    newCode.setHighestSortingStringForJumboProcessedCode(firstJumboString);
    return newCode;
  }

  private void rewriteInstructionOffsets(List<DexInstruction> instructions) {
    for (DexInstruction instruction : instructions) {
      if (instruction instanceof DexFormat22t) { // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
        DexFormat22t condition = (DexFormat22t) instruction;
        int offset = instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        condition.CCCC = (short) offset;
      } else if (instruction instanceof DexFormat21t) { // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
        DexFormat21t condition = (DexFormat21t) instruction;
        int offset = instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        condition.BBBB = (short) offset;
      } else if (instruction instanceof DexGoto) {
        DexGoto jump = (DexGoto) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        assert Byte.MIN_VALUE <= offset && offset <= Byte.MAX_VALUE;
        jump.AA = (byte) offset;
      } else if (instruction instanceof DexGoto16) {
        DexGoto16 jump = (DexGoto16) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        jump.AAAA = (short) offset;
      } else if (instruction instanceof DexGoto32) {
        DexGoto32 jump = (DexGoto32) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        jump.AAAAAAAA = offset;
      } else if (instruction.hasPayload()) { // FillArrayData, SparseSwitch, PackedSwitch
        DexFormat31t payloadUser = (DexFormat31t) instruction;
        int offset =
            instructionTargets.get(payloadUser).get(0).getOffset() - instruction.getOffset();
        payloadUser.setPayloadOffset(offset);
      } else if (instruction instanceof DexSwitchPayload) {
        DexSwitchPayload payload = (DexSwitchPayload) instruction;
        DexInstruction switchInstruction = payloadToSwitch.get(payload);
        List<DexInstruction> switchTargets = instructionTargets.get(payload);
        int[] targets = payload.switchTargetOffsets();
        for (int i = 0; i < switchTargets.size(); i++) {
          DexInstruction target = switchTargets.get(i);
          targets[i] = target.getOffset() - switchInstruction.getOffset();
        }
      }
    }
  }

  private Try[] rewriteTryOffsets() {
    DexCode code = getCode();
    Try[] result = new Try[code.tries.length];
    for (int i = 0; i < code.tries.length; i++) {
      Try theTry = code.tries[i];
      TryTargets targets = tryTargets.get(theTry);
      result[i] = new Try(targets.getStartOffset(), targets.getStartToEndDelta(), -1);
      result[i].handlerIndex = theTry.handlerIndex;
    }
    return result;
  }

  private TryHandler[] rewriteHandlerOffsets() {
    DexCode code = getCode();
    TryHandler[] result = new TryHandler[code.handlers.length];
    for (int i = 0; i < code.handlers.length; i++) {
      TryHandler handler = code.handlers[i];
      List<DexInstruction> targets = handlerTargets.get(handler);
      Iterator<DexInstruction> it = targets.iterator();
      int catchAllAddr = NO_HANDLER;
      if (handler.catchAllAddr != NO_HANDLER) {
        catchAllAddr = it.next().getOffset();
      }
      TypeAddrPair[] newPairs = new TypeAddrPair[handler.pairs.length];
      for (int j = 0; j < handler.pairs.length; j++) {
        TypeAddrPair pair = handler.pairs[j];
        newPairs[j] = new TypeAddrPair(pair.getType(), it.next().getOffset());
      }
      result[i] = new TryHandler(newPairs, catchAllAddr);
    }
    return result;
  }

  private DexDebugInfo rewriteDebugInfoOffsets() {
    DexCode code = getCode();
    if (!debugEventTargets.isEmpty()) {
      assert debugEventBasedInfo != null;
      int lastOriginalOffset = 0;
      int lastNewOffset = 0;
      List<DexDebugEvent> events = new ArrayList<>();
      for (DexDebugEvent event : debugEventBasedInfo.events) {
        if (event instanceof AdvancePC) {
          AdvancePC advance = (AdvancePC) event;
          lastOriginalOffset += advance.delta;
          DexInstruction target = debugEventTargets.get(lastOriginalOffset);
          int pcDelta = target.getOffset() - lastNewOffset;
          events.add(factory.createAdvancePC(pcDelta));
          lastNewOffset = target.getOffset();
        } else if (event instanceof Default) {
          Default defaultEvent = (Default) event;
          lastOriginalOffset += defaultEvent.getPCDelta();
          DexInstruction target = debugEventTargets.get(lastOriginalOffset);
          int lineDelta = defaultEvent.getLineDelta();
          int pcDelta = target.getOffset() - lastNewOffset;
          addDefaultEventWithAdvancePcIfNecessary(lineDelta, pcDelta, events, factory);
          lastNewOffset = target.getOffset();
        } else {
          events.add(event);
        }
      }
      return new EventBasedDebugInfo(
          debugEventBasedInfo.startLine,
          debugEventBasedInfo.parameters,
          events.toArray(DexDebugEvent.EMPTY_ARRAY));
    }
    return code.getDebugInfo();
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private List<DexInstruction> expandCode() {
    LinkedList<DexInstruction> instructions = new LinkedList<>();
    Collections.addAll(instructions, getCode().instructions);
    int offsetDelta;
    do {
      ListIterator<DexInstruction> it = instructions.listIterator();
      offsetDelta = 0;
      while (it.hasNext()) {
        DexInstruction instruction = it.next();
        int orignalOffset = instruction.getOffset();
        instruction.setOffset(orignalOffset + offsetDelta);
        if (instruction instanceof DexConstString) {
          DexConstString string = (DexConstString) instruction;
          if (string.getString().compareTo(firstJumboString) >= 0) {
            DexConstStringJumbo jumboString =
                new DexConstStringJumbo(string.AA, string.getString());
            jumboString.setOffset(string.getOffset());
            offsetDelta++;
            it.set(jumboString);
            replaceTarget(instruction, jumboString);
          }
        } else if (instruction instanceof DexFormat22t) { // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
          DexFormat22t condition = (DexFormat22t) instruction;
          int offset =
              instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            DexFormat22t newCondition = null;
            switch (condition.getType().inverted()) {
              case EQ:
                newCondition = new DexIfEq(condition.A, condition.B, 0);
                break;
              case GE:
                newCondition = new DexIfGe(condition.A, condition.B, 0);
                break;
              case GT:
                newCondition = new DexIfGt(condition.A, condition.B, 0);
                break;
              case LE:
                newCondition = new DexIfLe(condition.A, condition.B, 0);
                break;
              case LT:
                newCondition = new DexIfLt(condition.A, condition.B, 0);
                break;
              case NE:
                newCondition = new DexIfNe(condition.A, condition.B, 0);
                break;
            }
            offsetDelta = rewriteIfToIfAndGoto(offsetDelta, it, condition, newCondition);
          }
        } else if (instruction
            instanceof DexFormat21t) { // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
          DexFormat21t condition = (DexFormat21t) instruction;
          int offset =
              instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            DexFormat21t newCondition = null;
            switch (condition.getType().inverted()) {
              case EQ:
                newCondition = new DexIfEqz(condition.AA, 0);
                break;
              case GE:
                newCondition = new DexIfGez(condition.AA, 0);
                break;
              case GT:
                newCondition = new DexIfGtz(condition.AA, 0);
                break;
              case LE:
                newCondition = new DexIfLez(condition.AA, 0);
                break;
              case LT:
                newCondition = new DexIfLtz(condition.AA, 0);
                break;
              case NE:
                newCondition = new DexIfNez(condition.AA, 0);
                break;
            }
            offsetDelta = rewriteIfToIfAndGoto(offsetDelta, it, condition, newCondition);
          }
        } else if (instruction instanceof DexGoto) {
          DexGoto jump = (DexGoto) instruction;
          int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
          if (Byte.MIN_VALUE > offset || offset > Byte.MAX_VALUE) {
            DexInstruction newJump;
            if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
              newJump = new DexGoto32(offset);
            } else {
              newJump = new DexGoto16(offset);
            }
            newJump.setOffset(jump.getOffset());
            it.set(newJump);
            offsetDelta += (newJump.getSize() - jump.getSize());
            replaceTarget(jump, newJump);
            List<DexInstruction> targets = instructionTargets.remove(jump);
            instructionTargets.put(newJump, targets);
          }
        } else if (instruction instanceof DexGoto16) {
          DexGoto16 jump = (DexGoto16) instruction;
          int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            DexInstruction newJump = new DexGoto32(offset);
            newJump.setOffset(jump.getOffset());
            it.set(newJump);
            offsetDelta += (newJump.getSize() - jump.getSize());
            replaceTarget(jump, newJump);
            List<DexInstruction> targets = instructionTargets.remove(jump);
            instructionTargets.put(newJump, targets);
          }
        } else if (instruction instanceof DexGoto32) {
          // Instruction big enough for any offset.
        } else if (instruction.hasPayload()) { // FillArrayData, SparseSwitch, PackedSwitch
          // Instruction big enough for any offset.
        } else if (instruction.isPayload()) {
          // Payload instructions must be 4 byte aligned (instructions are 2 bytes).
          if (instruction.getOffset() % 2 != 0) {
            it.previous();
            // Check if the previous instruction was a simple nop. If that is the case, remove it
            // to make the alignment instead of adding another one. Only allow removal if this
            // instruction is not targeted by anything. See b/78072750.
            DexInstruction instructionBeforePayload = it.hasPrevious() ? it.previous() : null;
            if (instructionBeforePayload != null
                && instructionBeforePayload.isSimpleNop()
                && debugEventTargets.get(orignalOffset) == null
                && tryRangeStartAndEndTargets.get(orignalOffset) == null) {
              it.remove();
              offsetDelta--;
            } else {
              if (instructionBeforePayload != null) {
                it.next();
              }
              DexNop nop = new DexNop();
              nop.setOffset(instruction.getOffset());
              it.add(nop);
              offsetDelta++;
            }
            instruction.setOffset(orignalOffset + offsetDelta);
            it.next();
          }
          // Instruction big enough for any offset.
        }
      }
    } while (offsetDelta > 0);
    return instructions;
  }

  private int rewriteIfToIfAndGoto(
      int offsetDelta,
      ListIterator<DexInstruction> it,
      DexInstruction condition,
      DexInstruction newCondition) {
    int jumpOffset = condition.getOffset() + condition.getSize();
    DexGoto32 jump = new DexGoto32(0);
    jump.setOffset(jumpOffset);
    newCondition.setOffset(condition.getOffset());
    it.set(newCondition);
    replaceTarget(condition, newCondition);
    it.add(jump);
    offsetDelta += jump.getSize();
    instructionTargets.put(jump, instructionTargets.remove(condition));
    DexInstruction fallthroughInstruction = it.next();
    instructionTargets.put(newCondition, Lists.newArrayList(fallthroughInstruction));
    it.previous();
    return offsetDelta;
  }

  private void replaceTarget(DexInstruction target, DexInstruction newTarget) {
    for (List<DexInstruction> instructions : instructionTargets.values()) {
      instructions.replaceAll((i) -> i == target ? newTarget : i);
    }
    for (Int2ReferenceMap.Entry<DexInstruction> entry : debugEventTargets.int2ReferenceEntrySet()) {
      if (entry.getValue() == target) {
        entry.setValue(newTarget);
      }
    }
    for (Entry<Try, TryTargets> entry : tryTargets.entrySet()) {
      entry.getValue().replaceTarget(target, newTarget);
    }
    for (List<DexInstruction> instructions : handlerTargets.values()) {
      instructions.replaceAll((i) -> i == target ? newTarget : i);
    }
  }

  private void recordInstructionTargets(Int2ReferenceMap<DexInstruction> offsetToInstruction) {
    DexInstruction[] instructions = getCode().instructions;
    for (DexInstruction instruction : instructions) {
      if (instruction instanceof DexFormat22t) { // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
        DexFormat22t condition = (DexFormat22t) instruction;
        DexInstruction target = offsetToInstruction.get(condition.getOffset() + condition.CCCC);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof DexFormat21t) { // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
        DexFormat21t condition = (DexFormat21t) instruction;
        DexInstruction target = offsetToInstruction.get(condition.getOffset() + condition.BBBB);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof DexGoto) {
        DexGoto jump = (DexGoto) instruction;
        DexInstruction target = offsetToInstruction.get(jump.getOffset() + jump.AA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof DexGoto16) {
        DexGoto16 jump = (DexGoto16) instruction;
        DexInstruction target = offsetToInstruction.get(jump.getOffset() + jump.AAAA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof DexGoto32) {
        DexGoto32 jump = (DexGoto32) instruction;
        DexInstruction target = offsetToInstruction.get(jump.getOffset() + jump.AAAAAAAA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction.hasPayload()) { // FillArrayData, SparseSwitch, PackedSwitch
        DexFormat31t offsetInstruction = (DexFormat31t) instruction;
        DexInstruction target =
            offsetToInstruction.get(
                offsetInstruction.getOffset() + offsetInstruction.getPayloadOffset());
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof DexSwitchPayload) {
        DexSwitchPayload payload = (DexSwitchPayload) instruction;
        int[] targetOffsets = payload.switchTargetOffsets();
        int switchOffset = payloadToSwitch.get(instruction).getOffset();
        List<DexInstruction> targets = new ArrayList<>();
        for (int i = 0; i < targetOffsets.length; i++) {
          DexInstruction target = offsetToInstruction.get(switchOffset + targetOffsets[i]);
          assert target != null;
          targets.add(target);
        }
        instructionTargets.put(instruction, targets);
      }
    }
  }

  private void recordDebugEventTargets(Int2ReferenceMap<DexInstruction> offsetToInstruction) {
    EventBasedDebugInfo eventBasedInfo = DexDebugInfo.convertToEventBased(getCode(), factory);
    if (eventBasedInfo == null) {
      if (materializeInfoForNativePc.getAsBoolean()) {
        eventBasedInfo =
            DexDebugInfo.createEventBasedDebugInfoForNativePc(
                method.getParameters().size(), getCode(), factory);
      } else {
        return;
      }
    }
    debugEventBasedInfo = eventBasedInfo;
    int address = 0;
    for (DexDebugEvent event : eventBasedInfo.events) {
      if (event instanceof AdvancePC) {
        AdvancePC advance = (AdvancePC) event;
        address += advance.delta;
        DexInstruction target = offsetToInstruction.get(address);
        assert target != null;
        debugEventTargets.put(address, target);
      } else if (event instanceof Default) {
        Default defaultEvent = (Default) event;
        address += defaultEvent.getPCDelta();
        DexInstruction target = offsetToInstruction.get(address);
        assert target != null;
        debugEventTargets.put(address, target);
      }
    }
  }

  private void recordTryAndHandlerTargets(
      Int2ReferenceMap<DexInstruction> offsetToInstruction, DexInstruction lastInstruction) {
    DexCode code = getCode();
    for (Try theTry : code.tries) {
      DexInstruction start = offsetToInstruction.get(theTry.startAddress);
      DexInstruction end = null;
      int endAddress = theTry.startAddress + theTry.instructionCount;
      TryTargets targets;
      if (endAddress > lastInstruction.getOffset()) {
        end = lastInstruction;
        targets = new TryTargets(start, lastInstruction, true);
      } else {
        end = offsetToInstruction.get(endAddress);
        targets = new TryTargets(start, end, false);
      }
      assert theTry.startAddress == targets.getStartOffset();
      assert theTry.instructionCount == targets.getStartToEndDelta();
      tryTargets.put(theTry, targets);
      tryRangeStartAndEndTargets.put(start.getOffset(), start);
      tryRangeStartAndEndTargets.put(end.getOffset(), end);
    }
    for (TryHandler handler : code.handlers) {
      List<DexInstruction> targets = new ArrayList<>();
      if (handler.catchAllAddr != NO_HANDLER) {
        DexInstruction target = offsetToInstruction.get(handler.catchAllAddr);
        assert target != null;
        targets.add(target);
      }
      for (TypeAddrPair pair : handler.pairs) {
        DexInstruction target = offsetToInstruction.get(pair.addr);
        assert target != null;
        targets.add(target);
      }
      handlerTargets.put(handler, targets);
    }
  }

  private void recordTargets() {
    Int2ReferenceMap<DexInstruction> offsetToInstruction = new Int2ReferenceOpenHashMap<>();
    DexInstruction[] instructions = getCode().instructions;
    boolean containsPayloads = false;
    for (DexInstruction instruction : instructions) {
      offsetToInstruction.put(instruction.getOffset(), instruction);
      if (instruction.hasPayload()) { // FillArrayData, SparseSwitch, PackedSwitch
        containsPayloads = true;
      }
    }
    if (containsPayloads) {
      for (DexInstruction instruction : instructions) {
        if (instruction.hasPayload()) { // FillArrayData, SparseSwitch, PackedSwitch
          DexInstruction payload =
              offsetToInstruction.get(instruction.getOffset() + instruction.getPayloadOffset());
          assert payload != null;
          payloadToSwitch.put(payload, instruction);
        }
      }
    }
    recordInstructionTargets(offsetToInstruction);
    recordDebugEventTargets(offsetToInstruction);
    DexInstruction lastInstruction = instructions[instructions.length - 1];
    recordTryAndHandlerTargets(offsetToInstruction, lastInstruction);
  }
}
