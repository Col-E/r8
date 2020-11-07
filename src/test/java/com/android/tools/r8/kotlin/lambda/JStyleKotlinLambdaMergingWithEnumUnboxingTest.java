// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.optimize.lambda.kotlin.JStyleLambdaGroupIdFactory;
import com.android.tools.r8.ir.optimize.lambda.kotlin.KotlinLambdaGroupIdFactory;
import com.android.tools.r8.kotlin.lambda.JStyleKotlinLambdaMergingWithEnumUnboxingTest.Main.EnumUnboxingCandidate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JStyleKotlinLambdaMergingWithEnumUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public JStyleKotlinLambdaMergingWithEnumUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.kotlinLambdaMergerFactoryForClass =
                    this::getKotlinLambdaMergerFactoryForClass)
        .addHorizontallyMergedLambdaClassesInspector(
            inspector -> inspector.assertMerged(Lambda1.class, Lambda2.class))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(EnumUnboxingCandidate.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Lambda1.method()", "Lambda2.method()");
  }

  private KotlinLambdaGroupIdFactory getKotlinLambdaMergerFactoryForClass(DexProgramClass clazz) {
    String typeName = clazz.getType().toSourceString();
    if (typeName.equals(Lambda1.class.getTypeName())
        || typeName.equals(Lambda2.class.getTypeName())) {
      return JStyleLambdaGroupIdFactory.getInstance();
    }
    return null;
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
    static I createLambda(EnumUnboxingCandidate value) {
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
    static void accept(I instance) {
      instance.method();
    }
  }

  interface I {

    void method();
  }

  @NeverClassInline
  public static final class Lambda1 implements I {

    @NeverInline
    @Override
    public final void method() {
      System.out.println("Lambda1.method()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static final class Lambda2 implements I {

    @NeverInline
    @Override
    public final void method() {
      System.out.println("Lambda2.method()");
    }
  }
}
