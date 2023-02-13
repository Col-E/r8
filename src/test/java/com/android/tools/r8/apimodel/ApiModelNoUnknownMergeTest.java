// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoUnknownMergeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Set<String> methodReferences = new HashSet<>();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, LibraryClassFooCaller.class, LibraryClassBarCaller.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> {
              ClassReference fooCaller = Reference.classFromClass(LibraryClassFooCaller.class);
              ClassReference barCaller = Reference.classFromClass(LibraryClassBarCaller.class);
              options.apiModelingOptions().tracedMethodApiLevelCallback =
                  (methodReference, computedApiLevel) -> {
                    if ((methodReference.getHolderClass().equals(fooCaller)
                            && methodReference.getMethodName().equals("callFoo"))
                        || (methodReference.getHolderClass().equals(barCaller)
                            && methodReference.getMethodName().equals("callBar"))) {
                      methodReferences.add(methodReference.toSourceString());
                      assertTrue(computedApiLevel.isUnknownApiLevel());
                    }
                  };
            })
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addBootClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("LibraryClass::foo", "LibraryClass::bar");
    Set<String> expected = new HashSet<>();
    expected.add(
        Reference.methodFromMethod(LibraryClassFooCaller.class.getDeclaredMethod("callFoo"))
            .toSourceString());
    expected.add(
        Reference.methodFromMethod(LibraryClassBarCaller.class.getDeclaredMethod("callBar"))
            .toSourceString());
    // Ensure that the two caller methods has been visited.
    assertEquals(expected, methodReferences);
  }

  public static class LibraryClass {

    public static void foo() {
      System.out.println("LibraryClass::foo");
    }

    public static void bar() {
      System.out.println("LibraryClass::bar");
    }
  }

  public static class LibraryClassFooCaller {

    @NeverInline
    public static void callFoo() {
      LibraryClass.foo();
    }
  }

  public static class LibraryClassBarCaller {

    @NeverInline
    public static void callBar() {
      LibraryClass.bar();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      LibraryClassFooCaller.callFoo();
      LibraryClassBarCaller.callBar();
    }
  }
}
