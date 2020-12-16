// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithNestAndKeepOnPrivateTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build());
  }

  public RepackageWithNestAndKeepOnPrivateTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testJvm() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getProgramClassData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassData())
        .apply(this::configureRepackaging)
        .addKeepClassAndMembersRules(A.class)
        .enableInliningAnnotations()
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresentAndNotRenamed());
              assertThat(B.class, isRepackaged(inspector));
            })
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/175196884): Repackaging is not allowed to move class.
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private List<byte[]> getProgramClassData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class).removeInnerClasses().transform(),
        transformer(A.class)
            .setPrivate(A.class.getDeclaredMethod("aPrivate"))
            .removeInnerClasses()
            .setNest(A.class, B.class)
            .transform(),
        transformer(B.class).removeInnerClasses().setNest(A.class, B.class).transform());
  }

  public static class A {

    @NeverInline
    /* private */ static void aPrivate() {
      System.out.println("Hello World!");
    }
  }

  public static class B {

    @NeverInline
    public static void foo() {
      A.aPrivate();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B.foo();
    }
  }
}
