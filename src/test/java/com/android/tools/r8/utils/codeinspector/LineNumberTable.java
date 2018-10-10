// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;

public class LineNumberTable {
  private final Reference2IntMap<InstructionSubject> lineNumberTable;

  public LineNumberTable(Reference2IntMap<InstructionSubject> lineNumberTable) {
    this.lineNumberTable = lineNumberTable;
  }

  public IntCollection getLines() {
    return lineNumberTable.values();
  }
}
