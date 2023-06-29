// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.TestBase.descriptor;
import static com.android.tools.r8.TestBase.transformer;

import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

// Provides convenience to use Paths/SafeVarargs which are missing on old Android but
// required by some Jdk tests, and for java.base extensions.

public class Jdk11SupportFiles {

  // TODO(b/289346278): See if we can remove the xxx subfolder.
  private static final Path ANDROID_PATHS_FILES_DIR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "android_jar/lib-v26/xxx/");
  private static final Path ANDROID_SAFE_VAR_ARGS_LOCATION =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "android_jar/lib-v26/java/lang/SafeVarargs.class");
  private static final Path[] ANDROID_PATHS_FILES =
      new Path[] {
        Paths.get("java/nio/file/Files.class"),
        Paths.get("java/nio/file/OpenOption.class"),
        Paths.get("java/nio/file/Watchable.class"),
        Paths.get("java/nio/file/Path.class"),
        Paths.get("java/nio/file/Paths.class")
      };

  public static Path[] getPathsFiles() {
    return Arrays.stream(ANDROID_PATHS_FILES)
        .map(ANDROID_PATHS_FILES_DIR::resolve)
        .toArray(Path[]::new);
  }

  public static Path getSafeVarArgsFile() {
    return ANDROID_SAFE_VAR_ARGS_LOCATION;
  }

  public static Path[] testNGSupportProgramFiles() {
    return new Path[] {testNGPath(), jcommanderPath()};
  }

  public static Path testNGPath() {
    return Paths.get(ToolHelper.DEPENDENCIES + "org/testng/testng/6.10/testng-6.10.jar");
  }

  public static Path jcommanderPath() {
    return Paths.get(ToolHelper.DEPENDENCIES + "com/beust/jcommander/1.48/jcommander-1.48.jar");
  }

  public static byte[] getTestNGMainRunner() throws Exception {
    return transformer(TestNGMainRunner.class)
        .setClassDescriptor("LTestNGMainRunner;")
        .replaceClassDescriptorInMethodInstructions(descriptor(TestNG.class), "Lorg/testng/TestNG;")
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TextReporter.class), "Lorg/testng/reporters/TextReporter;")
        .transform();
  }

  /** TestNGMainRunner used as the test runner in JDK11 tests. */
  public static class TestNGMainRunner {

    private static void runTestNg(Class<?> testClass, int verbose) {
      System.out.println("Running tests in " + testClass.getName());
      TestNG testng = new TestNG(false);
      testng.setTestClasses(new Class<?>[] {testClass});
      testng.setVerbose(verbose);
      // Deprecated API used because it works on Android unlike the recommended one.
      testng.addListener(new TextReporter(testClass.getName(), verbose));
      try {
        testng.run();
        System.out.print("Tests result in " + testClass.getName() + ": ");
        if (testng.hasFailure()) {
          System.out.println("FAILURE");
        } else {
          System.out.println("SUCCESS");
        }
      } catch (RuntimeException | Error e) {
        System.out.print("Tests result in " + testClass.getName() + ": ");
        System.out.println("ERROR");
        e.printStackTrace();
      }
    }

    public static void main(String[] args) throws Exception {
      // First arg is the verbosity level.
      // Second arg is the class to run.
      int verbose = Integer.parseInt(args[0]);
      runTestNg(Class.forName(args[1]), verbose);
    }
  }

  /** Stubs for the TestNGRunner */
  public static class TextReporter {

    public TextReporter(String name, int verbose) {}
  }

  public static class TestNG {

    public TestNG(boolean val) {}

    public void setTestClasses(Class<?>[] classes) {}

    public void setVerbose(int verbose) {}

    public void addListener(Object textReporter) {}

    public void run() {}

    public boolean hasFailure() {
      return false;
    }
  }
}
