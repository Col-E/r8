// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.Int2StructuralItemArrayMap;

public class MappedPosition {

  private final DexMethod method;
  private final int originalLine;
  private final Position caller;
  private final int obfuscatedLine;
  private final boolean isOutline;
  private final DexMethod outlineCallee;
  private final Int2StructuralItemArrayMap<Position> outlinePositions;

  public MappedPosition(
      DexMethod method,
      int originalLine,
      Position caller,
      int obfuscatedLine,
      boolean isOutline,
      DexMethod outlineCallee,
      Int2StructuralItemArrayMap<Position> outlinePositions) {
    this.method = method;
    this.originalLine = originalLine;
    this.caller = caller;
    this.obfuscatedLine = obfuscatedLine;
    this.isOutline = isOutline;
    this.outlineCallee = outlineCallee;
    this.outlinePositions = outlinePositions;
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getOriginalLine() {
    return originalLine;
  }

  public Position getCaller() {
    return caller;
  }

  public int getObfuscatedLine() {
    return obfuscatedLine;
  }

  public boolean isOutline() {
    return isOutline;
  }

  public DexMethod getOutlineCallee() {
    return outlineCallee;
  }

  public Int2StructuralItemArrayMap<Position> getOutlinePositions() {
    return outlinePositions;
  }

  public boolean isOutlineCaller() {
    return outlineCallee != null;
  }
}
