// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

public class RangeSubject {
  // For Dex backend, these are bytecode offset.
  // For CF backend, these are indices in the list of instructions.
  final int start;
  final int end;

  RangeSubject(int start, int end) {
    this.start = start;
    this.end = end;
  }

  // Returns true if the given instruction is within the current range.
  public boolean includes(InstructionOffsetSubject offsetSubject) {
    return this.start <= offsetSubject.offset && offsetSubject.offset <= this.end;
  }
}
