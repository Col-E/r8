// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import java.util.Objects;

public class Position {

  private static final Position NO_POSITION = new Position(-1, null, null, null, false);

  public final int line;
  public final DexString file;
  public final boolean synthetic;

  // If there's no inlining, callerPosition is null.
  //
  // For an inlined instruction its Position contains the inlinee's line and method and
  // callerPosition is the position of the invoke instruction in the caller.

  public final DexMethod method;
  public final Position callerPosition;

  public Position(int line, DexString file, DexMethod method, Position callerPosition) {
    this(line, file, method, callerPosition, false);
    assert line >= 0;
  }

  private Position(
      int line, DexString file, DexMethod method, Position callerPosition, boolean synthetic) {
    this.line = line;
    this.file = file;
    this.synthetic = synthetic;
    this.method = method;
    this.callerPosition = callerPosition;
    assert callerPosition == null || callerPosition.method != null;
    assert line == -1 || method != null; // It's NO_POSITION or must have valid method.
  }

  public static Position synthetic(int line, DexMethod method, Position callerPosition) {
    assert line >= 0;
    return new Position(line, null, method, callerPosition, true);
  }

  public static Position none() {
    return NO_POSITION;
  }

  // This factory method is used by the Inliner to create Positions when the caller has no valid
  // positions. Since the callee still may have valid positions we need a non-null Position to set
  // it as the caller of the inlined Positions.
  public static Position noneWithMethod(DexMethod method, Position callerPosition) {
    assert method != null;
    return new Position(-1, null, method, callerPosition, false);
  }

  public boolean isNone() {
    return line == -1;
  }

  public boolean isSome() {
    return !isNone();
  }

  // Follow the linked list of callerPositions and return the last.
  // Return this if no inliner.
  public Position getOutermostCaller() {
    Position lastPosition = this;
    while (lastPosition.callerPosition != null) {
      lastPosition = lastPosition.callerPosition;
    }
    return lastPosition;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof Position) {
      Position o = (Position) other;
      return !isNone()
          && line == o.line
          && file == o.file
          && method == o.method
          && Objects.equals(callerPosition, o.callerPosition);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + Objects.hashCode(file);
    result = 31 * result + (synthetic ? 1 : 0);
    result = 31 * result + Objects.hashCode(method);
    result = 31 * result + Objects.hashCode(callerPosition);
    return result;
  }

  private String toString(boolean forceMethod) {
    if (isNone()) {
      return "--";
    }
    StringBuilder builder = new StringBuilder();
    if (file != null) {
      builder.append(file).append(":");
    }
    if (method != null && (forceMethod || callerPosition != null)) {
      builder.append("[").append(method).append("]");
    }
    builder.append("#").append(line);
    if (callerPosition != null) {
      builder.append(" <- ").append(callerPosition.toString(true));
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return toString(false);
  }
}
