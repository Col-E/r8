// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.getAllFilesWithSuffixInDirectory;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.rules.TemporaryFolder;

public class Jdk11TestLibraryDesugaringSpecification {

  private static final String EXTENSION_STRING = "build/libs/java_base_extension.jar";

  private static final Path JDK_11_JAVA_BASE_EXTENSION_FILES_DIR =
      Paths.get("third_party/openjdk/jdk-11-test/lib/testlibrary/bootlib/java.base");
  private static final Path JDK_11_TESTLIBRARY_FILES_DIR =
      Paths.get("third_party/openjdk/jdk-11-test/lib/testlibrary/jdk");

  public static Path EXTENSION_PATH;

  public static LibraryDesugaringSpecification JDK8_JAVA_BASE_EXT;
  public static LibraryDesugaringSpecification JDK11_JAVA_BASE_EXT;
  public static LibraryDesugaringSpecification JDK11_PATH_JAVA_BASE_EXT;

  private static Path[] getExtensionsFiles() throws Exception {
    Path[] files =
        getAllFilesWithSuffixInDirectory(JDK_11_JAVA_BASE_EXTENSION_FILES_DIR, JAVA_EXTENSION);
    assert files.length > 0;
    Path[] files2 = getAllFilesWithSuffixInDirectory(JDK_11_TESTLIBRARY_FILES_DIR, JAVA_EXTENSION);
    assert files2.length > 0;
    List<Path> paths = new ArrayList<>(Arrays.asList(files));
    Collections.addAll(paths, files2);
    return paths.toArray(new Path[0]);
  }

  public static void setUp() throws Exception {
    if (ToolHelper.isWindows()) {
      // The library configuration is not available on windows. Do not run anything.
      return;
    }
    EXTENSION_PATH = Paths.get(EXTENSION_STRING);
    ensureJavaBaseExtensionsCompiled();
    JDK8_JAVA_BASE_EXT = createSpecification("JDK8_JAVA_BASE_EXT", JDK8);
    JDK11_JAVA_BASE_EXT = createSpecification("JDK11_JAVA_BASE_EXT", JDK11);
    JDK11_PATH_JAVA_BASE_EXT = createSpecification("JDK11_PATH_JAVA_BASE_EXT", JDK11_PATH);
  }

  private static LibraryDesugaringSpecification createSpecification(
      String name, LibraryDesugaringSpecification template) {
    Set<Path> desugaredJDKLibFiles = new HashSet<>(template.getDesugarJdkLibs());
    desugaredJDKLibFiles.add(EXTENSION_PATH);
    Set<Path> libFiles = new HashSet<>(template.getLibraryFiles());
    libFiles.add(EXTENSION_PATH);
    return new LibraryDesugaringSpecification(
        name,
        desugaredJDKLibFiles,
        template.getSpecification(),
        libFiles,
        template.getDescriptor(),
        getTestNGKeepRules());
  }

  private static synchronized void ensureJavaBaseExtensionsCompiled() throws Exception {
    if (Files.exists(EXTENSION_PATH)) {
      return;
    }

    TemporaryFolder folder =
        new TemporaryFolder(ToolHelper.isLinux() ? null : Paths.get("build", "tmp").toFile());
    folder.create();
    Path output = folder.newFolder("jdk11Ext").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + JDK_11_JAVA_BASE_EXTENSION_FILES_DIR);
    JavaCompilerTool.create(TestRuntime.getCheckedInJdk11(), folder)
        .addOptions(options)
        .addClasspathFiles(testNGPath())
        .addSourceFiles(getExtensionsFiles())
        .setOutputPath(output)
        .compile();
    Path[] toCompile = getAllFilesWithSuffixInDirectory(output, CLASS_EXTENSION);
    assert toCompile.length > 0;

    // Jar the contents.
    List<String> cmdline = new ArrayList<>();
    cmdline.add(TestRuntime.getCheckedInJdk11().getJavaExecutable().getParent() + "/jar");
    cmdline.add("cf");
    cmdline.add("tmp.jar");
    for (Path compile : toCompile) {
      cmdline.add(output.relativize(compile).toString());
    }
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    builder.directory(output.toFile());
    ProcessResult result = ToolHelper.runProcess(builder);
    assert result.exitCode == 0;

    // Move the result into the build/libs folder.
    List<String> cmdlineMv = new ArrayList<>();
    cmdlineMv.add("mv");
    cmdlineMv.add(output.resolve("tmp.jar").toString());
    cmdlineMv.add(EXTENSION_STRING);
    ProcessResult resultMv = ToolHelper.runProcess(new ProcessBuilder(cmdlineMv));
    assert resultMv.exitCode == 0;

    folder.delete();
  }

  private static String getTestNGKeepRules() {
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
}
