// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.lint.AbstractGenerateFiles.MAX_TESTED_ANDROID_API_LEVEL;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.ClassAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.FieldAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.MethodAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class SupportedClassesGenerator {

  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";

  private final InternalOptions options;

  public SupportedClassesGenerator(InternalOptions options) {
    this.options = options;
  }

  public SupportedClasses run(Collection<Path> desugaredLibraryImplementation, Path specification)
      throws IOException {
    SupportedClasses.Builder builder = SupportedClasses.builder();
    // First analyze everything which is supported when desugaring for api 1.
    collectSupportedMembersInB(desugaredLibraryImplementation, specification, builder);
    // Second annotate all apis which are partially and/or fully supported.
    AndroidApp library =
        AndroidApp.builder()
            .addProgramFiles(getAndroidJarPath(MAX_TESTED_ANDROID_API_LEVEL))
            .build();
    DirectMappedDexApplication appForMax =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();
    annotateMethodsNotOnLatestAndroidJar(appForMax, builder);
    annotateParallelMethods(builder);
    annotatePartialDesugaringMembers(builder, specification);
    annotateClasses(builder, appForMax);
    return builder.build();
  }

  private void annotateClasses(
      SupportedClasses.Builder builder, DirectMappedDexApplication appForMax) {
    builder.forEachClassFieldsAndMethods(
        (clazz, fields, methods) -> {
          ClassAnnotation classAnnotation = builder.getClassAnnotation(clazz.type);
          if (classAnnotation != null && classAnnotation.isAdditionalMembersOnClass()) {
            return;
          }
          DexClass maxClass = appForMax.definitionFor(clazz.type);
          List<DexField> missingFields = new ArrayList<>();
          List<DexMethod> missingMethods = new ArrayList<>();
          boolean fullySupported = true;
          fullySupported &= analyzeMissingMembers(maxClass.fields(), fields, missingFields);
          fullySupported &= analyzeMissingMembers(maxClass.methods(), methods, missingMethods);
          fullySupported &= builder.getFieldAnnotations(clazz).isEmpty();
          for (MethodAnnotation methodAnnotation : builder.getMethodAnnotations(clazz).values()) {
            fullySupported &= methodAnnotation.isCovariantReturnSupported();
          }
          builder.annotateClass(
              clazz.type, new ClassAnnotation(fullySupported, missingFields, missingMethods));
        });
  }

  private <EM extends DexEncodedMember<EM, M>, M extends DexMember<EM, M>>
      boolean analyzeMissingMembers(
          Iterable<EM> maxClassMembers, Collection<EM> referenceMembers, List<M> missingMembers) {
    boolean fullySupported = true;
    for (EM member : maxClassMembers) {
      if (!(member.getAccessFlags().isPublic() || member.getAccessFlags().isProtected())) {
        continue;
      }
      // If the field is in android.jar but not in the desugared library, then
      // the class is not marked as fully supported.
      if (referenceMembers.stream().noneMatch(em -> em.getReference() == member.getReference())) {
        missingMembers.add(member.getReference());
        fullySupported = false;
      }
    }
    return fullySupported;
  }

  private void annotatePartialDesugaringMembers(
      SupportedClasses.Builder builder, Path specification) throws IOException {
    for (int api = AndroidApiLevel.K.getLevel();
        api <= MAX_TESTED_ANDROID_API_LEVEL.getLevel();
        api++) {
      if (api == 20) {
        // Missing android.jar.
        continue;
      }
      AndroidApiLevel androidApiLevel = AndroidApiLevel.getAndroidApiLevel(api);
      MachineDesugaredLibrarySpecification machineSpecification =
          getMachineSpecification(androidApiLevel, specification);
      options.setMinApiLevel(androidApiLevel);
      options.resetDesugaredLibrarySpecificationForTesting();
      options.setDesugaredLibrarySpecification(machineSpecification);
      AndroidApp library =
          AndroidApp.builder().addProgramFiles(getAndroidJarPath(androidApiLevel)).build();
      DirectMappedDexApplication dexApplication =
          new ApplicationReader(library, options, Timing.empty()).read().toDirect();
      AppInfoWithClassHierarchy appInfo =
          AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
              dexApplication,
              ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
              MainDexInfo.none(),
              GlobalSyntheticsStrategy.forNonSynthesizing(),
              StartupOrder.empty());

      List<DexMethod> backports =
          BackportedMethodRewriter.generateListOfBackportedMethods(dexApplication, options);

      int finalApi = api;
      builder.forEachClassAndMethod(
          (clazz, encodedMethod) -> {
            DexMethod dexMethod = encodedMethod.getReference();
            if (machineSpecification.isSupported(dexMethod)
                || backports.contains(dexMethod)
                || machineSpecification.getCovariantRetarget().containsKey(dexMethod)) {
              if (machineSpecification.getCovariantRetarget().containsKey(dexMethod)) {
                builder.annotateMethod(dexMethod, MethodAnnotation.getCovariantReturnSupported());
              }
              return;
            }
            if (machineSpecification.getEmulatedInterfaces().containsKey(dexMethod.getHolderType())
                && encodedMethod.isStatic()) {
              // Static methods on emulated interfaces are always supported if the emulated
              // interface is supported.
              return;
            }
            MethodResolutionResult methodResolutionResult =
                appInfo.resolveMethod(
                    dexMethod,
                    appInfo
                        .contextIndependentDefinitionFor(dexMethod.getHolderType())
                        .isInterface());
            if (methodResolutionResult.isFailedResolution()) {
              builder.annotateMethod(dexMethod, MethodAnnotation.createMissingInMinApi(finalApi));
            }
          });

      builder.forEachClassAndField(
          (clazz, encodedField) -> {
            if (machineSpecification.isContextTypeMaintainedOrRewritten(
                    encodedField.getHolderType())
                || machineSpecification
                    .getStaticFieldRetarget()
                    .containsKey(encodedField.getReference())) {
              return;
            }
            FieldResolutionResult fieldResolutionResult =
                appInfo.resolveField(encodedField.getReference());
            if (fieldResolutionResult.isFailedResolution()) {
              builder.annotateField(
                  encodedField.getReference(), FieldAnnotation.createMissingInMinApi(finalApi));
            }
          });
    }
  }

  private void annotateParallelMethods(SupportedClasses.Builder builder) {
    for (DexMethod parallelMethod : getParallelMethods()) {
      builder.annotateMethodIfPresent(parallelMethod, MethodAnnotation.getParallelStreamMethod());
    }
  }

  private void annotateMethodsNotOnLatestAndroidJar(
      DirectMappedDexApplication appForMax, SupportedClasses.Builder builder) {
    builder.forEachClassAndMethod(
        (clazz, method) -> {
          DexClass dexClass = appForMax.definitionFor(clazz.type);
          assert dexClass != null;
          if (dexClass.lookupMethod(method.getReference()) == null) {
            builder.annotateMethod(
                method.getReference(), MethodAnnotation.getMissingFromLatestAndroidJar());
          }
        });
  }

  static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    String jar =
        apiLevel == AndroidApiLevel.MASTER
            ? "third_party/android_jar/lib-master/android.jar"
            : String.format(ANDROID_JAR_PATTERN, apiLevel.getLevel());
    return Paths.get(jar);
  }

  private void collectSupportedMembersInB(
      Collection<Path> desugaredLibraryImplementation,
      Path specification,
      SupportedClasses.Builder builder)
      throws IOException {

    MachineDesugaredLibrarySpecification machineSpecification =
        getMachineSpecification(AndroidApiLevel.B, specification);

    options.setMinApiLevel(AndroidApiLevel.B);
    options.resetDesugaredLibrarySpecificationForTesting();
    options.setDesugaredLibrarySpecification(machineSpecification);

    AndroidApp implementation =
        AndroidApp.builder().addProgramFiles(desugaredLibraryImplementation).build();
    DirectMappedDexApplication implementationApplication =
        new ApplicationReader(implementation, options, Timing.empty()).read().toDirect();

    AndroidApp library =
        AndroidApp.builder()
            .addLibraryFiles(getAndroidJarPath(MAX_TESTED_ANDROID_API_LEVEL))
            .build();
    DirectMappedDexApplication amendedAppForMax =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();

    List<DexMethod> backports =
        BackportedMethodRewriter.generateListOfBackportedMethods(amendedAppForMax, options);

    DesugaredLibraryAmender.run(
        machineSpecification.getAmendLibraryMethods(),
        machineSpecification.getAmendLibraryFields(),
        amendedAppForMax,
        options.reporter,
        ComputedApiLevel.unknown());

    for (DexProgramClass clazz : implementationApplication.classes()) {
      // All emulated interfaces static and default methods are supported.
      if (machineSpecification.getEmulatedInterfaces().containsKey(clazz.type)) {
        assert clazz.isInterface();
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isDefaultMethod() && !method.isStatic()) {
            continue;
          }
          if (method.getName().startsWith("lambda$")
              || method.getName().toString().contains("$deserializeLambda$")) {
            // We don't care if lambda methods are present or not.
            continue;
          }
          if (method
              .getReference()
              .toSourceString()
              .equals("void java.util.Collection.forEach(java.util.function.Consumer)")) {
            // This method is present for binary compatibility. Do not mark as supported (Supported
            // through Iterable#forEach).
            continue;
          }
          builder.addSupportedMethod(clazz, method);
        }
        addBackports(clazz, backports, builder, amendedAppForMax);
        builder.annotateClass(clazz.type, ClassAnnotation.getAdditionnalMembersOnClass());
      } else {
        // All methods in maintained or rewritten classes are supported.
        if ((clazz.accessFlags.isPublic() || clazz.accessFlags.isProtected())
            && machineSpecification.isContextTypeMaintainedOrRewritten(clazz.type)
            && amendedAppForMax.definitionFor(clazz.type) != null) {
          for (DexEncodedMethod method : clazz.methods()) {
            if (!method.isPublic() && !method.isProtectedMethod()) {
              continue;
            }
            builder.addSupportedMethod(clazz, method);
          }
          addBackports(clazz, backports, builder, amendedAppForMax);
          for (DexEncodedField field : clazz.fields()) {
            if (!field.isPublic() && !field.isProtected()) {
              continue;
            }
            builder.addSupportedField(clazz, field);
          }
        }
      }
    }

    // All retargeted methods are supported.
    machineSpecification.forEachRetargetMethod(
        method -> {
          DexClass dexClass = implementationApplication.definitionFor(method.getHolderType());
          if (dexClass != null) {
            DexEncodedMethod dexEncodedMethod = dexClass.lookupMethod(method);
            if (dexEncodedMethod != null) {
              builder.addSupportedMethod(dexClass, dexEncodedMethod);
              builder.annotateClass(dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
              return;
            }
          }
          dexClass = amendedAppForMax.definitionFor(method.getHolderType());
          DexEncodedMethod dexEncodedMethod = dexClass.lookupMethod(method);
          assert dexEncodedMethod != null;
          builder.addSupportedMethod(dexClass, dexEncodedMethod);
          builder.annotateClass(dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
        });

    machineSpecification
        .getStaticFieldRetarget()
        .forEach(
            (field, rewritten) -> {
              DexClass dexClass = implementationApplication.definitionFor(field.getHolderType());
              if (dexClass != null) {
                DexEncodedField dexEncodedField = dexClass.lookupField(field);
                if (dexEncodedField != null) {
                  builder.addSupportedField(dexClass, dexEncodedField);
                  builder.annotateClass(
                      dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
                  return;
                }
              }
              dexClass = amendedAppForMax.definitionFor(field.getHolderType());
              DexEncodedField dexEncodedField = dexClass.lookupField(field);
              assert dexEncodedField != null;
              builder.addSupportedField(dexClass, dexEncodedField);
              builder.annotateClass(dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
            });
  }

  private void addBackports(
      DexProgramClass clazz,
      List<DexMethod> backports,
      SupportedClasses.Builder builder,
      DirectMappedDexApplication amendedAppForMax) {
    for (DexMethod backport : backports) {
      if (clazz.type == backport.getHolderType()) {
        DexClass maxClass = amendedAppForMax.definitionFor(clazz.type);
        DexEncodedMethod dexEncodedMethod = maxClass.lookupMethod(backport);
        // Some backports are not in amendedAppForMax, such as Stream#ofNullable and recent ones
        // introduced in U.
        if (dexEncodedMethod == null) {
          ImmutableSet<DexType> allStaticPublicMethods =
              ImmutableSet.of(
                  options.dexItemFactory().mathType,
                  options.dexItemFactory().strictMathType,
                  options.dexItemFactory().objectsType);
          if (backport
                  .toString()
                  .equals(
                      "java.util.stream.Stream"
                          + " java.util.stream.Stream.ofNullable(java.lang.Object)")
              || allStaticPublicMethods.contains(backport.getHolderType())) {
            dexEncodedMethod =
                DexEncodedMethod.builder()
                    .setMethod(backport)
                    .setAccessFlags(
                        MethodAccessFlags.fromSharedAccessFlags(
                            Constants.ACC_PUBLIC | Constants.ACC_STATIC, false))
                    .build();
          } else {
            throw new Error(
                "Unexpected backport missing from Android "
                    + MAX_TESTED_ANDROID_API_LEVEL
                    + ": "
                    + backport);
          }
        }
        builder.addSupportedMethod(clazz, dexEncodedMethod);
      }
    }
  }

  private MachineDesugaredLibrarySpecification getMachineSpecification(
      AndroidApiLevel api, Path specification) throws IOException {
    DesugaredLibrarySpecification librarySpecification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            StringResource.fromFile(specification),
            options.itemFactory,
            options.reporter,
            false,
            api.getLevel());
    Path androidJarPath = getAndroidJarPath(librarySpecification.getRequiredCompilationApiLevel());
    DexApplication app = createLoadingApp(androidJarPath, options);
    return librarySpecification.toMachineSpecification(app, Timing.empty());
  }

  private DexApplication createLoadingApp(Path androidLib, InternalOptions options)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    AndroidApp inputApp = builder.addLibraryFiles(androidLib).build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication loadingApp = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return loadingApp;
  }

  private Set<DexMethod> getParallelMethods() {
    Set<DexMethod> parallelMethods = Sets.newIdentityHashSet();
    DexItemFactory factory = options.dexItemFactory();
    DexType streamType = factory.createType(factory.createString("Ljava/util/stream/Stream;"));
    DexMethod parallelMethod =
        factory.createMethod(
            factory.collectionType,
            factory.createProto(streamType),
            factory.createString("parallelStream"));
    parallelMethods.add(parallelMethod);
    DexType baseStreamType =
        factory.createType(factory.createString("Ljava/util/stream/BaseStream;"));
    for (String typePrefix : new String[] {"Base", "Double", "Int", "Long"}) {
      streamType =
          factory.createType(factory.createString("Ljava/util/stream/" + typePrefix + "Stream;"));
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(streamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
      // Also filter out the generated bridges for the covariant return type.
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(baseStreamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
    }
    return parallelMethods;
  }
}
