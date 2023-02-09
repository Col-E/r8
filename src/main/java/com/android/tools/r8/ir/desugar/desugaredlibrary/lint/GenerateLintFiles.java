// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Timing;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class GenerateLintFiles extends AbstractGenerateFiles {

  public static GenerateLintFiles createForTesting(
      Path specification, Set<Path> implementation, Path outputDirectory) throws Exception {
    return new GenerateLintFiles(specification, implementation, outputDirectory);
  }

  public GenerateLintFiles(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory);
  }

  public GenerateLintFiles(
      Path desugarConfigurationPath,
      Collection<Path> desugarImplementationPath,
      Path outputDirectory)
      throws Exception {
    super(desugarConfigurationPath, desugarImplementationPath, outputDirectory);
  }

  private CfCode buildEmptyThrowingCfCode(DexMethod method) {
    CfInstruction insn[] = {new CfConstNull(), new CfThrow()};
    return new CfCode(method.holder, 1, method.proto.parameters.size() + 1, Arrays.asList(insn));
  }

  private void addMethodsToHeaderJar(
      DexApplication.Builder builder, DexClass clazz, List<DexEncodedMethod> methods) {
    if (methods.size() == 0) {
      return;
    }

    List<DexEncodedMethod> directMethods = new ArrayList<>();
    List<DexEncodedMethod> virtualMethods = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      assert method.getHolderType() == clazz.type;
      CfCode code = null;
      if (!method.accessFlags.isAbstract() /*&& !method.accessFlags.isNative()*/) {
        code = buildEmptyThrowingCfCode(method.getReference());
      }
      DexEncodedMethod throwingMethod =
          DexEncodedMethod.builder()
              .setMethod(method.getReference())
              .setAccessFlags(method.accessFlags)
              .setGenericSignature(MethodTypeSignature.noSignature())
              .setCode(code)
              .setClassFileVersion(CfVersion.V1_6)
              .disableAndroidApiLevelCheck()
              .build();
      if (method.isStatic() || method.isDirectMethod()) {
        directMethods.add(throwingMethod);
      } else {
        virtualMethods.add(throwingMethod);
      }
    }

    DexEncodedMethod[] directMethodsArray = new DexEncodedMethod[directMethods.size()];
    DexEncodedMethod[] virtualMethodsArray = new DexEncodedMethod[virtualMethods.size()];
    directMethods.toArray(directMethodsArray);
    virtualMethods.toArray(virtualMethodsArray);
    assert !options.encodeChecksums;
    ChecksumSupplier checksumSupplier = DexProgramClass::invalidChecksumRequest;
    DexProgramClass programClass =
        new DexProgramClass(
            clazz.type,
            null,
            Origin.unknown(),
            clazz.accessFlags,
            clazz.superType,
            clazz.interfaces,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            MethodCollectionFactory.fromMethods(directMethodsArray, virtualMethodsArray),
            false,
            checksumSupplier);
    builder.addProgramClass(programClass);
  }

  private String lintBaseFileName(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel) {
    return "desugared_apis_" + compilationApiLevel.getLevel() + "_" + minApiLevel.getLevel();
  }

  private Path lintFile(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel, String extension)
      throws Exception {
    Path directory = outputDirectory.resolve("compile_api_level_" + compilationApiLevel.getLevel());
    Files.createDirectories(directory);
    return Paths.get(
        directory
            + File.separator
            + lintBaseFileName(compilationApiLevel, minApiLevel)
            + extension);
  }

  private void writeLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedMethods supportedMethods)
      throws Exception {
    // Build a plain text file with the desugared APIs.
    List<String> desugaredApisSignatures = new ArrayList<>();

    LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, Timing.empty());
    supportedMethods.supportedMethods.forEach(
        (clazz, methods) -> {
          String classBinaryName =
              DescriptorUtils.getClassBinaryNameFromDescriptor(clazz.type.descriptor.toString());
          if (!supportedMethods.classesWithAllMethodsSupported.contains(clazz)) {
            for (DexEncodedMethod method : methods) {
              if (method.isInstanceInitializer() || method.isClassInitializer()) {
                // No new constructors are added.
                continue;
              }
              desugaredApisSignatures.add(
                  classBinaryName
                      + '#'
                      + method.getReference().name
                      + method.getReference().proto.toDescriptorString());
            }
          } else {
            desugaredApisSignatures.add(classBinaryName);
          }

          addMethodsToHeaderJar(builder, clazz, methods);
        });

    // Write a plain text file with the desugared APIs.
    desugaredApisSignatures.sort(Comparator.naturalOrder());
    FileUtils.writeTextFile(
        lintFile(compilationApiLevel, minApiLevel, ".txt"), desugaredApisSignatures);

    // Write a header jar with the desugared APIs.
    AppView<?> appView =
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                builder.build(), GlobalSyntheticsStrategy.forNonSynthesizing()));
    CfApplicationWriter writer = new CfApplicationWriter(appView, options.getMarker());
    ClassFileConsumer consumer =
        new ClassFileConsumer.ArchiveConsumer(
            lintFile(compilationApiLevel, minApiLevel, FileUtils.JAR_EXTENSION));
    writer.write(consumer);
    consumer.finished(options.reporter);
  }

  private void generateLintFiles(
      AndroidApiLevel compilationApiLevel,
      Predicate<AndroidApiLevel> generateForThisMinApiLevel,
      BiPredicate<AndroidApiLevel, DexEncodedMethod> supportedForMinApiLevel)
      throws Exception {
    System.out.print("  - generating for min API:");
    for (AndroidApiLevel minApiLevel : AndroidApiLevel.values()) {
      if (!generateForThisMinApiLevel.test(minApiLevel)) {
        continue;
      }

      System.out.print(" " + minApiLevel);

      SupportedMethods supportedMethods =
          collectSupportedMethods(
              compilationApiLevel, (method -> supportedForMinApiLevel.test(minApiLevel, method)));
      writeLintFiles(compilationApiLevel, minApiLevel, supportedMethods);
    }
    System.out.println();
  }

  void run() throws Exception {
    // Run over all the API levels that the desugared library can be compiled with.
    for (int apiLevel = MAX_TESTED_ANDROID_API_LEVEL.getLevel();
        apiLevel >= desugaredLibrarySpecification.getRequiredCompilationApiLevel().getLevel();
        apiLevel--) {
      System.out.println("Generating lint files for compile API " + apiLevel);
      run(apiLevel);
    }
  }

  public void run(int apiLevel) throws Exception {
    generateLintFiles(
        AndroidApiLevel.getAndroidApiLevel(apiLevel),
        minApiLevel -> minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B,
        (minApiLevel, method) -> {
          assert minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B;
          if (minApiLevel == AndroidApiLevel.L) {
            return true;
          }
          assert minApiLevel == AndroidApiLevel.B;
          return !parallelMethods.contains(method.getReference());
        });
  }

  public static void main(String[] args) throws Exception {
    AbstractGenerateFiles.main(args);
  }
}
