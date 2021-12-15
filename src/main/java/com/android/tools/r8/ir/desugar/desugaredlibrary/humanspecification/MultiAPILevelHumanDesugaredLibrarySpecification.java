// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.origin.Origin;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;

public class MultiAPILevelHumanDesugaredLibrarySpecification {

  private final Origin origin;
  private final HumanTopLevelFlags topLevelFlags;
  private final Int2ObjectMap<HumanRewritingFlags> commonFlags;
  private final Int2ObjectMap<HumanRewritingFlags> libraryFlags;
  private final Int2ObjectMap<HumanRewritingFlags> programFlags;

  public MultiAPILevelHumanDesugaredLibrarySpecification(
      Origin origin,
      HumanTopLevelFlags topLevelFlags,
      Int2ObjectMap<HumanRewritingFlags> commonFlags,
      Int2ObjectMap<HumanRewritingFlags> libraryFlags,
      Int2ObjectMap<HumanRewritingFlags> programFlags) {
    this.origin = origin;
    this.topLevelFlags = topLevelFlags;
    this.commonFlags = commonFlags;
    this.libraryFlags = libraryFlags;
    this.programFlags = programFlags;
  }

  public Origin getOrigin() {
    return origin;
  }

  public HumanTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public Int2ObjectMap<HumanRewritingFlags> getCommonFlags() {
    return commonFlags;
  }

  public Int2ObjectMap<HumanRewritingFlags> getLibraryFlags() {
    return libraryFlags;
  }

  public Int2ObjectMap<HumanRewritingFlags> getProgramFlags() {
    return programFlags;
  }

  public Map<Integer, HumanRewritingFlags> getCommonFlagsForTesting() {
    return commonFlags;
  }

  public Map<Integer, HumanRewritingFlags> getLibraryFlagsForTesting() {
    return libraryFlags;
  }

  public Map<Integer, HumanRewritingFlags> getProgramFlagsForTesting() {
    return programFlags;
  }

}
