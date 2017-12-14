// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;

/** Enumeration of the possible output formats of compilation. */
public enum OutputMode {
  @Deprecated
  Indexed,

  @Deprecated
  FilePerInputClass,

  /** Produce DEX files using standard indexed-multidex for programs larger that a single file. */
  DexIndexed,

  /** Produce a DEX file for each Java class-file input file. */
  DexFilePerClassFile,

  /** Produce Java class files. */
  ClassFile;

  public boolean isDexIndexed() {
    return this == Indexed || this == DexIndexed;
  }

  public boolean isDexFilePerClassFile() {
    return this == FilePerInputClass || this == DexFilePerClassFile;
  }

  public boolean isClassFile() {
    return this == ClassFile;
  }

  public boolean isDeprecated() {
    return this == Indexed || this == FilePerInputClass;
  }

  public OutputMode toNonDeprecated() {
    assert isDeprecated();
    switch (this) {
      case Indexed:
        return DexIndexed;
      case FilePerInputClass:
        return DexFilePerClassFile;
      default:
        assert isDeprecated();
        throw new Unreachable();
    }
  }
}
