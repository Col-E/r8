// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

public class AppForSpecConversion {

  public static DexApplication readAppForTesting(
      Collection<Path> desugaredJDKLib,
      Collection<Path> androidLib,
      InternalOptions options,
      boolean libraryCompilation,
      Timing timing)
      throws IOException {
    timing.begin("Read App for testing");
    assert !libraryCompilation || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    if (libraryCompilation) {
      builder.addProgramFiles(desugaredJDKLib);
    }
    AndroidApp inputApp = builder.addLibraryFiles(androidLib).build();
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
