// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticLambdaWithMissingInterfaceMergingTest extends TestBase {

  @Parameter(0)
  public boolean enableOptimization;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, optimize: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class)
        .addDontWarn(J.class)
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (enableOptimization) {
                inspector.assertNoClassesMerged();
              } else {
                // TODO(b/191747442): Should not merge lambdas.
                Set<Set<DexType>> groups = inspector.getMergeGroups();
                assertEquals(1, groups.size());

                Set<DexType> group = groups.iterator().next();
                assertEquals(2, group.size());
                assertTrue(
                    group.stream()
                        .allMatch(
                            member ->
                                SyntheticItemsTestUtils.isExternalLambda(
                                    member.asClassReference())));
              }
            })
        .applyIf(!enableOptimization, TestShrinkerBuilder::addDontOptimize)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/191747442): Should succeed.
        .applyIf(
            enableOptimization,
            result -> result.assertSuccessWithOutputLines("I"),
            result -> result.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  static class Main {
    public static void main(String[] args) {
      I i = () -> System.out.println("I");
      i.m1();
    }

    static void dead() {
      J j = () -> System.out.println("J");
      j.m2();
    }
  }

  interface I {
    void m1();
  }

  interface J {
    void m2();
  }
}
