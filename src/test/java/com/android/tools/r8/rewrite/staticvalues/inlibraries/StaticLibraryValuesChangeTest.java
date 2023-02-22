// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.staticvalues.inlibraries;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.PreloadedClassFileProvider;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticLibraryValuesChangeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testStatic() throws Exception {
    /*
     * Three versions of the class LibraryClass is used in the test:
     *
     *   * one in Java code, where field x is "public static int x = 1" (not final)
     *   * one in Jasmin code, where field x is "public static final int x = 2"
     *   * int in smali code, where field x is  "public static final int x = 3"
     *
     * The first version is used to compile TestMain with javac. This causes the field accessor
     * for x to be in the code for TestMain.main. As javac will inline compile-time constants
     * it cannot be declared "final" here.
     *
     * The second version is used as a library class when compiling the javac compiled TestMain
     * with R8.
     *
     * The third version is used for running the R8 compiled TestMain on Art.
     */

    // Build the second version of LibraryClass
    JasminBuilder compileTimeLibrary = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = compileTimeLibrary.addClass(
        "com.android.tools.r8.rewrite.staticvalues.inlibraries.LibraryClass");
    clazz.addStaticFinalField("x", "I", "2");
    clazz.addStaticMethod("getX", ImmutableList.of(), "I",
        ".limit stack 1",
        ".limit locals 0",
        "  iconst_2",
        "  ireturn");

    // Build the third version of LibraryClass
    SmaliBuilder runtimeLibrary = new SmaliBuilder(LibraryClass.class.getCanonicalName());
    runtimeLibrary.addStaticField("x", "I", "3");
    runtimeLibrary.addStaticMethod(
        "int",
        "getX",
        ImmutableList.of(),
        1,
        "    const/4             v0, 3",
        "    return              v0"
    );

    Path lib = temp.newFile("lib.dex").toPath().toAbsolutePath();
    FileUtils.writeToFile(lib, null, runtimeLibrary.compile());

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(TestMain.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        // Compile TestMain with R8 using the second version of LibraryClass as library.
        .addLibraryProvider(
            PreloadedClassFileProvider.fromClassData(
                DescriptorUtils.javaTypeToDescriptor(LibraryClass.class.getName()),
                compileTimeLibrary.buildClasses().get(0)))
        .noTreeShaking()
        .addDontObfuscate()
        .compile()
        // Merge the compiled TestMain with the runtime version of LibraryClass.
        .addRunClasspathFiles(lib)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutput("33");
  }
}
