// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.origin.Origin;
import java.util.Map;

public class MultiAPILevelMachineDesugaredLibrarySpecification {

  private final Origin origin;
  private final MachineTopLevelFlags topLevelFlags;
  private final Map<ApiLevelRange, MachineRewritingFlags> commonFlags;
  private final Map<ApiLevelRange, MachineRewritingFlags> libraryFlags;
  private final Map<ApiLevelRange, MachineRewritingFlags> programFlags;

  public MultiAPILevelMachineDesugaredLibrarySpecification(
      Origin origin,
      MachineTopLevelFlags topLevelFlags,
      Map<ApiLevelRange, MachineRewritingFlags> commonFlags,
      Map<ApiLevelRange, MachineRewritingFlags> libraryFlags,
      Map<ApiLevelRange, MachineRewritingFlags> programFlags) {
    this.origin = origin;
    this.topLevelFlags = topLevelFlags;
    this.commonFlags = commonFlags;
    this.libraryFlags = libraryFlags;
    this.programFlags = programFlags;
  }

  public Origin getOrigin() {
    return origin;
  }

  public MachineTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public Map<ApiLevelRange, MachineRewritingFlags> getCommonFlags() {
    return commonFlags;
  }

  public Map<ApiLevelRange, MachineRewritingFlags> getLibraryFlags() {
    return libraryFlags;
  }

  public Map<ApiLevelRange, MachineRewritingFlags> getProgramFlags() {
    return programFlags;
  }
}
