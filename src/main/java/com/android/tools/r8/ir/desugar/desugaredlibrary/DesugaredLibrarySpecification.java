// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface DesugaredLibrarySpecification {

  default boolean isHuman() {
    return false;
  }

  default boolean isLegacy() {
    return false;
  }

  boolean isEmpty();

  boolean isLibraryCompilation();

  String getJsonSource();

  String getSynthesizedLibraryClassesPackagePrefix();

  List<String> getExtraKeepRules();

  AndroidApiLevel getRequiredCompilationApiLevel();

  MachineDesugaredLibrarySpecification toMachineSpecification(
      InternalOptions options, AndroidApp app, Timing timing) throws IOException;

  MachineDesugaredLibrarySpecification toMachineSpecification(
      InternalOptions options, Path library, Timing timing, Path desugaredJDKLib)
      throws IOException;

  default MachineDesugaredLibrarySpecification toMachineSpecification(
      InternalOptions options, Path library, Timing timing) throws IOException {
    assert !isLibraryCompilation();
    return toMachineSpecification(options, library, timing, null);
  }
}
