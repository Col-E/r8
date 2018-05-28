// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.DexIndexedConsumer.DirectoryConsumer;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FeatureClassMapping;
import com.android.tools.r8.utils.FeatureClassMapping.FeatureMappingException;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DexSplitterHelper {

  public static void run(
      D8Command command, FeatureClassMapping featureClassMapping, String output, String proguardMap)
      throws CompilationFailedException {
    ExecutorService executor = ThreadUtils.getExecutorService(ThreadUtils.NOT_SPECIFIED);
    try {
      ExceptionUtils.withCompilationHandler(
          command.getReporter(),
          () -> run(command, featureClassMapping, output, proguardMap, executor));
    } finally {
      executor.shutdown();
    }
  }

  public static void run(
      D8Command command,
      FeatureClassMapping featureClassMapping,
      String output,
      String proguardMap,
      ExecutorService executor)
      throws IOException {
    InternalOptions options = command.getInternalOptions();
    options.enableDesugaring = false;
    options.enableMainDexListCheck = false;
    options.minimalMainDex = false;
    options.enableMinification = false;
    options.enableInlining = false;
    options.outline.enabled = false;

    try {
      Timing timing = new Timing("DexSplitter");
      DexApplication app =
          new ApplicationReader(command.getInputApp(), options, timing).read(null, executor);

      List<Marker> markers = app.dexItemFactory.extractMarkers();

      ClassNameMapper mapper = null;
      if (proguardMap != null) {
        mapper = ClassNameMapper.mapperFromFile(Paths.get(proguardMap));
      }
      Map<String, Builder> applications = getDistribution(app, featureClassMapping, mapper);
      for (Entry<String, Builder> entry : applications.entrySet()) {
        DexApplication featureApp = entry.getValue().build();
        // We use the same factory, reset sorting.
        featureApp.dexItemFactory.resetSortedIndices();
        assert !options.hasMethodsFilter();

        // Run d8 optimize to ensure jumbo strings are handled.
        AppInfo appInfo = new AppInfo(featureApp);
        featureApp = D8.optimize(featureApp, appInfo, options, timing, executor);
        // We create a specific consumer for each split.
        Path outputDir = Paths.get(output).resolve(entry.getKey());
        if (!Files.exists(outputDir)) {
          Files.createDirectory(outputDir);
        }
        DexIndexedConsumer consumer = new DirectoryConsumer(outputDir);

        try {
          new ApplicationWriter(
                  featureApp,
                  options,
                  markers,
                  null,
                  NamingLens.getIdentityLens(),
                  null,
                  null,
                  consumer)
              .write(executor);
          options.printWarnings();
        } finally {
          consumer.finished(options.reporter);
        }
      }
    } catch (ExecutionException e) {
      R8.unwrapExecutionException(e);
      throw new AssertionError(e); // unwrapping method should have thrown
    } catch (FeatureMappingException e) {
      options.reporter.error(e.getMessage());
    } finally {
      options.signalFinishedToConsumers();
    }
  }

  private static Map<String, Builder> getDistribution(
      DexApplication app, FeatureClassMapping featureClassMapping, ClassNameMapper mapper)
      throws FeatureMappingException {
    Map<String, Builder> applications = new HashMap<>();
    for (DexProgramClass clazz : app.classes()) {
      String clazzName =
          mapper != null ? mapper.deobfuscateClassName(clazz.toString()) : clazz.toString();
      String feature = featureClassMapping.featureForClass(clazzName);
      Builder featureApplication = applications.get(feature);
      if (featureApplication == null) {
        featureApplication = DexApplication.builder(app.dexItemFactory, app.timing);
        applications.put(feature, featureApplication);
      }
      featureApplication.addProgramClass(clazz);
    }
    return applications;
  }

  public static void runD8ForTesting(D8Command command, boolean dontCreateMarkerInD8)
      throws IOException {
    InternalOptions options = command.getInternalOptions();
    options.testing.dontCreateMarkerInD8 = dontCreateMarkerInD8;
    D8.runForTesting(command.getInputApp(), options);
  }
}
