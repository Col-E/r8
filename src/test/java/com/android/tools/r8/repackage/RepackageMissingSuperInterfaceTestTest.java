// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageMissingSuperInterfaceTestTest extends RepackageTestBase {

  private final String[] EXPECTED = new String[] {"ClassImplementingMissingInterface::bar"};

  public RepackageMissingSuperInterfaceTestTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8WithoutRepackaging() throws Exception {
    runTest(false);
  }

  @Test
  public void testR8() throws Exception {
    runTest(true);
  }

  private void runTest(boolean repackage) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ClassImplementingMissingInterface.class, Main.class)
        .addKeepMainRule(Main.class)
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters)
        .addDontWarn(MissingInterface.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              if (repackage) {
                assertThat(ClassImplementingMissingInterface.class, isRepackaged(inspector));
              } else {
                // The class is minified.
                ClassSubject clazz = inspector.clazz(ClassImplementingMissingInterface.class);
                assertThat(clazz, isPresentAndRenamed());
              }
            })
        .addRunClasspathClasses(MissingInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface MissingInterface {

    void bar();
  }

  @NeverClassInline
  public static class ClassImplementingMissingInterface implements MissingInterface {

    @Override
    @NeverInline
    public void bar() {
      System.out.println("ClassImplementingMissingInterface::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ClassImplementingMissingInterface().bar();
    }
  }
}
