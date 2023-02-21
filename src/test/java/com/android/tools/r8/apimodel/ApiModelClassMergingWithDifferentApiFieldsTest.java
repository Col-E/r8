// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelClassMergingWithDifferentApiFieldsTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Api::foo", "B::bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelClassMergingWithDifferentApiFieldsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, ApiSetter.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesMerged(A.class, B.class))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableCheckAllApiReferencesAreNotUnknown)
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Api {

    public void foo() {
      System.out.println("Api::foo");
    }
  }

  @NoHorizontalClassMerging
  static class ApiSetter {
    static void set() {
      A.api = new Api();
    }
  }

  static class A {

    private static Api api;

    public static void callApi() throws Exception {
      // The reflective call here is to ensure that the setting of A's api level is not based on
      // a method reference to `Api` and only because of the type reference in the field `api`.
      Class<?> aClass =
          Class.forName(
              "com.android.tools.r8.apimodel.ApiModelClassMergingWithDifferentApiFieldsTest_Api"
                  .replace("_", "$"));
      Method foo = aClass.getDeclaredMethod("foo");
      foo.invoke(api);
    }
  }

  public static class B {

    @NeverInline
    public static void bar() {
      System.out.println("B::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      ApiSetter.set();
      A.callApi();
      B.bar();
    }
  }
}
