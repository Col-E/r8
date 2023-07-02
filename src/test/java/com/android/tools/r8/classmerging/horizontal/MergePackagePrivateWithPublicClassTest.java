// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.PackagePrivateClassRunner;
import org.junit.Test;

public class MergePackagePrivateWithPublicClassTest extends HorizontalClassMergingTestBase {

  public MergePackagePrivateWithPublicClassTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramClasses(
            PackagePrivateClassRunner.class, PackagePrivateClassRunner.getPrivateClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("package private")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(PackagePrivateClassRunner.class), isPresent());
              assertThat(
                  codeInspector.clazz(PackagePrivateClassRunner.getPrivateClass()), isAbsent());
            });
  }

  public static class Main {
    public static void main(String[] args) {
      new PackagePrivateClassRunner().run();
    }
  }
}
