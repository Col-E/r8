// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.origin.Origin;
import java.util.Map;

public class MultiAPILevelHumanDesugaredLibrarySpecification {

  private final Origin origin;
  private final HumanTopLevelFlags topLevelFlags;
  private final Map<ApiLevelRange, HumanRewritingFlags> commonFlags;
  private final Map<ApiLevelRange, HumanRewritingFlags> libraryFlags;
  private final Map<ApiLevelRange, HumanRewritingFlags> programFlags;

  public MultiAPILevelHumanDesugaredLibrarySpecification(
      Origin origin,
      HumanTopLevelFlags topLevelFlags,
      Map<ApiLevelRange, HumanRewritingFlags> commonFlags,
      Map<ApiLevelRange, HumanRewritingFlags> libraryFlags,
      Map<ApiLevelRange, HumanRewritingFlags> programFlags) {
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

  public Map<ApiLevelRange, HumanRewritingFlags> getCommonFlags() {
    return commonFlags;
  }

  public Map<ApiLevelRange, HumanRewritingFlags> getLibraryFlags() {
    return libraryFlags;
  }

  public Map<ApiLevelRange, HumanRewritingFlags> getProgramFlags() {
    return programFlags;
  }
}
