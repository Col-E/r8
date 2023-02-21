// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageWithSyntheticItemTest extends RepackageTestBase {

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public RepackageWithSyntheticItemTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(RepackageWithSyntheticItemTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RepackageWithSyntheticItemTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(I.class)
        .setMinApi(parameters)
        .apply(this::configureRepackaging)
        .noClassInlining()
        .addInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0")
        .inspect(
            inspector -> {
              // Find the lambda class that starts with foo.
              List<FoundClassSubject> classesStartingWithfoo =
                  inspector.allClasses().stream()
                      .filter(item -> item.getFinalName().startsWith("foo"))
                      .collect(Collectors.toList());
              assertEquals(1, classesStartingWithfoo.size());
              String expectedOriginalNamePrefix = typeName(A.class) + "$$ExternalSyntheticLambda0";
              assertThat(
                  classesStartingWithfoo.get(0).getOriginalName(),
                  containsString(expectedOriginalNamePrefix));
            });
  }

  public static class A {

    @NeverInline
    public static void testLambda() {
      Main.test(System.out::println);
    }
  }

  public interface I {
    void foo(int x);
  }

  public static class Main {

    private static int argCount = 0;

    @NeverInline
    public static void test(I i) {
      i.foo(argCount);
    }

    public static void main(String[] args) {
      argCount = args.length;
      A.testLambda();
    }
  }
}
