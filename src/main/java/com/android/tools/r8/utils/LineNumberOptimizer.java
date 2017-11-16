// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEventBuilder;
import com.android.tools.r8.graph.DexDebugEventVisitor;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.naming.NamingLens;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class LineNumberOptimizer {

  // EventFilter is a visitor for DebugEvents, splits events into two sinks:
  // - Forwards non-positional events unchanged into a BypassedEventReceiver
  // - Forwards positional events, accumulated into DexDebugPositionStates, into
  //   positionEventReceiver.
  private static class EventFilter implements DexDebugEventVisitor {
    private final BypassedEventReceiver bypassedEventReceiver;
    private final PositionEventReceiver positionEventReceiver;

    private interface BypassedEventReceiver {
      void receiveBypassedEvent(DexDebugEvent event);
    }

    private interface PositionEventReceiver {
      void receivePositionEvent(DexDebugPositionState positionState);
    }

    private DexDebugPositionState positionState;

    private EventFilter(
        int startLine,
        DexMethod method,
        BypassedEventReceiver bypassedEventReceiver,
        PositionEventReceiver positionEventReceiver) {
      positionState = new DexDebugPositionState(startLine, method);
      this.bypassedEventReceiver = bypassedEventReceiver;
      this.positionEventReceiver = positionEventReceiver;
    }

    @Override
    public void visit(DexDebugEvent.SetPrologueEnd event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.SetEpilogueBegin event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.StartLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.EndLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.RestartLocal event) {
      bypassedEventReceiver.receiveBypassedEvent(event);
    }

    @Override
    public void visit(DexDebugEvent.AdvancePC advancePC) {
      positionState.visit(advancePC);
    }

    @Override
    public void visit(DexDebugEvent.AdvanceLine advanceLine) {
      positionState.visit(advanceLine);
    }

    @Override
    public void visit(DexDebugEvent.SetInlineFrame setInlineFrame) {
      positionState.visit(setInlineFrame);
    }

    @Override
    public void visit(DexDebugEvent.Default defaultEvent) {
      positionState.visit(defaultEvent);
      positionEventReceiver.receivePositionEvent(positionState);
    }

    @Override
    public void visit(DexDebugEvent.SetFile setFile) {
      positionState.visit(setFile);
    }
  }

  // PositionRemapper is a stateful function which takes a position (represented by a
  // DexDebugPositionState) and returns a remapped Position.
  private static class PositionRemapper {
    private int nextLineNumber;

    PositionRemapper(int nextLineNumber) {
      this.nextLineNumber = nextLineNumber;
    }

    private Position createRemappedPosition(DexDebugPositionState positionState) {
      // TODO(tamaskenez) Actual remapping is to be implemented here.
      // For now this is only identity-mapping.
      return new Position(
          positionState.getCurrentLine(),
          positionState.getCurrentFile(),
          positionState.getCurrentMethod(),
          positionState.getCurrentCallerPosition());
    }

    int getNextLineNumber() {
      return nextLineNumber;
    }
  }

  // PositionEventEmitter is a stateful function which converts a Position into series of
  // position-related DexDebugEvents and puts them into a processedEvents list.
  private static class PositionEventEmitter {
    private final DexItemFactory dexItemFactory;
    private int startLine = -1;
    private DexMethod method;
    private int previousPc = DexDebugEventBuilder.NO_PC_INFO;
    private Position previousPosition = null;
    private List<DexDebugEvent> processedEvents;

    private PositionEventEmitter(
        DexItemFactory dexItemFactory, DexMethod method, List<DexDebugEvent> processedEvents) {
      this.dexItemFactory = dexItemFactory;
      this.method = method;
      this.processedEvents = processedEvents;
    }

    private void emitPositionEvents(int currentPc, Position currentPosition) {
      if (previousPosition == null) {
        startLine = currentPosition.line;
        previousPosition = new Position(startLine, null, method, null);
      }
      DexDebugEventBuilder.emitAdvancementEvents(
          previousPc,
          previousPosition,
          currentPc,
          currentPosition,
          processedEvents,
          dexItemFactory);
      previousPc = currentPc;
      previousPosition = currentPosition;
    }

    private int getStartLine() {
      assert (startLine >= 0);
      return startLine;
    }
  }

  public static void run(
      DexApplication application, NamingLens namingLens, boolean identityMapping) {
    IdentityHashMap<DexString, List<DexProgramClass>> classesOfFiles = new IdentityHashMap<>();
    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : application.classes()) {

      // TODO(tamaskenez) fix b/69356670 and remove the conditional skipping.
      if (!clazz.getSynthesizedFrom().isEmpty()) {
        continue;
      }

      // Group methods by name
      IdentityHashMap<DexString, List<DexEncodedMethod>> methodsByName =
          new IdentityHashMap<>(clazz.directMethods().length + clazz.virtualMethods().length);
      clazz.forEachMethod(
          method -> {
            if (doesContainPositions(method)) {
              methodsByName.compute(
                  method.method.name,
                  (name, methods) -> {
                    if (methods == null) {
                      methods = new ArrayList<>();
                    }
                    methods.add(method);
                    return methods;
                  });
            }
          });
      for (List<DexEncodedMethod> methods : methodsByName.values()) {
        if (methods.size() > 1) {
          // If there are multiple methods with the same name (overloaded) then sort them for
          // deterministic behaviour: the algorithm will assign new line numbers in this order.
          // Methods with different names can share the same line numbers, that's why they don't
          // need to be sorted.
          methods.sort(
              (lhs, rhs) -> {
                int startLineDiff =
                    lhs.getCode().asDexCode().getDebugInfo().startLine
                        - rhs.getCode().asDexCode().getDebugInfo().startLine;
                if (startLineDiff != 0) return startLineDiff;
                return DexEncodedMethod.slowCompare(lhs, rhs);
              });
        }
        int nextLineNumber = 1;
        for (DexEncodedMethod method : methods) {
          // Do the actual processing for each method.
          DexCode dexCode = method.getCode().asDexCode();
          DexDebugInfo debugInfo = dexCode.getDebugInfo();
          List<DexDebugEvent> processedEvents = new ArrayList<>();

          // Our pipeline will be:
          // [debugInfo.events] -> eventFilter -> positionRemapper -> positionEventEmitter ->
          // [processedEvents]
          PositionEventEmitter positionEventEmitter =
              new PositionEventEmitter(application.dexItemFactory, method.method, processedEvents);
          PositionRemapper positionRemapper = new PositionRemapper(nextLineNumber);

          EventFilter eventFilter =
              new EventFilter(
                  debugInfo.startLine,
                  method.method,
                  processedEvents::add,
                  positionState -> {
                    Position position = positionRemapper.createRemappedPosition(positionState);
                    positionEventEmitter.emitPositionEvents(positionState.getCurrentPc(), position);
                  });
          for (DexDebugEvent event : debugInfo.events) {
            event.accept(eventFilter);
          }
          nextLineNumber = positionRemapper.getNextLineNumber();
          DexDebugInfo optimizedDebugInfo =
              new DexDebugInfo(
                  positionEventEmitter.getStartLine(),
                  debugInfo.parameters,
                  processedEvents.toArray(new DexDebugEvent[processedEvents.size()]));

          // TODO(tamaskenez) Remove this as soon as we have external tests testing not only the
          // remapping but whether the non-positional debug events remain intact.
          if (identityMapping) {
            assert (optimizedDebugInfo.startLine == debugInfo.startLine);
            assert (optimizedDebugInfo.events.length == debugInfo.events.length);
            for (int i = 0; i < debugInfo.events.length; ++i) {
              assert optimizedDebugInfo.events[i].equals(debugInfo.events[i]);
            }
          }
          dexCode.setDebugInfo(optimizedDebugInfo);
        }
      }
    }
  }

  // Return true if any of the methods' debug infos describe a Position which lists the containing
  // method as the outermost caller.
  private static boolean checkMethodsForSelfReferenceInPositions(DexEncodedMethod[] methods) {
    for (DexEncodedMethod method : methods) {
      if (!doesContainPositions(method)) {
        continue;
      }
      DexDebugInfo debugInfo = method.getCode().asDexCode().getDebugInfo();

      DexDebugPositionState positionState =
          new DexDebugPositionState(debugInfo.startLine, method.method);
      for (DexDebugEvent event : debugInfo.events) {
        event.accept(positionState);
        if (event instanceof DexDebugEvent.Default) {
          Position caller = positionState.getCurrentCallerPosition();
          DexMethod outermostMethod =
              caller == null
                  ? positionState.getCurrentMethod()
                  : caller.getOutermostCaller().method;
          if (outermostMethod == method.method) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean doesContainPositions(DexEncodedMethod method) {
    Code code = method.getCode();
    if (code == null || !code.isDexCode()) {
      return false;
    }
    DexDebugInfo debugInfo = code.asDexCode().getDebugInfo();
    if (debugInfo == null) {
      return false;
    }
    for (DexDebugEvent event : debugInfo.events) {
      if (event instanceof DexDebugEvent.Default) {
        return true;
      }
    }
    return false;
  }
}
