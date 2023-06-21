// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
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
public class ApiModelNoInliningOfHigherApiLevelInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public ApiModelNoInliningOfHigherApiLevelInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("callInterfaceMethod", Object.class);
    Method apiCallerCaller = A.class.getDeclaredMethod("noApiCall", Object.class);
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::noApiCall", "ApiCaller::callInterfaceMethod")
        .inspect(
            inspector ->
                verifyThat(inspector, parameters, apiCaller)
                    .inlinedIntoFromApiLevel(apiCallerCaller, AndroidApiLevel.L_MR1));
  }

  public interface Api {

    void apiLevel22();
  }

  @NoHorizontalClassMerging
  public static class ApiCaller {

    @KeepConstantArguments
    public static void callInterfaceMethod(Object o) {
      System.out.println("ApiCaller::callInterfaceMethod");
      if (o != null) {
        ((Api) o).apiLevel22();
      }
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void noApiCall(Object o) {
      System.out.println("A::noApiCall");
      ApiCaller.callInterfaceMethod(o);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      @NoAccessModification
      class ApiImpl implements Api {
        @Override
        public void apiLevel22() {
          throw new RuntimeException("Foo");
        }
      }
      A.noApiCall(args.length > 0 ? new ApiImpl() : null);
    }
  }
}
