// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.List;

public class LocalVariableTable {
  public static class LocalVariableTableEntry {
    public final int index;
    public final String name;
    public final TypeSubject type;
    public final String signature;
    public final InstructionSubject start;
    public final InstructionSubject end;

    LocalVariableTableEntry(
        int index,
        String name,
        TypeSubject type,
        String signature,
        InstructionSubject start,
        InstructionSubject end) {
      this.index = index;
      this.name = name;
      this.type = type;
      this.signature = signature;
      this.start = start;
      this.end = end;
    }
  }

  private final List<LocalVariableTableEntry> localVariableTable;

  public LocalVariableTable(List<LocalVariableTableEntry> localVariableTable) {
    this.localVariableTable = localVariableTable;
  }

  public int size() {
    return localVariableTable.size();
  }

  public boolean isEmpty() {
    return localVariableTable.isEmpty();
  }

  public LocalVariableTableEntry get(int i) {
    return localVariableTable.get(i);
  }

  public List<LocalVariableTableEntry> getEntries() {
    return localVariableTable;
  }
}
