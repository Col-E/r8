// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.corelib.CoreLibDesugarTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;

// Provides convenience to use Paths/SafeVarargs which are missing on old Android but
// required by some Jdk tests, and for java.base extensions.

public class Jdk11CoreLibTestBase extends CoreLibDesugarTestBase {

  protected static Path[] JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES;
  protected static final Path JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_CLASSES_DIR + "Bootlib");
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

  protected static Path[] getAllFilesWithSuffixInDirectory(Path directory, String suffix)
      throws IOException {
    return Files.walk(directory)
        .filter(path -> path.toString().endsWith(suffix))
        .toArray(Path[]::new);
  }

  protected static Path getSafeVarArgsFile() {
    return ANDROID_SAFE_VAR_ARGS_LOCATION;
  }

  private static Path[] getJavaBaseExtensionsFiles() throws Exception {
    Path[] files =
        getAllFilesWithSuffixInDirectory(JDK_11_JAVA_BASE_EXTENSION_FILES_DIR, JAVA_EXTENSION);
    assert files.length > 0;
    return files;
  }

  @BeforeClass
  public static void compileJavaBaseExtensions() throws Exception {
    if (!new File(JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR.toString()).exists()) {
      List<String> options =
          Arrays.asList(
              "--add-reads",
              "java.base=ALL-UNNAMED",
              "--patch-module",
              "java.base=" + JDK_11_JAVA_BASE_EXTENSION_FILES_DIR);
      ToolHelper.runJavac(
          CfVm.JDK11,
          Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")),
          JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR,
          options,
          getJavaBaseExtensionsFiles());
    }
    JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_JAVA_BASE_EXTENSION_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES.length > 0;
  }

  // TODO(134732760): Remove this and use L8 below.
  private Map<String, String> buildBackportCoreLibraryMembers() {
    // R8 specific to deal with *8 removal.
    return ImmutableMap.<String, String>builder()
        .put("java.lang.Double8", "java.lang.Double")
        .put("java.lang.Integer8", "java.lang.Integer")
        .put("java.lang.Long8", "java.lang.Long")
        .build();
  }

  // TODO(134732760): Remove this and use L8 below.
  private Map<String, String> buildPrefixRewritingForCoreLibCompilation() {
    return ImmutableMap.<String, String>builder()
        // --rewrite_core_library_prefix.
        // Extra flags for R8
        .put("java.io.DesugarBufferedReader", "j$.io.DesugarBufferedReader")
        .put("java.io.UncheckedIOException", "j$.io.UncheckedIOException")
        // Bazel flags.
        .put("java.lang.Double8", "j$.lang.Double8")
        .put("java.lang.Integer8", "j$.lang.Integer8")
        .put("java.lang.Long8", "j$.lang.Long8")
        .put("java.lang.Math8", "j$.lang.Math8")
        .put("java.time.", "j$.time.")
        .put("java.util.stream.", "j$.util.stream.")
        .put("java.util.function.", "j$.util.function.")
        .put("java.util.Comparators", "j$.util.Comparators")
        .put("java.util.Desugar", "j$.util.Desugar")
        .put("java.util.DoubleSummaryStatistics", "j$.util.DoubleSummaryStatistics")
        .put("java.util.IntSummaryStatistics", "j$.util.IntSummaryStatistics")
        .put("java.util.LongSummaryStatistics", "j$.util.LongSummaryStatistics")
        .put("java.util.Objects", "j$.util.Objects")
        .put("java.util.Optional", "j$.util.Optional")
        .put("java.util.PrimitiveIterator", "j$.util.PrimitiveIterator")
        .put("java.util.SortedSet$1", "j$.util.SortedSet$1")
        .put("java.util.Spliterator", "j$.util.Spliterator")
        .put("java.util.StringJoiner", "j$.util.StringJoiner")
        .put("java.util.Tripwire", "j$.util.Tripwire")
        .put("java.util.concurrent.ConcurrentHashMap", "j$.util.concurrent.ConcurrentHashMap")
        .put("java.util.concurrent.DesugarUnsafe", "j$.util.concurrent.DesugarUnsafe")
        .put("java.util.concurrent.ThreadLocalRandom", "j$.util.concurrent.ThreadLocalRandom")
        .put("java.util.concurrent.atomic.DesugarAtomic", "j$.util.concurrent.atomic.DesugarAtomic")
        .build();
  }

  // TODO(134732760): Remove this and use L8 below.
  private void configureCoreLibDesugarCompilationWithJavaBaseExtension(InternalOptions options) {
    options.coreLibraryCompilation = true;
    options.backportCoreLibraryMembers = buildBackportCoreLibraryMembers();
    options.retargetCoreLibMember = buildRetargetCoreLibraryMemberForProgramCompilation();
    options.dontRewriteInvocations = buildDontRewriteInvocations();
    options.rewritePrefix = buildPrefixRewritingForCoreLibCompilation();
    options.emulateLibraryInterface = buildEmulateLibraryInterface();
  }

  protected Path buildDesugaredLibraryWithJavaBaseExtension(AndroidApiLevel apiLevel)
      throws Exception {
    // TODO(134732760): Use L8.
    return testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addProgramFiles(JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES)
        .addProgramFiles(ToolHelper.getDesugarJDKLibs())
        .addOptionsModification(this::configureCoreLibDesugarCompilationWithJavaBaseExtension)
        .setMinApi(apiLevel)
        .compile()
        .writeToZip();
  }
}
