// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.GenerateLintFiles;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LintFilesTest extends DesugaredLibraryTestBase {

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  private List<String> lintContents;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), getJdk8Jdk11());
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

    // All methods supported on Optional*.
    assertTrue(supportsAllMethodsOf("java/util/Optional"));
    assertTrue(supportsAllMethodsOf("java/util/OptionalInt"));

    // No parallel* methods pre L, and all stream methods supported from L.
    assertEquals(
        minApiLevel == AndroidApiLevel.L,
        supportsMethodButNotAllMethodsInClass(
            "java/util/Collection#parallelStream()Ljava/util/stream/Stream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.L, supportsAllMethodsOf("java/util/stream/DoubleStream"));
    assertFalse(
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/DoubleStream#parallel()Ljava/util/stream/DoubleStream;"));
    assertFalse(
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/DoubleStream#parallel()Ljava/util/stream/BaseStream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.B,
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/DoubleStream#allMatch(Ljava/util/function/DoublePredicate;)Z"));
    assertEquals(
        minApiLevel == AndroidApiLevel.L, lintContents.contains("java/util/stream/IntStream"));
    assertFalse(
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/IntStream#parallel()Ljava/util/stream/IntStream;"));
    assertFalse(
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/IntStream#parallel()Ljava/util/stream/BaseStream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.B,
        supportsMethodButNotAllMethodsInClass(
            "java/util/stream/IntStream#allMatch(Ljava/util/function/IntPredicate;)Z"));

    assertEquals(
        libraryDesugaringSpecification != JDK8,
        supportsAllMethodsOf("java/util/concurrent/ConcurrentHashMap"));

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
        libraryDesugaringSpecification != JDK8,
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
  public void testFileContent() throws Exception {
    Path directory = temp.newFolder().toPath();
    Path jdkLibJar =
        libraryDesugaringSpecification == JDK8
            ? ToolHelper.DESUGARED_JDK_8_LIB_JAR
            : LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar();
    GenerateLintFiles.main(
        new String[] {
          libraryDesugaringSpecification.getSpecification().toString(),
          jdkLibJar.toString(),
          directory.toString()
        });
    InternalOptions options = new InternalOptions(new DexItemFactory(), new Reporter());
    DesugaredLibrarySpecification desugaredLibrarySpecification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            StringResource.fromFile(libraryDesugaringSpecification.getSpecification()),
            options.itemFactory,
            options.reporter,
            false,
            AndroidApiLevel.B.getLevel());

    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel.isGreaterThan(AndroidApiLevel.Sv2)) {
        continue;
      }
      Path compileApiLevelDirectory = directory.resolve("compile_api_level_" + apiLevel.getLevel());
      if (apiLevel.getLevel()
          < desugaredLibrarySpecification.getRequiredCompilationApiLevel().getLevel()) {
        System.out.println("!Checking " + compileApiLevelDirectory);
        continue;
      }
      assertTrue(Files.exists(compileApiLevelDirectory));
      for (AndroidApiLevel minApiLevel : AndroidApiLevel.values()) {
        String desugaredApisBaseName =
            "desugared_apis_" + apiLevel.getLevel() + "_" + minApiLevel.getLevel();
        if (minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B) {
          assertTrue(
              Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
          assertTrue(
              Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".jar")));
          checkFileContent(
              minApiLevel, compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt"));
        } else {
          assertFalse(
              Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
          assertFalse(
              Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".jar")));
        }
      }
    }
  }
}
