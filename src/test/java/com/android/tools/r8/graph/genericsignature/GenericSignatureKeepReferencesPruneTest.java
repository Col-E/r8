// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.genericsignature.testclasses.Foo;
import com.android.tools.r8.graph.genericsignature.testclasses.I;
import com.android.tools.r8.graph.genericsignature.testclasses.J;
import com.android.tools.r8.graph.genericsignature.testclasses.K;
import com.android.tools.r8.graph.genericsignature.testclasses.L;
import com.android.tools.r8.graph.genericsignature.testclasses.Main;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureKeepReferencesPruneTest extends TestBase {

  private final String[] EXPECTED =
      new String[] {
        Foo.class.getTypeName() + "<java.lang.String>",
        I.class.getTypeName()
            + "<java.lang.Integer, "
            + Foo.class.getTypeName()
            + "<java.lang.Integer>>",
        I.class.getTypeName()
            + "<java.lang.String, "
            + Foo.class.getTypeName()
            + "<java.lang.String>>",
        "Hello world"
      };

  private final TestParameters parameters;
  private final boolean isCompat;

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GenericSignatureKeepReferencesPruneTest(TestParameters parameters, boolean isCompat) {
    this.parameters = parameters;
    this.isCompat = isCompat;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(I.class, Foo.class, J.class, K.class, L.class)
        .addProgramClassesAndInnerClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addProgramClasses(I.class, Foo.class, J.class, K.class, L.class)
        .addProgramClassesAndInnerClasses(Main.class)
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .noMinification()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/184927364): Should have different output due to pruning the inner class.
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspectSignatures);
  }

  private void inspectSignatures(CodeInspector inspector) {
    ClassSubject fooClass = inspector.clazz(Foo.class);
    assertThat(fooClass, isPresent());
    // TODO(b/184927364): Fullmode should not keep the interface bound.
    assertEquals(
        "<T::Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;",
        fooClass.getFinalSignatureAttribute());
    ClassSubject iClass = inspector.clazz(I.class);
    assertThat(iClass, isPresent());
    // TODO(b/184927364): Fullmode should not keep the interface and class bound.
    assertEquals(
        "<T::Ljava/lang/Comparable<TT;>;R:L" + binaryName(Foo.class) + "<TT;>;>Ljava/lang/Object;",
        iClass.getFinalSignatureAttribute());
    ClassSubject fooInnerClass = inspector.clazz(Main.class.getTypeName() + "$1");
    assertThat(fooInnerClass, isPresent());
    // TODO(b/184927364): Fullmode should completely remove this signature
    assertEquals(
        "Ljava/lang/Object;L"
            + binaryName(I.class)
            + "<Ljava/lang/String;L"
            + binaryName(Foo.class)
            + "<Ljava/lang/String;>;>;",
        fooInnerClass.getFinalSignatureAttribute());
  }
}
