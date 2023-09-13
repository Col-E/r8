// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.DexDebugUtils.verifySetPositionFramesFollowedByDefaultEvent;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for constructing a list of debug events suitable for DexDebugInfo.
 *
 * This builder is intended to be very pedantic and ensure a well-formed structure of the resulting
 * event stream.
 */
public class DexDebugEventBuilder {

  public static final int NO_PC_INFO = -1;
  private static final int NO_LINE_INFO = -1;

  private final AppView<?> appView;
  private final DexEncodedMethod method;
  private final DexItemFactory factory;
  private final InternalOptions options;

  // In order list of non-this argument locals.
  private ArrayList<DebugLocalInfo> arguments;

  // Mapping from register to the last known local in that register (See DBG_RESTART_LOCAL).
  private Int2ReferenceMap<DebugLocalInfo> lastKnownLocals;

  // Mapping from register to local for currently open/visible locals.
  private Int2ReferenceMap<DebugLocalInfo> pendingLocals = null;

  // Conservative pending-state of locals to avoid some equality checks on locals.
  // pendingLocalChanges == true ==> localsEqual(emittedLocals, pendingLocals).
  private boolean pendingLocalChanges = false;

  // State of pc, line, file and locals in the emitted event stream.
  private int emittedPc = NO_PC_INFO;
  private Position emittedPosition = Position.none();
  private Int2ReferenceMap<DebugLocalInfo> emittedLocals;
  private final boolean isComposing;

  // Emitted events.
  private final List<DexDebugEvent> events = new ArrayList<>();

  // Initial known line for the method.
  private int startLine = NO_LINE_INFO;

  public DexDebugEventBuilder(AppView<?> appView, IRCode code) {
    this.appView = appView;
    method = code.context().getDefinition();
    factory = appView.dexItemFactory();
    options = appView.options();
    isComposing =
        options.mappingComposeOptions().enableExperimentalMappingComposition
            && appView.appInfo().app().getProguardMap() != null;
  }

  /** Add events at pc for instruction. */
  public void add(int pc, int postPc, Instruction instruction, ProgramMethod context) {
    boolean isBlockEntry = instruction.getBlock().entry() == instruction;
    boolean isBlockExit = instruction.getBlock().exit() == instruction;

    // Initialize locals state on block entry.
    if (isBlockEntry) {
      updateBlockEntry(instruction);
    }
    assert pendingLocals != null;

    Position position = instruction.getPosition();
    boolean pcAdvancing = pc != postPc;

    // In debug mode check that all non-nop instructions have positions.
    assert instruction.verifyValidPositionInfo(options.debug);

    if (instruction.isArgument()) {
      startArgument(instruction.asArgument());
    } else if (instruction.isDebugLocalsChange()) {
      updateLocals(instruction.asDebugLocalsChange());
    } else if (pcAdvancing) {
      if (!position.isNone() && !position.equals(emittedPosition)) {
        if (options.debug
            || instruction.instructionInstanceCanThrow(appView, context)
            || isComposing) {
          emitDebugPosition(pc, position);
        }
      }
      if (emittedPc != pc) {
        emitLocalChanges(pc);
      }
    }

    if (isBlockExit) {
      // If this is the end of the block clear out the pending state.
      pendingLocals = null;
      pendingLocalChanges = false;
    }
  }

  /** Build the resulting DexDebugInfo object. */
  public DexDebugInfo build() {
    assert pendingLocals == null;
    assert !pendingLocalChanges;
    if (startLine == NO_LINE_INFO) {
      return null;
    }
    DexString[] params = new DexString[method.getReference().getArity()];
    if (arguments != null) {
      assert params.length == arguments.size();
      for (int i = 0; i < arguments.size(); i++) {
        DebugLocalInfo local = arguments.get(i);
        params[i] = (local == null || local.signature != null) ? null : local.name;
      }
    }
    assert verifySetPositionFramesFollowedByDefaultEvent(events);
    return new EventBasedDebugInfo(startLine, params, events.toArray(DexDebugEvent.EMPTY_ARRAY));
  }

  private void updateBlockEntry(Instruction instruction) {
    assert pendingLocals == null;
    assert !pendingLocalChanges;
    Int2ReferenceMap<DebugLocalInfo> locals = instruction.getBlock().getLocalsAtEntry();
    if (locals == null) {
      pendingLocals = Int2ReferenceMaps.emptyMap();
    } else {
      pendingLocals = new Int2ReferenceOpenHashMap<>(locals);
      pendingLocalChanges = true;
    }
    if (emittedLocals == null) {
      initialize(locals);
    }
  }

  private void initialize(Int2ReferenceMap<DebugLocalInfo> locals) {
    assert arguments == null;
    assert emittedLocals == null;
    assert lastKnownLocals == null;
    assert startLine == NO_LINE_INFO;
    if (locals == null) {
      emittedLocals = Int2ReferenceMaps.emptyMap();
      lastKnownLocals = Int2ReferenceMaps.emptyMap();
      return;
    }
    // Implicitly open all unparameterized arguments.
    emittedLocals = new Int2ReferenceOpenHashMap<>();
    for (Entry<DebugLocalInfo> entry : locals.int2ReferenceEntrySet()) {
      if (entry.getValue().signature == null) {
        emittedLocals.put(entry.getIntKey(), entry.getValue());
      } else if (options.testing.emitDebugLocalStartBeforeDefaultEvent) {
        events.add(new StartLocal(entry.getIntKey(), entry.getValue()));
        emittedLocals.put(entry.getIntKey(), entry.getValue());
      }
    }
    lastKnownLocals = new Int2ReferenceOpenHashMap<>(emittedLocals);
  }

  private void startArgument(Argument argument) {
    if (arguments == null) {
      arguments = new ArrayList<>(method.getReference().getArity());
    }
    if (!argument.outValue().isThis()) {
      arguments.add(argument.getLocalInfo());
    }
  }

  private void updateLocals(DebugLocalsChange change) {
    pendingLocalChanges = true;
    change.apply(pendingLocals);
  }

  private boolean localsChanged() {
    if (!pendingLocalChanges) {
      return false;
    }
    pendingLocalChanges = !DebugLocalInfo.localsInfoMapsEqual(emittedLocals, pendingLocals);
    return pendingLocalChanges;
  }

  private void emitDebugPosition(int pc, Position position) {
    assert !position.equals(emittedPosition);
    if (startLine == NO_LINE_INFO) {
      assert emittedPosition.isNone();
      if (position.isSyntheticPosition() && !position.hasCallerPosition()) {
        // Ignore synthetic positions prior to any actual position.
        // We do need to preserve synthetic position establishing the stack frame for inlined
        // methods.
        return;
      }
      startLine = position.getLine();
      emittedPosition =
          SourcePosition.builder()
              .setLine(position.getLine())
              .setMethod(position.getOutermostCaller().getMethod())
              .build();
    }
    assert emittedPc != pc;
    int previousPc = emittedPc == NO_PC_INFO ? 0 : emittedPc;
    emitAdvancementEvents(previousPc, emittedPosition, pc, position, events, factory, false);
    emittedPc = pc;
    emittedPosition = position;
    if (localsChanged()) {
      emitLocalChangeEvents(emittedLocals, pendingLocals, lastKnownLocals, events, factory);
      assert DebugLocalInfo.localsInfoMapsEqual(emittedLocals, pendingLocals);
    }
    pendingLocalChanges = false;
  }

  private void emitLocalChanges(int pc) {
    // If pc advanced since the locals changed and locals indeed have changed, emit the changes.
    if (localsChanged()) {
      assert emittedPc != pc;
      int pcDelta = emittedPc == NO_PC_INFO ? pc : pc - emittedPc;
      assert pcDelta > 0 || emittedPc == NO_PC_INFO;
      if (pcDelta > 0) {
        events.add(factory.createAdvancePC(pcDelta));
      }
      emittedPc = pc;
      emitLocalChangeEvents(emittedLocals, pendingLocals, lastKnownLocals, events, factory);
      pendingLocalChanges = false;
      assert DebugLocalInfo.localsInfoMapsEqual(emittedLocals, pendingLocals);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public static void emitAdvancementEvents(
      int previousPc,
      Position previousPosition,
      int nextPc,
      Position nextPosition,
      List<DexDebugEvent> events,
      DexItemFactory factory,
      boolean optimizingLineNumbers) {
    assert previousPc >= 0;
    int pcDelta = nextPc - previousPc;
    assert !previousPosition.isNone() || nextPosition.isNone();
    assert nextPosition.isNone() || nextPosition.getLine() >= 0;
    int lineDelta = nextPosition.isNone() ? 0 : nextPosition.getLine() - previousPosition.getLine();
    assert pcDelta >= 0;
    if (nextPosition.getFile() != previousPosition.getFile()) {
      events.add(factory.createSetFile(nextPosition.getFile()));
    }
    // The LineNumberOptimizer maps new positions based on the outer most caller with
    // callerPosition == null.
    assert nextPosition.hasCallerPosition()
        || previousPosition.hasCallerPosition()
        || nextPosition.getMethod() == previousPosition.getMethod()
        || optimizingLineNumbers;
    boolean isNewPosition =
        nextPosition.getCallerPosition() != previousPosition.getCallerPosition()
            || nextPosition.getMethod() != previousPosition.getMethod();
    boolean isOutline = nextPosition.isOutline();
    boolean isOutlineCallerWithInfo =
        nextPosition.isOutlineCaller()
            && nextPosition.getOutlineCallee() != null
            && !nextPosition.getOutlinePositions().isEmpty();
    boolean isNoLongerOutlineCaller =
        previousPosition.isOutlineCaller() && !nextPosition.isOutlineCaller();
    if (isNewPosition || isOutline || isOutlineCallerWithInfo || isNoLongerOutlineCaller) {
      events.add(factory.createPositionFrame(nextPosition));
    }
    addDefaultEventWithAdvancePcIfNecessary(lineDelta, pcDelta, events, factory);
  }

  public static void addDefaultEventWithAdvancePcIfNecessary(
      int lineDelta, int pcDelta, List<DexDebugEvent> events, DexItemFactory factory) {
    if (lineDelta < Constants.DBG_LINE_BASE
        || lineDelta - Constants.DBG_LINE_BASE >= Constants.DBG_LINE_RANGE) {
      events.add(factory.createAdvanceLine(lineDelta));
      // TODO(herhut): To be super clever, encode only the part that is above limit.
      lineDelta = 0;
    }
    int specialOpcode = Default.computeSpecialOpcode(lineDelta, pcDelta);
    if (specialOpcode > Constants.DBG_LAST_SPECIAL) {
      events.add(factory.createAdvancePC(pcDelta));
      // TODO(herhut): To be super clever, encode only the part that is above limit.
      specialOpcode = Default.computeSpecialOpcode(lineDelta, 0);
    }
    assert specialOpcode >= Constants.DBG_FIRST_SPECIAL;
    assert specialOpcode <= Constants.DBG_LAST_SPECIAL;
    events.add(factory.createDefault(specialOpcode));
  }

  @SuppressWarnings("ReferenceEquality")
  private static void emitLocalChangeEvents(
      Int2ReferenceMap<DebugLocalInfo> previousLocals,
      Int2ReferenceMap<DebugLocalInfo> nextLocals,
      Int2ReferenceMap<DebugLocalInfo> lastKnownLocals,
      List<DexDebugEvent> events,
      DexItemFactory factory) {
    Int2ReferenceSortedMap<DebugLocalInfo> ending =
        DebugLocalInfo.endingLocals(previousLocals, nextLocals);
    Int2ReferenceSortedMap<DebugLocalInfo> starting =
        DebugLocalInfo.startingLocals(previousLocals, nextLocals);
    assert !ending.isEmpty() || !starting.isEmpty();
    for (Entry<DebugLocalInfo> end : ending.int2ReferenceEntrySet()) {
      int register = end.getIntKey();
      if (!starting.containsKey(register)) {
        previousLocals.remove(register);
        events.add(factory.createEndLocal(register));
      }
    }
    for (Entry<DebugLocalInfo> start : starting.int2ReferenceEntrySet()) {
      int register = start.getIntKey();
      DebugLocalInfo local = start.getValue();
      previousLocals.put(register, local);
      if (lastKnownLocals.get(register) == local) {
        events.add(factory.createRestartLocal(register));
      } else {
        events.add(new StartLocal(register, local));
        lastKnownLocals.put(register, local);
      }
    }
  }
}
