// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

// Flags set based on the application when it was read.
// Note that in r8, once classes are pruned, the flags may not reflect the application anymore.
public class DexApplicationReadFlags {

  private final boolean hasReadProgramClassFromDex;
  private final boolean hasReadProgramClassFromCf;
  private final boolean hasReadRecordReferenceFromProgramClass;

  public DexApplicationReadFlags(
      boolean hasReadProgramClassFromDex,
      boolean hasReadProgramClassFromCf,
      boolean hasReadRecordReferenceFromProgramClass) {
    this.hasReadProgramClassFromDex = hasReadProgramClassFromDex;
    this.hasReadProgramClassFromCf = hasReadProgramClassFromCf;
    this.hasReadRecordReferenceFromProgramClass = hasReadRecordReferenceFromProgramClass;
  }

  public boolean hasReadProgramClassFromCf() {
    return hasReadProgramClassFromCf;
  }

  public boolean hasReadProgramClassFromDex() {
    return hasReadProgramClassFromDex;
  }

  public boolean hasReadRecordReferenceFromProgramClass() {
    return hasReadRecordReferenceFromProgramClass;
  }
}
