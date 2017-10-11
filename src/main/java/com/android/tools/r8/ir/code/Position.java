// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexString;

public class Position {

  private static final Position NO_POSITION = new Position(-1, null, false);

  public final int line;
  public final DexString file;
  public final boolean synthetic;

  public Position(int line, DexString file) {
    this(line, file, false);
    assert line >= 0;
  }

  private Position(int line, DexString file, boolean synthetic) {
    this.line = line;
    this.file = file;
    this.synthetic = synthetic;
  }

  public static Position synthetic(int line) {
    return new Position(line, null, true);
  }

  public static Position none() {
    return NO_POSITION;
  }

  public boolean isNone() {
    return this == NO_POSITION;
  }

  public boolean isSome() {
    return this != NO_POSITION;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof Position) {
      Position o = (Position) other;
      return !isNone() && line == o.line && file == o.file;
    }
    return false;
  }

  @Override
  public String toString() {
    if (isNone()) {
      return "--";
    }
    StringBuilder builder = new StringBuilder();
    if (file != null) {
      builder.append(file).append(":");
    }
    builder.append(line);
    return builder.toString();
  }
}
