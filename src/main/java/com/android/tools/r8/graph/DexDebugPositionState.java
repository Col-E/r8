package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexDebugEvent.AdvanceLine;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetInlineFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.ir.code.Position;

/**
 * State machine to process and accumulate position-related DexDebugEvents. Clients should retrieve
 * the current state using the getters after a Default event.
 */
public class DexDebugPositionState implements DexDebugEventVisitor {
  private final DexMethod method;

  private int currentPc = 0;
  private int currentLine;
  private DexString currentFile = null;
  private DexMethod currentMethod = null;
  private Position currentCallerPosition = null;

  public DexDebugPositionState(int startLine, DexMethod method) {
    this.method = method;
    currentLine = startLine;
    currentMethod = method;
  }

  public void visit(AdvancePC advancePC) {
    assert advancePC.delta >= 0;
    currentPc += advancePC.delta;
  }

  public void visit(AdvanceLine advanceLine) {
    currentLine += advanceLine.delta;
  }

  public void visit(SetInlineFrame setInlineFrame) {
    assert (setInlineFrame.caller == null && setInlineFrame.callee == method)
        || (setInlineFrame.caller != null
            && setInlineFrame.caller.getOutermostCaller().method == method);
    currentMethod = setInlineFrame.callee;
    currentCallerPosition = setInlineFrame.caller;
  }

  public void visit(Default defaultEvent) {
    assert defaultEvent.getPCDelta() >= 0;
    currentPc += defaultEvent.getPCDelta();
    currentLine += defaultEvent.getLineDelta();
  }

  public void visit(SetFile setFile) {
    currentFile = setFile.fileName;
  }

  @Override
  public void visit(SetPrologueEnd setPrologueEnd) {
    // Empty.
  }

  @Override
  public void visit(SetEpilogueBegin setEpilogueBegin) {
    // Empty.
  }

  @Override
  public void visit(StartLocal startLocal) {
    // Empty.
  }

  @Override
  public void visit(EndLocal endLocal) {
    // Empty.
  }

  @Override
  public void visit(RestartLocal restartLocal) {
    // Empty.
  }

  public int getCurrentPc() {
    return currentPc;
  }

  public int getCurrentLine() {
    return currentLine;
  }

  public DexString getCurrentFile() {
    return currentFile;
  }

  public DexMethod getCurrentMethod() {
    return currentMethod;
  }

  public Position getCurrentCallerPosition() {
    return currentCallerPosition;
  }
}
