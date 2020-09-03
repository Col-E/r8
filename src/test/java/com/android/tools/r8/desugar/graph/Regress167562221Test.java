// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress167562221Test extends TestBase {

  // Classpath classes:

  public interface I {}

  public interface J extends I {}

  // Does not contribute to C so won't be in any edges.
  public interface K extends I {}

  public static class A implements J {}

  // Does not contribute to C so won't be in any edges.
  public static class B implements K {}

  // Program class:

  public static class C extends A {}

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public Regress167562221Test(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void repro() throws Throwable {
    DesugarGraphUtils utils = new DesugarGraphUtils();
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    testForD8()
        .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
        .apply(b -> utils.addClasspathClass(b, I.class, J.class, K.class, A.class, B.class))
        .apply(b -> utils.addProgramClasses(b, C.class))
        .setMinApi(AndroidApiLevel.B)
        .compile();

    assertEquals(
        ImmutableSet.of(utils.origin(A.class)),
        consumer.getDirectDependencies(utils.origin(C.class)));

    assertEquals(
        ImmutableSet.of(utils.origin(A.class), utils.origin(J.class), utils.origin(I.class)),
        consumer.getTransitiveDependencies(utils.origin(C.class)));

    // Check that the unrelated CP types are not in the graph.

    assertEquals(ImmutableSet.of(), consumer.getTransitiveDependencies(utils.origin(K.class)));
    assertEquals(ImmutableSet.of(), consumer.getTransitiveDependencies(utils.origin(B.class)));

    assertEquals(ImmutableSet.of(), consumer.getTransitiveDependents(utils.origin(K.class)));
    assertEquals(ImmutableSet.of(), consumer.getTransitiveDependents(utils.origin(B.class)));
  }
}
