// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.constructor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForwardingConstructorUsedFromPlatformShakingOnDexTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, MyFragment.class)
        .addLibraryClasses(Fragment.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .addBootClasspathClasses(Fragment.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Instantiating");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject myFragmentClassSubject = inspector.clazz(MyFragment.class);
    assertThat(myFragmentClassSubject, isPresent());
    assertThat(myFragmentClassSubject.init(), isPresent());
  }

  public abstract static class Fragment {

    public Fragment newInstance() throws Exception {
      System.out.println("Instantiating");
      return getClass().getDeclaredConstructor().newInstance();
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      new MyFragment().newInstance();
    }
  }

  @NeverClassInline
  public static class MyFragment extends Fragment {

    public MyFragment() {}
  }
}
