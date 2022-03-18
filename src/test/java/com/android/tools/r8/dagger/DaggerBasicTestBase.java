// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dagger;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.DaggerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DaggerBasicTestBase extends TestBase {

  public static class CompiledCode {
    public final Path compiledProgramNotDependingOnDagger;
    public final Path compiledProgramDependingOnDagger;
    public final Path daggerGeneratedSourceFiles;

    public CompiledCode(
        Path compiledProgramNotDependingOnDagger,
        Path compiledProgramDependingOnDagger,
        Path daggerGeneratedSourceFiles) {
      this.compiledProgramNotDependingOnDagger = compiledProgramNotDependingOnDagger;
      this.compiledProgramDependingOnDagger = compiledProgramDependingOnDagger;
      this.daggerGeneratedSourceFiles = daggerGeneratedSourceFiles;
    }
  }

  public static final List<String> javacTargets = ImmutableList.of("1.8", "11");

  private static Map<String, CompiledCode> compilerCode;

  private static void compile(
      List<Path> sourceNotDependingOnDagger,
      List<Path> sourceDependingOnDagger,
      BiFunction<String, byte[], byte[]> transformer)
      throws Exception {
    ImmutableMap.Builder<String, CompiledCode> builder = ImmutableMap.builder();
    for (String target : javacTargets) {
      Path compiledProgramNotDependingOnDagger =
          DaggerUtils.compileWithoutAnnotationProcessing(target, sourceNotDependingOnDagger);
      // Use transformer on code not depending on Dagger.
      if (transformer != null) {
        compiledProgramNotDependingOnDagger =
            ZipUtils.map(
                compiledProgramNotDependingOnDagger,
                getStaticTemp().newFolder().toPath().resolve("transformed.jar"),
                (entry, bytes) ->
                    FileUtils.isClassFile(entry.getName())
                        ? transformer.apply(
                            entry
                                .getName()
                                .substring(
                                    0,
                                    entry.getName().length() - FileUtils.CLASS_EXTENSION.length()),
                            bytes)
                        : bytes);
      }
      // Compile with Dagger annotation processor. The part of the program not relying on dagger
      // generated types are passed as class files (potentially after transformation) and on command
      // line for being included in annotation processing.
      Path compiledProgramDependingOnDagger =
          DaggerUtils.compileWithAnnotationProcessing(
              target,
              sourceDependingOnDagger,
              ImmutableList.of(compiledProgramNotDependingOnDagger));
      Path daggerGeneratedSourceFiles =
          ZipUtils.filter(
              compiledProgramDependingOnDagger,
              getStaticTemp().newFolder().toPath().resolve("source.jar"),
              entry -> entry.getName().endsWith(".java"));
      // Check the generated Dagger source files.
      Set<String> generatedFiles = new HashSet<>();
      ZipUtils.iter(
          daggerGeneratedSourceFiles, (entry, unused) -> generatedFiles.add(entry.getName()));
      assertEquals(
          ImmutableSet.of(
              "basic/I1Impl1_Factory.java",
              "basic/I2Impl1_Factory.java",
              "basic/I3Impl1_Factory.java",
              "basic/ModuleUsingProvides_I1Factory.java",
              "basic/ModuleUsingProvides_I2Factory.java",
              "basic/ModuleUsingProvides_I3Factory.java",
              "basic/ModuleUsingProvides_Proxy.java",
              "basic/DaggerMainComponentUsingBinds.java",
              "basic/DaggerMainComponentUsingProvides.java"),
          generatedFiles);
      builder.put(
          target,
          new CompiledCode(
              compiledProgramNotDependingOnDagger,
              compiledProgramDependingOnDagger,
              daggerGeneratedSourceFiles));
    }
    compilerCode = builder.build();
  }

  private static boolean sourceFileReferingDaggerGeneratedClasses(Path file) {
    return file.getFileName().toString().startsWith("Main");
  }

  static void compileWithSingleton() throws Exception {
    compile(
        javaSourceFiles("basic", path -> !sourceFileReferingDaggerGeneratedClasses(path)),
        javaSourceFiles("basic", DaggerBasicTestBase::sourceFileReferingDaggerGeneratedClasses),
        DaggerBasicTestBase::transformAddSingleton);
  }

  static void compileWithoutSingleton() throws Exception {
    compile(
        javaSourceFiles("basic", path -> !sourceFileReferingDaggerGeneratedClasses(path)),
        javaSourceFiles("basic", DaggerBasicTestBase::sourceFileReferingDaggerGeneratedClasses),
        null);
  }

  private static byte[] transformAddSingleton(String binaryName, byte[] bytes) {
    // Add @Singleton to the constructors used with @Bind.
    if (binaryName.endsWith("Impl1")) {
      return transformer(bytes, Reference.classFromBinaryName(binaryName))
          .addClassTransformer(
              new ClassTransformer() {
                @Override
                public void visitEnd() {
                  super.visitAnnotation("Ljavax/inject/Singleton;", true);
                }
              })
          .transform();
    }
    // Add @Singleton to the methods annotated with @Provides.
    if (binaryName.endsWith("ModuleUsingProvides")) {
      return transformer(bytes, Reference.classFromBinaryName(binaryName))
          .addMethodTransformer(
              new MethodTransformer() {
                @Override
                public void visitEnd() {
                  super.visitAnnotation("Ljavax/inject/Singleton;", true);
                }
              })
          .transform();
    }
    return bytes;
  }

  static List<Path> javaSourceFiles(String testDir, Predicate<Path> filter) throws Exception {
    try (Stream<Path> walk =
        Files.walk(Paths.get(ToolHelper.TESTS_DIR, "examplesDagger", testDir))) {
      return walk.filter(filter).filter(FileUtils::isJavaFile).collect(Collectors.toList());
    }
  }

  Collection<Path> getProgramFiles(String target) {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.add(compilerCode.get(target).compiledProgramDependingOnDagger);
    builder.add(compilerCode.get(target).compiledProgramNotDependingOnDagger);
    builder.addAll(DaggerUtils.getDaggerRuntime());
    return builder.build();
  }
}
