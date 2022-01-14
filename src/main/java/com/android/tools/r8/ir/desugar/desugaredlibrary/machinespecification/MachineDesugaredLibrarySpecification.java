// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

/** TODO(b/184026720): Move the rest of the flags. */
public class MachineDesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final MachineRewritingFlags rewritingFlags;

  public MachineDesugaredLibrarySpecification(
      boolean libraryCompilation, MachineRewritingFlags rewritingFlags) {
    this.libraryCompilation = libraryCompilation;
    this.rewritingFlags = rewritingFlags;
  }

  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public MachineRewritingFlags getRewritingFlags() {
    return rewritingFlags;
  }
}
