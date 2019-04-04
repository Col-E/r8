// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import org.junit.Before;

public abstract class NamingTestBase {

  private final String appFileName;
  protected final List<String> keepRulesFiles;
  protected final BiConsumer<DexItemFactory, NamingLens> inspection;

  private final Timing timing;

  private DexApplication program;
  protected DexItemFactory dexItemFactory;

  protected NamingTestBase(
      String test,
      List<String> keepRulesFiles,
      BiConsumer<DexItemFactory, NamingLens> inspection,
      Timing timing) {
    appFileName = ToolHelper.EXAMPLES_BUILD_DIR + test + "/classes.dex";
    this.keepRulesFiles = keepRulesFiles;
    this.inspection = inspection;
    this.timing = timing;
  }

  @Before
  public void readApp() throws IOException, ExecutionException {
    program = ToolHelper.buildApplication(ImmutableList.of(appFileName));
    dexItemFactory = program.dexItemFactory;
  }

  protected NamingLens runMinifier(List<Path> configPaths) throws ExecutionException {
    ProguardConfiguration configuration =
        ToolHelper.loadProguardConfiguration(dexItemFactory, configPaths);
    InternalOptions options = new InternalOptions(configuration, new Reporter());
    options.programConsumer = DexIndexedConsumer.emptyConsumer();

    ExecutorService executor = ThreadUtils.getExecutorService(1);

    AppView<AppInfoWithSubtyping> appView =
        AppView.createForR8(new AppInfoWithSubtyping(program), options);
    appView.setRootSet(
        new RootSetBuilder(appView, program, configuration.getRules()).run(executor));
    appView.setAppServices(AppServices.builder(appView).build());

    Enqueuer enqueuer = new Enqueuer(appView, options, null);
    appView.setAppInfo(
        enqueuer.traceApplication(
            appView.rootSet(), configuration.getDontWarnPatterns(), executor, timing));
    return new Minifier(appView.withLiveness(), Collections.emptySet()).run(timing);
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
