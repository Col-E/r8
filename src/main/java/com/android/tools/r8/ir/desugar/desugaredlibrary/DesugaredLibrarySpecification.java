// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface DesugaredLibrarySpecification {

  default boolean isHuman() {
    return false;
  }

  default boolean isLegacy() {
    return false;
  }

  boolean isEmpty();

  boolean isLibraryCompilation();

  String getIdentifier();

  String getJsonSource();

  String getSynthesizedLibraryClassesPackagePrefix();

  List<String> getExtraKeepRules();

  Set<String> getMaintainTypeOrPrefixForTesting();

  AndroidApiLevel getRequiredCompilationApiLevel();

  MachineDesugaredLibrarySpecification toMachineSpecification(DexApplication app, Timing timing)
      throws IOException;
}
