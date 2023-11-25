// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.GenerateDesugaredLibraryLintFiles;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.GenerateHtmlDoc;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LintFilesTest extends DesugaredLibraryTestBase {

  private static final String ANDROID_JAR_34 =
      ToolHelper.THIRD_PARTY_DIR + "android_jar/lib-v34/android.jar";

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  private List<String> lintContents;

  @Parameters(name = "{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        ImmutableList.of(JDK8, JDK11_MINIMAL, JDK11, JDK11_PATH, JDK11_LEGACY));
  }

  public LintFilesTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    assert parameters.isNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private boolean supportsAllMethodsOf(String type) {
    return lintContents.contains(type);
  }

  private boolean supportsMethodButNotAllMethodsInClass(String method) {
    assert method.contains("#");
    return !supportsAllMethodsOf(method.split("#")[0]) && lintContents.contains(method);
  }

  private void checkFileContent(AndroidApiLevel minApiLevel, Path lintFile) throws Exception {
    // Just do some light probing in the generated lint files.
    lintContents = FileUtils.readAllLines(lintFile);

    // All methods supported on CHM.
    assertEquals(
        libraryDesugaringSpecification != JDK8,
        supportsAllMethodsOf("java/util/concurrent/ConcurrentHashMap"));

    // All methods supported on BiFunction with maintain prefix.
    assertTrue(supportsAllMethodsOf("java/util/function/BiFunction"));

    if (libraryDesugaringSpecification == JDK11_MINIMAL) {
      return;
    }

    // All methods supported on Optional*.
    assertTrue(supportsAllMethodsOf("java/util/Optional"));
    assertTrue(supportsAllMethodsOf("java/util/OptionalInt"));

    // No parallel* methods pre L, Stream are never fully supported due to takeWhile/dropWhile.
    assertEquals(
        minApiLevel == AndroidApiLevel.L,
        supportsMethodButNotAllMethodsInClass(
            "java/util/Collection#parallelStream()Ljava/util/stream/Stream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.L,
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/DoubleStream#parallel()Ljava/util/stream/DoubleStream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.L,
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/BaseStream#parallel()Ljava/util/stream/BaseStream;"));
    assertTrue(
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/DoubleStream#allMatch(Ljava/util/function/DoublePredicate;)Z"));

    assertEquals(
        libraryDesugaringSpecification != JDK8, supportsAllMethodsOf("java/util/concurrent/Flow"));

    // Checks specific methods are supported or not in JDK8, all is supported in JDK11.
    if (libraryDesugaringSpecification == JDK8) {
      // Supported methods on ConcurrentHashMap.
      assertTrue(
          supportsMethodButNotAllMethodsInClass(
              "java/util/concurrent/ConcurrentHashMap#getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));

      // Don't include constructors.
      assertFalse(
          supportsMethodButNotAllMethodsInClass(
              "java/util/concurrent/ConcurrentHashMap#<init>()V"));

      // Unsupported methods on ConcurrentHashMap.
      assertFalse(
          supportsMethodButNotAllMethodsInClass(
              "java/util/concurrent/ConcurrentHashMap#reduce(JLjava/util/function/BiFunction;Ljava/util/function/BiFunction;)Ljava/lang/Object;"));
      assertFalse(
          supportsMethodButNotAllMethodsInClass(
              "java/util/concurrent/ConcurrentHashMap#newKeySet()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"));
    }

    // Maintain type.
    assertEquals(
        libraryDesugaringSpecification != JDK8 && libraryDesugaringSpecification != JDK11_LEGACY,
        supportsAllMethodsOf("java/io/UncheckedIOException"));

    // Retarget method.
    assertTrue(
        supportsMethodButNotAllMethodsInClass(
            "java/util/Arrays#spliterator([I)Ljava/util/Spliterator$OfInt;"));

    // Emulated interface default method.
    assertTrue(
        supportsMethodButNotAllMethodsInClass(
            "java/util/List#spliterator()Ljava/util/Spliterator;"));

    // Emulated interface static method.
    assertTrue(
        supportsMethodButNotAllMethodsInClass(
            "java/util/Map$Entry#comparingByValue()Ljava/util/Comparator;"));

    // No no-default method from emulated interface.
    assertFalse(supportsMethodButNotAllMethodsInClass("java/util/List#size()I"));

    // File should be sorted.
    List<String> sorted = new ArrayList<>(lintContents);
    sorted.sort(Comparator.naturalOrder());
    assertEquals(lintContents, sorted);
  }

  @Test
  public void testLint() throws Exception {
    Path directory = temp.newFolder().toPath();
    Path jdkLibJar =
        libraryDesugaringSpecification == JDK8
            ? ToolHelper.DESUGARED_JDK_8_LIB_JAR
            : LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar();
    GenerateDesugaredLibraryLintFiles.main(
        new String[] {
          libraryDesugaringSpecification.getSpecification().toString(),
          jdkLibJar.toString(),
          directory.toString(),
          // TODO(b/289365156): Should probably not be hardcoded on U.
          ToolHelper.THIRD_PARTY_DIR
              + "android_jar/lib-v"
              + AndroidApiLevel.U.getLevel()
              + "/android.jar"
        });
    InternalOptions options = new InternalOptions(new DexItemFactory(), new Reporter());
    DesugaredLibrarySpecification desugaredLibrarySpecification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            StringResource.fromFile(libraryDesugaringSpecification.getSpecification()),
            options.itemFactory,
            options.reporter,
            false,
            AndroidApiLevel.B.getLevel());

    AndroidApiLevel requiredCompilationApiLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    Path compileApiLevelDirectory =
        directory.resolve("compile_api_level_" + requiredCompilationApiLevel.getLevel());

    assertTrue(Files.exists(compileApiLevelDirectory));
    for (AndroidApiLevel minApiLevel : AndroidApiLevel.values()) {
      String desugaredApisBaseName =
          "desugared_apis_" + requiredCompilationApiLevel.getLevel() + "_" + minApiLevel.getLevel();
      if (minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B) {
        assertTrue(Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
        checkFileContent(
            minApiLevel, compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt"));
      } else {
        assertFalse(Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
      }
    }
  }

  @Test
  public void testHTML() throws Exception {
    Path jdkLibJar =
        libraryDesugaringSpecification == JDK8
            ? ToolHelper.DESUGARED_JDK_8_LIB_JAR
            : LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar();

    Path directory2 = temp.newFolder().toPath();
    GenerateHtmlDoc.main(
        new String[] {
          "--generate-api-docs",
          libraryDesugaringSpecification.getSpecification().toString(),
          jdkLibJar.toString(),
          directory2.toString(),
          // TODO(b/289365156): Should probably not be hardcoded on U.
          ToolHelper.THIRD_PARTY_DIR
              + "android_jar/lib-v"
              + AndroidApiLevel.U.getLevel()
              + "/android.jar"
        });
    List<String> html = Files.readAllLines(directory2.resolve("apis.html"));
    // The doc has the same content than the lint data that is tested above, this is just a sanity
    // check that the doc generation ran without error and looks sane.
    assertEquals("<tr>", html.get(0));
    assertEquals("</tr>", html.get(html.size() - 2));
    if (libraryDesugaringSpecification == JDK11 || libraryDesugaringSpecification == JDK11_PATH) {
      assertEquals(6, html.stream().filter(s -> s.contains("Flow")).count());
    }
  }

  public static void main(String[] args) throws Exception {
    // Generate all html docs and lint files.
    Path top = Paths.get("generated");
    Path html = top.resolve("html");
    Files.createDirectories(html);
    ImmutableList<LibraryDesugaringSpecification> specs =
        ImmutableList.of(JDK8, JDK11_MINIMAL, JDK11, JDK11_PATH, JDK11_LEGACY);
    for (LibraryDesugaringSpecification spec : specs) {
      Path jdkLibJar =
          spec == JDK8
              ? ToolHelper.DESUGARED_JDK_8_LIB_JAR
              : LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar();
      new GenerateHtmlDoc(
              StringResource.fromFile(spec.getSpecification()),
              ImmutableList.of(ArchiveProgramResourceProvider.fromArchive(jdkLibJar)),
              html,
              ImmutableList.of(new ArchiveClassFileProvider(Paths.get(ANDROID_JAR_34))))
          .run(spec + ".html");
      Path lint = top.resolve("lint_" + spec);
      Files.createDirectories(lint);
      new GenerateDesugaredLibraryLintFiles(
              new Reporter(),
              StringResource.fromFile(spec.getSpecification()),
              ImmutableList.of(ArchiveProgramResourceProvider.fromArchive(jdkLibJar)),
              lint,
              ImmutableList.of(new ArchiveClassFileProvider(Paths.get(ANDROID_JAR_34))))
          .run();
    }
  }
}
