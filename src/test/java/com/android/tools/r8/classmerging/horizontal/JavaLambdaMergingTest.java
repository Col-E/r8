// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class JavaLambdaMergingTest extends HorizontalClassMergingTestBase {

  public JavaLambdaMergingTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.horizontalClassMergerOptions().enableIf(enableHorizontalClassMerging);
              assertFalse(options.horizontalClassMergerOptions().isJavaLambdaMergingEnabled());
              options.horizontalClassMergerOptions().enableJavaLambdaMerging();
            })
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging && parameters.isDexRuntime(),
            inspector -> {
              Set<DexType> lambdaSources =
                  inspector.getSources().stream()
                      .filter(x -> x.toSourceString().contains(LAMBDA_CLASS_NAME_PREFIX))
                      .collect(Collectors.toSet());
              assertEquals(3, lambdaSources.size());
              DexType firstTarget = inspector.getTarget(lambdaSources.iterator().next());
              for (DexType lambdaSource : lambdaSources) {
                assertTrue(
                    inspector
                        .getTarget(lambdaSource)
                        .toSourceString()
                        .contains(LAMBDA_CLASS_NAME_PREFIX));
                assertEquals(firstTarget, inspector.getTarget(lambdaSource));
              }
            })
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  public static class Main {

    public static void main(String[] args) {
      HelloGreeter helloGreeter =
          System.currentTimeMillis() > 0
              ? () -> System.out.print("Hello")
              : () -> {
                throw new RuntimeException();
              };
      WorldGreeter worldGreeter =
          System.currentTimeMillis() > 0
              ? () -> System.out.println(" world!")
              : () -> {
                throw new RuntimeException();
              };
      helloGreeter.hello();
      worldGreeter.world();
    }
  }

  interface HelloGreeter {

    void hello();
  }

  interface WorldGreeter {

    void world();
  }
}
