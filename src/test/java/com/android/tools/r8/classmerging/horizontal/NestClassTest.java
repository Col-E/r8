// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.classmerging.horizontal.NestClassTest.R.horizontalclassmerging.BasicNestHostHorizontalClassMerging;
import com.android.tools.r8.classmerging.horizontal.NestClassTest.R.horizontalclassmerging.BasicNestHostHorizontalClassMerging2;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesClass;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesJava11RootPackage;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesPackage;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NestClassTest extends HorizontalClassMergingTestBase {
  public static class R extends ExamplesJava11RootPackage {
    public static class horizontalclassmerging extends ExamplesPackage {
      public static class BasicNestHostHorizontalClassMerging extends ExamplesClass {
        public static class A extends ExamplesClass {}

        public static class B extends ExamplesClass {}
      }

      public static class BasicNestHostHorizontalClassMerging2 extends ExamplesClass {
        public static class A extends ExamplesClass {}

        public static class B extends ExamplesClass {}
      }
    }
  }

  public NestClassTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Parameterized.Parameters(name = "{0}, horizontalClassMerging:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build(),
        BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(examplesTypeName(BasicNestHostHorizontalClassMerging.class))
        .addExamplesProgramFiles(R.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), examplesTypeName(BasicNestHostHorizontalClassMerging.class))
        .assertSuccessWithOutputLines("1: a", "1: b", "2: a", "2: b")
        .inspect(
            codeInspector -> {
              ClassSubject class1A =
                  codeInspector.clazz(
                      examplesTypeName(BasicNestHostHorizontalClassMerging.A.class));
              ClassSubject class2A =
                  codeInspector.clazz(
                      examplesTypeName(BasicNestHostHorizontalClassMerging2.A.class));
              ClassSubject class1 =
                  codeInspector.clazz(examplesTypeName(BasicNestHostHorizontalClassMerging.class));
              ClassSubject class2 =
                  codeInspector.clazz(examplesTypeName(BasicNestHostHorizontalClassMerging2.class));
              assertThat(class1, isPresent());
              assertThat(class2, isPresent());
              assertThat(class1A, isPresent());
              assertThat(class2A, isPresent());

              MethodSubject printClass1MethodSubject =
                  class1.method("void", "print", String.class.getTypeName());
              assertThat(printClass1MethodSubject, isPresent());
              assertThat(printClass1MethodSubject, isPrivate());

              MethodSubject printClass2MethodSubject =
                  class2.method("void", "print", String.class.getTypeName());
              assertThat(printClass2MethodSubject, isPresent());
              assertThat(printClass2MethodSubject, isPrivate());
              assertThat(printClass2MethodSubject, isStatic());

              assertThat(
                  codeInspector.clazz(
                      examplesTypeName(BasicNestHostHorizontalClassMerging.B.class)),
                  notIf(isPresent(), enableHorizontalClassMerging));
              assertThat(
                  codeInspector.clazz(
                      examplesTypeName(BasicNestHostHorizontalClassMerging2.B.class)),
                  notIf(isPresent(), enableHorizontalClassMerging));

              // TODO(b/165517236): Explicitly check 1.B is merged into 1.A, and 2.B into 2.A.
            });
  }
}
