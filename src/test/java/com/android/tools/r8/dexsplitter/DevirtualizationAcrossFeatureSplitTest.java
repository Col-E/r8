// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DevirtualizationAcrossFeatureSplitTest extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DevirtualizationAcrossFeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(BaseClass.class, BaseInterface.class)
        // Link against android.jar that contains ReflectiveOperationException.
        .addLibraryFiles(parameters.getDefaultAndroidJarAbove(AndroidApiLevel.K))
        .addFeatureSplitRuntime()
        .addFeatureSplit(FeatureMain.class, BaseInterfaceImpl.class)
        .addKeepFeatureMainRules(FeatureMain.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .runFeature(parameters.getRuntime(), FeatureMain.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  // Base.

  public static class BaseClass {

    @NeverInline
    public static void run(BaseInterface instance) {
      instance.greet();
    }
  }

  public interface BaseInterface {

    void greet();
  }

  // Feature.

  public static class FeatureMain implements RunInterface {

    @Override
    public void run() {
      BaseClass.run(new BaseInterfaceImpl());
    }
  }

  public static class BaseInterfaceImpl implements BaseInterface {

    @Override
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
