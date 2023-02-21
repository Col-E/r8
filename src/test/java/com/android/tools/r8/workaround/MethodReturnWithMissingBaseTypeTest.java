// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodReturnWithMissingBaseTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Utils.class)
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " { void notUsedDuringLaunch(); }")
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(UsedDuringLaunch.class, NotUsedDuringLaunch.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      new UsedDuringLaunch().usedDuringLaunch();
    }

    // @Keep
    static void notUsedDuringLaunch() {
      Consumer<?> emptyConsumer = Utils.getEmptyConsumer();
      new UsedDuringLaunch().onlyUsedOnHighApiLevels(emptyConsumer);
      new NotUsedDuringLaunch().useOfConsumerArray();
    }
  }

  @NeverClassInline
  static class UsedDuringLaunch {

    @NeverInline
    void usedDuringLaunch() {
      System.out.println("Hello world!");
    }

    @NeverInline
    void onlyUsedOnHighApiLevels(Consumer<?> c) {
      System.out.println(c);
    }
  }

  @NeverClassInline
  static class NotUsedDuringLaunch {

    @NeverInline
    void useOfConsumerArray() {
      Utils.accept(Utils.getEmptyConsumers());
    }
  }

  // @Keep
  static class Utils {

    // @Keep
    static void accept(Consumer<?>[] array) {
      System.out.println(array.length);
    }

    // @Keep
    public static Consumer<?> getEmptyConsumer() {
      return ignore -> {};
    }

    // @Keep
    public static Consumer<?>[] getEmptyConsumers() {
      return new Consumer<?>[] {getEmptyConsumer()};
    }
  }
}
