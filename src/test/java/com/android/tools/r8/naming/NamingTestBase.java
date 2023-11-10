// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class NamingTestBase extends TestBase {

  private final String appFileName;
  protected final List<String> keepRulesFiles;
  protected final Consumer<NamingLens> inspection;

  private DexItemFactory dexItemFactory = null;

  protected NamingTestBase(
      String test, List<String> keepRulesFiles, BiConsumer<DexItemFactory, NamingLens> inspection) {
    appFileName = ToolHelper.EXAMPLES_BUILD_DIR + test + ".jar";
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = lens -> inspection.accept(dexItemFactory, lens);
  }

  protected NamingLens runMinifier(List<Path> configPaths) throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            AndroidApp.builder()
                .addProgramFile(Paths.get(appFileName))
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory -> ToolHelper.loadProguardConfiguration(factory, configPaths));
    dexItemFactory = appView.dexItemFactory();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      new Minifier(appView).run(executor, Timing.empty());
    } finally {
      executor.shutdown();
    }
    return appView.getNamingLens();
  }

  protected static <T> Collection<Object[]> createTests(
      List<String> tests, Map<String, T> inspections) {
    List<Object[]> testCases = new ArrayList<>();
    Set<String> usedInspections = new HashSet<>();
    for (String test : tests) {
      File[] keepFiles = new File(ToolHelper.EXAMPLES_DIR + test)
          .listFiles(file -> file.isFile() && file.getName().endsWith(".txt"));
      for (File keepFile : keepFiles) {
        String keepName = keepFile.getName();
        T inspection = getTestOptionalParameter(inspections, usedInspections, test, keepName);
        if (inspection != null) {
          testCases.add(new Object[]{test, ImmutableList.of(keepFile.getPath()), inspection});
        }
      }
    }
    assert usedInspections.size() == inspections.size();
    return testCases;
  }

  private static <T> T getTestOptionalParameter(
      Map<String, T> specifications,
      Set<String> usedSpecifications,
      String test,
      String keepName) {
    T parameter = specifications.get(test);
    if (parameter == null) {
      parameter = specifications.get(test + ":" + keepName);
      if (parameter != null) {
        usedSpecifications.add(test + ":" + keepName);
      }
    } else {
      usedSpecifications.add(test);
    }
    return parameter;
  }
}
