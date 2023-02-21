// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.lambda.KStyleKotlinLambdaMergingWithEnumUnboxingTest.Main.EnumUnboxingCandidate;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KStyleKotlinLambdaMergingWithEnumUnboxingTest extends TestBase {

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinTestParameters;

  @Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilers().withNoTargetVersion().build());
  }

  public KStyleKotlinLambdaMergingWithEnumUnboxingTest(
      TestParameters parameters, KotlinTestParameters kotlinTestParameters) {
    this.parameters = parameters;
    this.kotlinTestParameters = kotlinTestParameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramFiles(
            kotlinTestParameters.getCompiler().getKotlinStdlibJar(),
            kotlinTestParameters.getCompiler().getKotlinAnnotationJar())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(Lambda2.class, Lambda1.class))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(EnumUnboxingCandidate.class))
        .allowDiagnosticWarningMessages()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Lambda1.method()", "Lambda2.method()");
  }

  static class Main {

    @NeverClassInline
    public enum EnumUnboxingCandidate {
      LAMBDA1,
      LAMBDA2
    }

    public static void main(String[] args) {
      accept(createLambda(EnumUnboxingCandidate.LAMBDA1));
      accept(createLambda(EnumUnboxingCandidate.LAMBDA2));
    }

    @NeverInline
    static kotlin.jvm.functions.Function0<kotlin.Unit> createLambda(EnumUnboxingCandidate value) {
      switch (value) {
        case LAMBDA1:
          return new Lambda1();
        case LAMBDA2:
          return new Lambda2();
        default:
          throw new RuntimeException();
      }
    }

    @NeverInline
    static void accept(kotlin.jvm.functions.Function0<kotlin.Unit> instance) {
      instance.invoke();
    }
  }

  @NeverClassInline
  public static final class Lambda1 extends kotlin.jvm.internal.Lambda<kotlin.Unit>
      implements kotlin.jvm.functions.Function0<kotlin.Unit> {

    public Lambda1() {
      super(0);
    }

    @NeverInline
    @Override
    public final kotlin.Unit invoke() {
      System.out.println("Lambda1.method()");
      return null;
    }
  }

  @NeverClassInline
  public static final class Lambda2 extends kotlin.jvm.internal.Lambda<kotlin.Unit>
      implements kotlin.jvm.functions.Function0<kotlin.Unit> {

    public Lambda2() {
      super(0);
    }

    @NeverInline
    @Override
    public final kotlin.Unit invoke() {
      System.out.println("Lambda2.method()");
      return null;
    }
  }
}
