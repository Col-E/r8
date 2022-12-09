// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.ir.code.Position;

public class MappedPosition {

  private final int obfuscatedLine;
  private final Position position;

  public MappedPosition(Position position, int obfuscatedLine) {
    this.position = position;
    this.obfuscatedLine = obfuscatedLine;
  }

  public int getObfuscatedLine() {
    return obfuscatedLine;
  }

  public Position getPosition() {
    return position;
  }
}
