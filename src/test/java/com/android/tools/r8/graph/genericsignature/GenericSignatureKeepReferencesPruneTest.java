// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestRunResult;
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

  private final TestParameters parameters;
  private final boolean isCompat;
  private final boolean minify;

  @Parameters(name = "{0}, isCompat: {1}, minify: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public GenericSignatureKeepReferencesPruneTest(
      TestParameters parameters, boolean isCompat, boolean minify) {
    this.parameters = parameters;
    this.isCompat = isCompat;
    this.minify = minify;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(isCompat);
    assumeTrue(!minify);
    testForJvm(parameters)
        .addProgramClasses(I.class, Foo.class, J.class, K.class, L.class)
        .addProgramClassesAndInnerClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpected(Foo.class.getTypeName(), I.class.getTypeName()));
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
            .addProgramClasses(I.class, Foo.class, J.class, K.class, L.class)
            .addProgramClassesAndInnerClasses(Main.class)
            .addKeepClassAndMembersRules(Main.class)
            .setMinApi(parameters)
            .addKeepAttributeSignature()
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .compile()
            .inspect(this::inspectSignatures)
            .run(parameters.getRuntime(), Main.class);
    runResult.assertSuccess();
    CodeInspector inspector = runResult.inspector();
    // Foo and I exists due to the assertions in the inspection.
    runResult.assertSuccessWithOutputLines(
        getExpected(
            inspector.clazz(Foo.class).getFinalName(), inspector.clazz(I.class).getFinalName()));
  }

  private String[] getExpected(String fooName, String iName) {
    if (isCompat) {
      return new String[] {
        fooName + "<java.lang.String>",
        iName + "<java.lang.Integer, " + fooName + "<java.lang.Integer>>",
        iName + "<java.lang.String, " + fooName + "<java.lang.String>>",
        "Hello world"
      };
    } else {
      return new String[] {
        "class " + fooName, "interface " + iName, "interface " + iName, "Hello world"
      };
    }
  }

  private void inspectSignatures(CodeInspector inspector) {
    ClassSubject fooClass = inspector.clazz(Foo.class);
    assertThat(fooClass, isPresent());
    assertEquals(
        isCompat ? "<T::Ljava/lang/Comparable<TT;>;>Ljava/lang/Object;" : null,
        fooClass.getFinalSignatureAttribute());
    ClassSubject iClass = inspector.clazz(I.class);
    assertThat(iClass, isPresent());
    assertEquals(
        isCompat
            ? "<T::Ljava/lang/Comparable<TT;>;R:L"
                + fooClass.getFinalBinaryName()
                + "<TT;>;>Ljava/lang/Object;"
            : null,
        iClass.getFinalSignatureAttribute());
    ClassSubject fooInnerClass = inspector.clazz(Main.class.getTypeName() + "$1");
    assertThat(fooInnerClass, isPresent());
    assertEquals(
        isCompat
            ? "Ljava/lang/Object;L"
                + iClass.getFinalBinaryName()
                + "<Ljava/lang/String;L"
                + fooClass.getFinalBinaryName()
                + "<Ljava/lang/String;>;>;"
            : null,
        fooInnerClass.getFinalSignatureAttribute());
  }
}
