// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/213581039.
@RunWith(Parameterized.class)
public class MemberRebindingInvokeSuperAbstractTest extends TestBase {

  @Parameter() public TestParameters parameters;

  private final List<Class<?>> libraryClasses =
      ImmutableList.of(LibraryBase.class, LibrarySub.class, LibrarySubSub.class);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(libraryClasses)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            builder ->
                libraryClasses.forEach(clazz -> setMockApiLevelForClass(clazz, AndroidApiLevel.B)))
        .apply(
            setMockApiLevelForMethod(
                LibraryBase.class.getDeclaredMethod("getSystemService"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibrarySub.class.getDeclaredMethod("getSystemService"), AndroidApiLevel.B))
        .compile()
        .addRunClasspathClasses(libraryClasses)
        .inspect(
            inspector -> {
              MethodSubject getSystemService =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("getSystemService");
              assertThat(getSystemService, isPresent());
              // We should never rebind this call to LibraryBase::getSystemService since this can
              // cause errors when verifying the code on a device where the image has a definition
              // but it is abstract. For more information, see b/213581039.
              assertThat(
                  getSystemService,
                  CodeMatchers.invokesMethodWithHolderAndName(
                      typeName(LibrarySubSub.class), "getSystemService"));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("LibrarySub::getSystemService");
  }

  public abstract static class LibraryBase {

    public abstract void getSystemService();
  }

  public static class LibrarySub extends LibraryBase {

    @Override
    public void getSystemService() {
      System.out.println("LibrarySub::getSystemService");
    }
  }

  public static class LibrarySubSub extends LibrarySub {}

  public static class Main extends LibrarySubSub {

    public static void main(String[] args) {
      new Main().getSystemService();
    }

    @Override
    @NeverInline
    public void getSystemService() {
      super.getSystemService();
    }
  }
}
