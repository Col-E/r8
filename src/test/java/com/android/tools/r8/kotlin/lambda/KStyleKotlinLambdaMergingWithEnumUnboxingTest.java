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
import com.android.tools.r8.ir.optimize.lambda.kotlin.KStyleLambdaGroupIdFactory;
import com.android.tools.r8.ir.optimize.lambda.kotlin.KotlinLambdaGroupIdFactory;
import com.android.tools.r8.kotlin.lambda.KStyleKotlinLambdaMergingWithEnumUnboxingTest.Main.EnumUnboxingCandidate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KStyleKotlinLambdaMergingWithEnumUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public KStyleKotlinLambdaMergingWithEnumUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
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
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Lambda1.method()", "Lambda2.method()");
  }

  private KotlinLambdaGroupIdFactory getKotlinLambdaMergerFactoryForClass(DexProgramClass clazz) {
    String typeName = clazz.getType().toSourceString();
    if (typeName.equals(Lambda1.class.getTypeName())
        || typeName.equals(Lambda2.class.getTypeName())) {
      return KStyleLambdaGroupIdFactory.getInstance();
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
  @NoHorizontalClassMerging
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
