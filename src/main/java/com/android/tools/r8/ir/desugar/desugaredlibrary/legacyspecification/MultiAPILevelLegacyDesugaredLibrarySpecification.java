// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification;

import com.android.tools.r8.origin.Origin;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class MultiAPILevelLegacyDesugaredLibrarySpecification {

  private final Origin origin;
  private final LegacyTopLevelFlags topLevelFlags;
  private final Int2ObjectMap<LegacyRewritingFlags> commonFlags;
  private final Int2ObjectMap<LegacyRewritingFlags> libraryFlags;
  private final Int2ObjectMap<LegacyRewritingFlags> programFlags;

  public MultiAPILevelLegacyDesugaredLibrarySpecification(
      Origin origin,
      LegacyTopLevelFlags topLevelFlags,
      Int2ObjectMap<LegacyRewritingFlags> commonFlags,
      Int2ObjectMap<LegacyRewritingFlags> libraryFlags,
      Int2ObjectMap<LegacyRewritingFlags> programFlags) {
    this.origin = origin;
    this.topLevelFlags = topLevelFlags;
    this.commonFlags = commonFlags;
    this.libraryFlags = libraryFlags;
    this.programFlags = programFlags;
  }

  public Origin getOrigin() {
    return origin;
  }

  public LegacyTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public Int2ObjectMap<LegacyRewritingFlags> getCommonFlags() {
    return commonFlags;
  }

  public Int2ObjectMap<LegacyRewritingFlags> getLibraryFlags() {
    return libraryFlags;
  }

  public Int2ObjectMap<LegacyRewritingFlags> getProgramFlags() {
    return programFlags;
  }
}
