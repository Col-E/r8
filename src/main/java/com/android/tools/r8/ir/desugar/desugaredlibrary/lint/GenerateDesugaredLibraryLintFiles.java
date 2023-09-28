// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.MethodAnnotation;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class GenerateDesugaredLibraryLintFiles extends AbstractGenerateFiles {

  // Only recent versions of studio support the format with fields.
  private static final boolean FORMAT_WITH_FIELD = true;

  public static GenerateDesugaredLibraryLintFiles createForTesting(
      Path specification, Set<Path> implementation, Path outputDirectory, Path androidJar) {
    return new GenerateDesugaredLibraryLintFiles(
        specification, implementation, outputDirectory, androidJar);
  }

  public GenerateDesugaredLibraryLintFiles(
      String desugarConfigurationPath,
      String desugarImplementationPath,
      String outputDirectory,
      String androidJarPath) {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory, androidJarPath);
  }

  GenerateDesugaredLibraryLintFiles(
      Path desugarConfigurationPath,
      Collection<Path> desugarImplementationPath,
      Path outputDirectory,
      Path androidJar) {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory, androidJar);
  }

  private String lintBaseFileName(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel) {
    return "desugared_apis_" + compilationApiLevel.getLevel() + "_" + minApiLevel.getLevel();
  }

  Path lintFile(AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel, String extension)
      throws Exception {
    Path directory = output.resolve("compile_api_level_" + compilationApiLevel.getLevel());
    Files.createDirectories(directory);
    return Paths.get(
        directory
            + File.separator
            + lintBaseFileName(compilationApiLevel, minApiLevel)
            + extension);
  }

  void writeLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedClasses supportedClasses)
      throws Exception {
    // Build a plain text file with the desugared APIs.
    List<String> desugaredApisSignatures = new ArrayList<>();

    supportedClasses.forEachClass(
        (supportedClass) -> {
          String classBinaryName =
              DescriptorUtils.getClassBinaryNameFromDescriptor(
                  supportedClass.getType().descriptor.toString());
          if (!supportedClass.getClassAnnotation().isFullySupported()) {
            supportedClass.forEachMethodAndAnnotation(
                (method, methodAnnotation) -> {
                  if (method.isInstanceInitializer() || method.isClassInitializer()) {
                    // No new constructors are added.
                    return;
                  }
                  if (shouldAddMethodToLint(methodAnnotation, minApiLevel)) {
                    desugaredApisSignatures.add(
                        classBinaryName
                            + '#'
                            + method.getReference().name
                            + method.getReference().proto.toDescriptorString());
                  }
                });
            if (FORMAT_WITH_FIELD) {
              supportedClass.forEachFieldAndAnnotation(
                  (field, fieldAnnotation) -> {
                    if (fieldAnnotation == null || !fieldAnnotation.unsupportedInMinApiRange) {
                      desugaredApisSignatures.add(
                          classBinaryName + '#' + field.getReference().name);
                    }
                  });
            }
          } else {
            desugaredApisSignatures.add(classBinaryName);
          }
        });

    for (DexMethod extraMethod : supportedClasses.getExtraMethods()) {
      String classBinaryName =
          DescriptorUtils.getClassBinaryNameFromDescriptor(
              extraMethod.getHolderType().descriptor.toString());
      desugaredApisSignatures.add(
          classBinaryName + '#' + extraMethod.name + extraMethod.proto.toDescriptorString());
    }

    // Write a plain text file with the desugared APIs.
    desugaredApisSignatures.sort(Comparator.naturalOrder());
    FileUtils.writeTextFile(
        lintFile(compilationApiLevel, minApiLevel, ".txt"), desugaredApisSignatures);
  }

  private boolean shouldAddMethodToLint(
      MethodAnnotation methodAnnotation, AndroidApiLevel minApiLevel) {
    if (methodAnnotation == null) {
      return true;
    }
    if (methodAnnotation.unsupportedInMinApiRange) {
      // Do not lint method which are unavailable with some min apis.
      return false;
    }
    if (methodAnnotation.parallelStreamMethod) {
      return minApiLevel == AndroidApiLevel.L;
    }
    assert methodAnnotation.missingFromLatestAndroidJar;
    // We add missing methods from the latest jar, javac will add the squikles.
    return true;
  }

  private void generateLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedClasses supportedMethods)
      throws Exception {
    System.out.print("  - generating for min API:");
    System.out.print(" " + minApiLevel);
    writeLintFiles(compilationApiLevel, minApiLevel, supportedMethods);
  }

  String getDebugIdentifier() {
    return desugaredLibrarySpecification.getIdentifier() == null
        ? "backported methods only"
        : desugaredLibrarySpecification.getIdentifier();
  }

  @Override
  public AndroidApiLevel run() throws Exception {
    AndroidApiLevel compilationLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    SupportedClasses supportedMethods =
        new SupportedClassesGenerator(options, androidJar)
            .run(desugaredLibraryImplementation, desugaredLibrarySpecificationPath);
    System.out.println(
        "Generating lint files for "
            + getDebugIdentifier()
            + " (compile API "
            + compilationLevel
            + ")");
    generateLintFiles(compilationLevel, AndroidApiLevel.B, supportedMethods);
    generateLintFiles(compilationLevel, AndroidApiLevel.L, supportedMethods);
    System.out.println();
    return compilationLevel;
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 3 || args.length == 4) {
      new GenerateDesugaredLibraryLintFiles(args[0], args[1], args[2], getAndroidJarPath(args, 4))
          .run();
      return;
    }
    throw new RuntimeException(
        StringUtils.joinLines(
            "Invalid invocation.",
            "Usage: GenerateDesugaredLibraryLintFiles <desugar configuration> "
                + "<desugar implementation> <output directory> [<android jar path for Android "
                + MAX_TESTED_ANDROID_API_LEVEL
                + " or higher>]"));
  }
}
