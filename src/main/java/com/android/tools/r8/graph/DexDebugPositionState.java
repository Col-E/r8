// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexDebugEvent.AdvanceLine;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetPositionFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;

/**
 * State machine to process and accumulate position-related DexDebugEvents. Clients should retrieve
 * the current state using the getters after a Default event.
 */
public class DexDebugPositionState implements DexDebugEventVisitor {

  private int currentPc = 0;
  private int currentLine;
  protected DexMethod currentMethod;
  protected boolean isCurrentMethodD8R8Synthesized;
  protected Position currentPosition;

  public DexDebugPositionState(int startLine, DexMethod method, boolean isD8R8Synthesized) {
    currentLine = startLine;
    currentMethod = method;
    isCurrentMethodD8R8Synthesized = isD8R8Synthesized;
  }

  @Override
  public void visit(AdvancePC advancePC) {
    assert advancePC.delta >= 0;
    currentPc += advancePC.delta;
  }

  @Override
  public void visit(AdvanceLine advanceLine) {
    currentLine += advanceLine.delta;
  }

  @Override
  public void visit(SetPositionFrame setPositionFrame) {
    assert setPositionFrame.getPosition() != null;
    Position position = setPositionFrame.getPosition();
    currentMethod = position.getMethod();
    isCurrentMethodD8R8Synthesized = position.isD8R8Synthesized();
    currentPosition = position;
  }

  @Override
  public void visit(Default defaultEvent) {
    assert defaultEvent.getPCDelta() >= 0;
    currentPc += defaultEvent.getPCDelta();
    currentLine += defaultEvent.getLineDelta();
  }

  @Override
  public void visit(SetFile setFile) {
    // Empty.
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

  public Position getPosition() {
    if (currentPosition == null) {
      return (getCurrentLine() > 0 ? SourcePosition.builder() : SyntheticPosition.builder())
          .setLine(getCurrentLine())
          .setMethod(currentMethod)
          .setIsD8R8Synthesized(isCurrentMethodD8R8Synthesized)
          .build();
    } else {
      return currentPosition.builderWithCopy().setLine(getCurrentLine()).build();
    }
  }
}
