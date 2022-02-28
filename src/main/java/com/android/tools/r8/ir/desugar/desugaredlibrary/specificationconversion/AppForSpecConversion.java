// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public class AppForSpecConversion {
  static DexApplication readApp(
      AndroidApp inputApp, InternalOptions options, boolean libraryCompilation, Timing timing)
      throws IOException {
    timing.begin("Read App");
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassFileResourceProvider classFileResourceProvider :
        inputApp.getLibraryResourceProviders()) {
      builder.addLibraryResourceProvider(classFileResourceProvider);
    }
    if (libraryCompilation) {
      for (ProgramResourceProvider programResourceProvider :
          inputApp.getProgramResourceProviders()) {
        builder.addProgramResourceProvider(programResourceProvider);
      }
    }
    DexApplication app = internalReadApp(builder.build(), options, timing);
    timing.end();
    return app;
  }

  static DexApplication readAppForTesting(
      Path desugaredJDKLib,
      Path androidLib,
      InternalOptions options,
      boolean libraryCompilation,
      Timing timing)
      throws IOException {
    timing.begin("Read App for testing");
    assert !libraryCompilation || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    if (libraryCompilation) {
      builder.addProgramFile(desugaredJDKLib);
    }
    AndroidApp inputApp = builder.addLibraryFile(androidLib).build();
    DexApplication app = internalReadApp(inputApp, options, timing);
    timing.end();
    return app;
  }

  private static DexApplication internalReadApp(
      AndroidApp inputApp, InternalOptions options, Timing timing) throws IOException {
    timing.begin("Internal Read App");
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    timing.end();
    return app;
  }
}
