// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.staticvalues.inlibraries;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.PreloadedClassFileProvider;
import com.google.common.collect.ImmutableList;
import org.junit.Assume;
import org.junit.Test;

public class StaticLibraryValuesChangeTest extends TestBase {
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

    // TODO(66944616): Can we make this work on Dalvik as well?
    Assume.assumeTrue("Skipping on 4.4.4", ToolHelper.getDexVm().getVersion() != Version.V4_4_4);

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

    // Compile TestMain with R8 using the second version of LibraryClass as library.
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(
        FilteredClassPath.unfiltered(ToolHelper.getClassFileForTestClass(TestMain.class)));
    builder.addLibraryFiles(FilteredClassPath.unfiltered(ToolHelper.getDefaultAndroidJar()));
    builder.addLibraryResourceProvider(PreloadedClassFileProvider.fromClassData(
        "Lcom/android/tools/r8/rewrite/staticvalues/inlibraries/LibraryClass;",
        compileTimeLibrary.buildClasses().get(0)));
    AndroidApp app = compileWithR8(builder.build());

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

    // Merge the compiled TestMain with the runtime version of LibraryClass.
    builder = AndroidApp.builder(app);
    builder.addDexProgramData(runtimeLibrary.compile(), EmbeddedOrigin.INSTANCE);
    String result = runOnArt(builder.build(), TestMain.class);
    assertEquals("33", result);
  }
}
