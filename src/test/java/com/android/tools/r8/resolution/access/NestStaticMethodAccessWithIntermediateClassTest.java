// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static com.android.tools.r8.TestRuntime.CfVm.JDK17;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestStaticMethodAccessWithIntermediateClassTest extends TestBase {

  private final TestParameters parameters;
  private final boolean inSameNest;

  @Parameterized.Parameters(name = "{0}, in-same-nest:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(JDK11)
            .withDexRuntimes()
            .withAllApiLevels()
            .build(),
        BooleanUtils.values());
  }

  public NestStaticMethodAccessWithIntermediateClassTest(
      TestParameters parameters, boolean inSameNest) {
    this.parameters = parameters;
    this.inSameNest = inSameNest;
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(A.class).setPrivate(A.class.getDeclaredMethod("bar")).transform(),
        withNest(B.class).transform(),
        withNest(C.class).transform());
  }

  @Test
  public void testResolutionAccess() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(getClasses())
                .addClassProgramData(getTransformedClasses())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexProgramClass bClass =
        appInfo.definitionFor(buildType(B.class, appInfo.dexItemFactory())).asProgramClass();
    DexMethod bar = buildMethod(A.class.getDeclaredMethod("bar"), appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(bar);
    assertEquals(OptionalBool.of(inSameNest), resolutionResult.isAccessibleFrom(bClass, appInfo));
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            // TODO(b/227160049): Incorrect nest-based access allowed on JDK17!?
            inSameNest
                && parameters.isCfRuntime()
                && parameters.asCfRuntime().isNewerThanOrEqual(JDK17),
            runResult -> runResult.assertSuccessWithOutputLines("A::bar"),
            runResult -> checkExpectedResult(runResult, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkExpectedResult(runResult, true));
  }

  private void checkExpectedResult(TestRunResult<?> result, boolean isR8) {
    if (inSameNest && (parameters.isCfRuntime() || isR8)) {
      result.assertFailureWithErrorThatMatches(containsString(NoSuchMethodError.class.getName()));
    } else {
      result.assertFailureWithErrorThatMatches(containsString(IllegalAccessError.class.getName()));
    }
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host with B and C as members.
      return transformer(clazz).setNest(A.class, B.class, C.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  static class A {
    /* will be private */ static void bar() {
      System.out.println("A::bar");
    }
  }

  static class B extends A {}

  static class C {
    public void foo() {
      // Static invoke to private method.
      B.bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new C().foo();
    }
  }
}
