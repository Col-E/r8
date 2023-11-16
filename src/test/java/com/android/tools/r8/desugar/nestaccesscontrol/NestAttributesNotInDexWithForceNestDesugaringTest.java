// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.nestaccesscontrol.NestAttributesNotInDexWithForceNestDesugaringTest.Host.Member1;
import com.android.tools.r8.desugar.nestaccesscontrol.NestAttributesNotInDexWithForceNestDesugaringTest.Host.Member2;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAttributesNotInDexWithForceNestDesugaringTest extends NestAttributesInDexTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean forceNestDesugaring;

  @Parameters(name = "{0}, forceNestDesugaring: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addOptionsModification(options -> options.forceNestDesugaring = forceNestDesugaring)
        .compile()
        .inspect(
            inspector -> {
              assertThat(
                  inspector
                      .clazz(Member1.class)
                      .uniqueMethodWithOriginalName(
                          SyntheticItemsTestUtils.syntheticNestInstanceMethodAccessor(
                                  Member1.class.getDeclaredMethod("m1"))
                              .getMethodName()),
                  isPresentIf(forceNestDesugaring));
              assertThat(
                  inspector
                      .clazz(Member2.class)
                      .uniqueMethodWithOriginalName(
                          SyntheticItemsTestUtils.syntheticNestStaticMethodAccessor(
                                  Member2.class.getDeclaredMethod("m2"))
                              .getMethodName()),
                  isPresentIf(forceNestDesugaring));
            });
  }

  @Test
  public void testD8NoDesugar() {
    assumeTrue(forceNestDesugaring);
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .addProgramClassFileData(getTransformedClasses())
                .disableDesugaring()
                .setMinApi(parameters)
                .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
                .addOptionsModification(
                    options -> options.forceNestDesugaring = forceNestDesugaring)
                .compile());
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(Host.class)
            .setAccessFlags(MethodPredicate.onName("h"), MethodAccessFlags::setPrivate)
            .transform(),
        withNest(Member1.class)
            .setAccessFlags(MethodPredicate.onName("m1"), MethodAccessFlags::setPrivate)
            .transform(),
        withNest(Member2.class)
            .setAccessFlags(MethodPredicate.onName("m2"), MethodAccessFlags::setPrivate)
            .transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    return transformer(clazz).setNest(Host.class, Member1.class, Member2.class);
  }

  static class TestClass {

    public static void main(String[] args) {}
  }

  static class Host {
    static class Member1 {
      void m1() { // Will be private.
        Member2.m2();
      }
    }

    static class Member2 {
      static void m2() { // Will be private.
      }
    }

    void h() { // Will be private.
      new Member1().m1();
    }
  }
}
