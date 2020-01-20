// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;

// Provides convenience to use Paths/SafeVarargs which are missing on old Android but
// required by some Jdk tests, and for java.base extensions.

public class Jdk11DesugaredLibraryTestBase extends DesugaredLibraryTestBase {

  protected static Path[] JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES;
  static Path JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR;
  private static final Path JDK_11_JAVA_BASE_EXTENSION_FILES_DIR =
      Paths.get("third_party/openjdk/jdk-11-test/lib/testlibrary/bootlib/java.base");

  private static final Path ANDROID_PATHS_FILES_DIR =
      Paths.get("third_party/android_jar/lib-v26/xxx/");
  private static final Path ANDROID_SAFE_VAR_ARGS_LOCATION =
      Paths.get("third_party/android_jar/lib-v26/java/lang/SafeVarargs.class");
  private static final Path[] ANDROID_PATHS_FILES =
      new Path[] {
        Paths.get("java/nio/file/Files.class"),
        Paths.get("java/nio/file/OpenOption.class"),
        Paths.get("java/nio/file/Watchable.class"),
        Paths.get("java/nio/file/Path.class"),
        Paths.get("java/nio/file/Paths.class")
      };

  protected static Path[] getPathsFiles() {
    return Arrays.stream(ANDROID_PATHS_FILES)
        .map(ANDROID_PATHS_FILES_DIR::resolve)
        .toArray(Path[]::new);
  }

  protected static Path getSafeVarArgsFile() {
    return ANDROID_SAFE_VAR_ARGS_LOCATION;
  }

  protected static Path[] testNGSupportProgramFiles() {
    return new Path[] {
      Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar"),
      Paths.get(JDK_TESTS_BUILD_DIR + "jcommander-1.48.jar"),
      Paths.get(ToolHelper.JAVA_CLASSES_DIR + "examplesTestNGRunner/TestNGMainRunner.class")
    };
  }

  private static Path[] getJavaBaseExtensionsFiles() throws Exception {
    Path[] files =
        getAllFilesWithSuffixInDirectory(JDK_11_JAVA_BASE_EXTENSION_FILES_DIR, JAVA_EXTENSION);
    assert files.length > 0;
    return files;
  }

  @BeforeClass
  public static void compileJavaBaseExtensions() throws Exception {
    JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR = getStaticTemp().newFolder("jdk11JavaBaseExt").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + JDK_11_JAVA_BASE_EXTENSION_FILES_DIR);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(
            Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")))
        .addSourceFiles(getJavaBaseExtensionsFiles())
        .setOutputPath(JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR)
        .compile();
    JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES.length > 0;
  }

  private String getTestNGKeepRules() {
    // Keep data providers and their annotations.
    return "-keepclasseswithmembers class * {\n"
        + "    @org.testng.annotations.DataProvider <methods>;\n"
        + "}\n"
        + "-keepattributes *Annotation*\n"
        // Do not even attempt to shrink testNG (unrelated to desugared lib shrinking goal).
        + "-keep class org.testng.** { *; }\n"
        // There are missing classes in testNG.
        + "-dontwarn";
  }

  protected Path buildDesugaredLibraryWithJavaBaseExtension(
      AndroidApiLevel apiLevel, String keepRules, boolean shrink) {
    // there are missing classes from testNG.
    keepRules = getTestNGKeepRules() + keepRules;
    return buildDesugaredLibrary(
        apiLevel,
        keepRules,
        shrink,
        ImmutableList.copyOf(JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES));
  }
}
