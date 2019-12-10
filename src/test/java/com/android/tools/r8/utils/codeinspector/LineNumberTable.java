// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

public class LineNumberTable {
  private final Object2IntMap<InstructionSubject> lineNumberTable;

  public LineNumberTable(Object2IntMap<InstructionSubject> lineNumberTable) {
    this.lineNumberTable = lineNumberTable;
  }

  public IntCollection getLines() {
    return lineNumberTable.values();
  }

  public int getLineForInstruction(InstructionSubject subject) {
    return lineNumberTable.getInt(subject);
  }
}
