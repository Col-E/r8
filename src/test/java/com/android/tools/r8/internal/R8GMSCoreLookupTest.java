// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;

public class R8GMSCoreLookupTest extends TestBase {

  static final String APP_DIR = "third_party/gmscore/v5/";
  private AndroidApp app;
  private DirectMappedDexApplication program;
  private AppInfoWithSubtyping appInfo;
  private AppView<? extends AppInfoWithClassHierarchy> appView;

  @Before
  public void readGMSCore() throws Exception {
    Path directory = Paths.get(APP_DIR);
    app = ToolHelper.builderFromProgramDirectory(directory).build();
    Path mapFile = directory.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE);
    StringResource proguardMap = null;
    if (Files.exists(mapFile)) {
      proguardMap = StringResource.fromFile(mapFile);
    }
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Timing timing = Timing.empty();
    program =
        new ApplicationReader(app, new InternalOptions(), timing)
            .read(proguardMap, executorService)
            .toDirect();
    appInfo = new AppInfoWithSubtyping(program);
    appView = computeAppViewWithSubtyping(app);
  }

  private void testVirtualLookup(DexProgramClass clazz, DexEncodedMethod method) {
    // Check lookup will produce the same result.
    DexMethod id = method.method;
    assertEquals(appInfo.resolveMethod(id.holder, method.method).getSingleTarget(), method);

    // Check lookup targets with include method.
    ResolutionResult resolutionResult = appInfo.resolveMethodOnClass(clazz, method.method);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(clazz, appView, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<DexEncodedMethod> targets = lookupResult.asLookupResultSuccess().getMethodTargets();
    assertTrue(targets.contains(method));
  }

  private void testInterfaceLookup(DexProgramClass clazz, DexEncodedMethod method) {
    LookupResult lookupResult =
        appInfo
            .resolveMethodOnInterface(clazz, method.method)
            .lookupVirtualDispatchTargets(clazz, appView, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<DexEncodedMethod> targets = lookupResult.asLookupResultSuccess().getMethodTargets();
    if (appInfo.subtypes(method.method.holder).stream()
        .allMatch(t -> appInfo.definitionFor(t).isInterface())) {
      assertEquals(
          0,
          targets.stream()
              .filter(m -> m.accessFlags.isAbstract() || !m.accessFlags.isBridge())
              .count());
    } else {
      assertEquals(0, targets.stream().filter(m -> m.accessFlags.isAbstract()).count());
    }
  }

  private void testLookup(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      for (DexEncodedMethod method : clazz.virtualMethods()) {
        testInterfaceLookup(clazz, method);
      }
    } else {
      for (DexEncodedMethod method : clazz.virtualMethods()) {
        testVirtualLookup(clazz, method);
      }
    }
  }

  @Test
  public void testLookup() {
    program.classes().forEach(this::testLookup);
  }
}
