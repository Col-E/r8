// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.Collection;

public abstract class AbstractGenerateFiles {

  // If we increment this api level, we need to verify everything works correctly.
  static final AndroidApiLevel MAX_TESTED_ANDROID_API_LEVEL = AndroidApiLevel.U;

  private final DexItemFactory factory = new DexItemFactory();
  private final Reporter reporter = new Reporter();
  final InternalOptions options =
      new InternalOptions(factory, reporter)
          .getArtProfileOptions()
          .setAllowReadingEmptyArtProfileProvidersMultipleTimesForTesting(true)
          .getOptions();

  final DesugaredLibrarySpecification desugaredLibrarySpecification;
  final StringResource desugaredLibrarySpecificationResource;
  final Collection<ProgramResourceProvider> desugaredLibraryImplementation;
  final Path output;
  final Collection<ClassFileResourceProvider> androidJar;

  AbstractGenerateFiles(
      StringResource desugaredLibrarySpecificationResource,
      Collection<ProgramResourceProvider> desugarImplementation,
      Path output,
      Collection<ClassFileResourceProvider> androidJar) {
    assert androidJar != null;
    this.desugaredLibrarySpecificationResource = desugaredLibrarySpecificationResource;
    this.androidJar = androidJar;
    this.desugaredLibrarySpecification = readDesugaredLibrarySpecification();
    this.desugaredLibraryImplementation = desugarImplementation;
    this.output = output;
  }

  private DesugaredLibrarySpecification readDesugaredLibrarySpecification() {
    if (desugaredLibrarySpecificationResource == null) {
      return MachineDesugaredLibrarySpecification.empty();
    }
    return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
        desugaredLibrarySpecificationResource,
        factory,
        reporter,
        false,
        AndroidApiLevel.B.getLevel());
  }

  abstract AndroidApiLevel run() throws Exception;
}
