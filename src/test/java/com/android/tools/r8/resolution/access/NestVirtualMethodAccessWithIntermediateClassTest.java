// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.optimize.NestUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestVirtualMethodAccessWithIntermediateClassTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::bar");

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

  public NestVirtualMethodAccessWithIntermediateClassTest(
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
            buildClasses(getClasses()).addClassProgramData(getTransformedClasses()).build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexProgramClass bClass =
        appInfo.definitionFor(buildType(B.class, appInfo.dexItemFactory())).asProgramClass();
    DexMethod bar = buildMethod(A.class.getDeclaredMethod("bar"), appInfo.dexItemFactory());
    ResolutionResult resolutionResult = appInfo.resolveMethod(bar.holder, bar);
    // TODO(b/145187573): Update to check the full access control once possible.
    assertEquals(
        inSameNest,
        NestUtils.sameNest(bClass.type, resolutionResult.getSingleTarget().method.holder, appView));
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  @Test
  public void testR8() throws Exception {
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(getClasses())
            .addProgramClassFileData(getTransformedClasses())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class);
    // TODO(b/145196085): R8 fails compilation when the classes are in the same nest.
    if (inSameNest) {
      if (parameters.isDexRuntime()) {
        try {
          builder.compile();
        } catch (CompilationFailedException e) {
          // Expected failure.
          return;
        }
        fail("Expected failure: b/145196085");
      }
    }
    builder
        .run(parameters.getRuntime(), Main.class)
        .apply(
            result -> {
              if (inSameNest) {
                // TODO(b/145187969): R8 compiles out the errors when in the same nest.
                result.assertSuccessWithOutput(EXPECTED);
              } else {
                checkExpectedResult(result);
              }
            });
  }

  private void checkExpectedResult(TestRunResult<?> result) {
    if (inSameNest && parameters.isCfRuntime()) {
      // TODO(b/145187969): Investigate if the change to NoSuchMethodError is according to spec?
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
    /* will be private */ void bar() {
      System.out.println("A::bar");
    }
  }

  static class B extends A {}

  static class C {
    public void foo() {
      // Virtual invoke to private method.
      new B().bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new C().foo();
    }
  }
}
