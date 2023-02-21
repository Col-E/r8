// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingOnLibraryPathTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addApplyMapping(typeName(LibraryClass.class) + " -> a.a:")
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Main.class);
              assertThat(clazz, isPresentAndNotRenamed());
              FoundClassSubject foundClassSubject = clazz.asFoundClassSubject();
              assertEquals(
                  typeName(LibraryClass.class),
                  foundClassSubject.getSuperClass().getOriginalName());
            })
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("LibraryClass::foo");
  }

  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  public static class Main extends LibraryClass {

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
