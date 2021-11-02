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
import com.android.tools.r8.graph.DexDebugEvent.SetInlineFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetOutlineCallerFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetOutlineFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.Int2StructuralItemArrayMap;

/**
 * State machine to process and accumulate position-related DexDebugEvents. Clients should retrieve
 * the current state using the getters after a Default event.
 */
public class DexDebugPositionState implements DexDebugEventVisitor {

  private int currentPc = 0;
  private int currentLine;
  private DexString currentFile = null;
  private DexMethod currentMethod;
  private Position currentCallerPosition = null;
  private boolean isOutline;
  private DexMethod outlineCallee;
  private Int2StructuralItemArrayMap<Position> outlineCallerPositions;

  public DexDebugPositionState(int startLine, DexMethod method) {
    currentLine = startLine;
    currentMethod = method;
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
  public void visit(SetInlineFrame setInlineFrame) {
    currentMethod = setInlineFrame.callee;
    currentCallerPosition = setInlineFrame.caller;
  }

  @Override
  public void visit(SetOutlineFrame setOutlineFrame) {
    isOutline = true;
  }

  @Override
  public void visit(SetOutlineCallerFrame setOutlineCallerFrame) {
    outlineCallee = setOutlineCallerFrame.getOutlineCallee();
    outlineCallerPositions = setOutlineCallerFrame.getOutlinePositions();
  }

  @Override
  public void visit(Default defaultEvent) {
    assert defaultEvent.getPCDelta() >= 0;
    currentPc += defaultEvent.getPCDelta();
    currentLine += defaultEvent.getLineDelta();
  }

  @Override
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

  public boolean isOutline() {
    return isOutline;
  }

  public DexMethod getOutlineCallee() {
    return outlineCallee;
  }

  public Int2StructuralItemArrayMap<Position> getOutlineCallerPositions() {
    return outlineCallerPositions;
  }

  public void resetOutlineInformation() {
    isOutline = false;
    outlineCallee = null;
    outlineCallerPositions = null;
  }
}
