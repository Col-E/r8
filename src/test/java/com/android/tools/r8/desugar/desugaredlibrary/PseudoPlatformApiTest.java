// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Test of an API which is to be treated as a platform API. This is simulating an API which can be
// implemented by an OEM in an on-device APK containing the DEX code. The program will probe for the
// OEM class and if not found it will use a built-in program class implementing the interface.
//
// This is tested with and without library desugaring.

// For context see b/229793269.
@RunWith(Parameterized.class)
public class PseudoPlatformApiTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.P)
            .build());
  }

  private Path androidJarAdditions() throws Exception {
    return ZipBuilder.builder(temp.newFile("additions_android.jar").toPath())
        .addFilesRelative(
            ToolHelper.getClassPathForTests(),
            ToolHelper.getClassFileForTestClass(PseudoPlatformInterface.class))
        .build();
  }

  private Path androidJarAdditionsDex() throws Exception {
    // Build the OEM DEX files without library desugaring enabled and using min API 28.
    return testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addProgramClasses(PseudoPlatformInterface.class)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip();
  }

  private Path oemDex() throws Exception {
    // Build the OEM DEX files without library desugaring enabled and using min API 28.
    return testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        // This is equivalent to adding the PseudoPlatformInterface to android.jar.
        .addLibraryClasses(PseudoPlatformInterface.class)
        .addProgramClasses(OemClass.class)
        .setMinApi(AndroidApiLevel.P)
        .compile()
        .writeToZip();
  }

  @Test
  public void testD8WithoutLibraryDesugaringOemClassNotPresent() throws Exception {
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addLibraryFiles(androidJarAdditions())
        .addProgramClasses(ProgramClass.class)
        .setMinApi(AndroidApiLevel.H_MR2)
        .addRunClasspathFiles(androidJarAdditionsDex())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("DEFAULT-X", "Y-DEFAULT");
  }

  @Test
  public void testD8WithoutLibraryDesugaringOemClassPresent() throws Exception {
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addLibraryFiles(androidJarAdditions())
        .addProgramClasses(ProgramClass.class)
        .setMinApi(AndroidApiLevel.H_MR2)
        .addRunClasspathFiles(androidJarAdditionsDex())
        .addRunClasspathFiles(oemDex())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("OEM-X", "Y-OEM");
  }

  @Test
  public void testD8WithLibraryDesugaringOemClassNotPresent() throws Exception {
    // Enable library desugaring with an effective min API of 1.
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addLibraryFiles(androidJarAdditions())
        .addProgramClasses(ProgramClass.class)
        .setMinApi(AndroidApiLevel.H_MR2)
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(AndroidApiLevel.B))
        .addRunClasspathFiles(androidJarAdditionsDex())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("DEFAULT-X", "Y-DEFAULT");
  }

  @Test
  public void testD8WithLibraryDesugaringOemClassPresent() throws Exception {
    // Enable library desugaring with an effective min API of 1.
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addLibraryFiles(androidJarAdditions())
        .addProgramClasses(ProgramClass.class)
        .setMinApi(AndroidApiLevel.H_MR2)
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(AndroidApiLevel.B))
        .addRunClasspathFiles(androidJarAdditionsDex())
        .addRunClasspathFiles(oemDex())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("OEM-X", "Y-OEM");
  }

  interface PseudoPlatformInterface {
    Function<String, String> api(Function<String, String> fn);
  }

  static class OemClass implements PseudoPlatformInterface {
    public Function<String, String> api(Function<String, String> fn) {
      System.out.println(fn.apply("OEM-"));
      return (v) -> v + "-OEM";
    }
  }

  static class ProgramClass implements PseudoPlatformInterface {
    public Function<String, String> api(Function<String, String> fn) {
      System.out.println(fn.apply("DEFAULT-"));
      return (v) -> v + "-DEFAULT";
    }

    public static void main(String[] args) {
      PseudoPlatformInterface pseudoPlatformInterface = null;
      try {
        Class<?> oemClass =
            Class.forName(
                "com.android.tools.r8.desugar.desugaredlibrary.PseudoPlatformApiTest$OemClass");
        pseudoPlatformInterface =
            (PseudoPlatformInterface) oemClass.getDeclaredConstructor().newInstance();
      } catch (ClassNotFoundException
          | NoSuchMethodException
          | InstantiationException
          | InvocationTargetException
          | IllegalAccessException e) {
        // Could not load OEM class use built-in program class.
        pseudoPlatformInterface = new ProgramClass();
      }
      Function<String, String> fn = (v) -> v + "X";
      System.out.println(pseudoPlatformInterface.api(fn).apply("Y"));
    }
  }
}
