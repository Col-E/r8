// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.lint.AbstractGenerateFiles.MAX_TESTED_ANDROID_API_LEVEL;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
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
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.ClassAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.FieldAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.SupportedClasses.MethodAnnotation;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class SupportedClassesGenerator {

  private final InternalOptions options;
  private final DirectMappedDexApplication appForMax;
  private final AndroidApiLevel minApi;
  private final SupportedClasses.Builder builder = SupportedClasses.builder();
  private final boolean androidPlatformBuild;
  private final boolean addBackports;

  public SupportedClassesGenerator(
      InternalOptions options, Collection<ClassFileResourceProvider> androidJar)
      throws IOException {
    this(options, androidJar, AndroidApiLevel.B, false, false);
  }

  public SupportedClassesGenerator(
      InternalOptions options,
      Collection<ClassFileResourceProvider> androidJar,
      AndroidApiLevel minApi,
      boolean androidPlatformBuild,
      boolean addBackports)
      throws IOException {
    this.options = options;
    this.appForMax = createAppForMax(androidJar);
    this.minApi = minApi;
    this.androidPlatformBuild = androidPlatformBuild;
    this.addBackports = addBackports;
  }

  public SupportedClasses run(
      Collection<ProgramResourceProvider> desugaredLibraryImplementation,
      StringResource specification)
      throws IOException {
    // First analyze everything which is supported when desugaring for api 1.
    collectSupportedMembersInMinApi(desugaredLibraryImplementation, specification);
    // Second annotate all apis which are partially and/or fully supported.
    annotateMethodsNotOnLatestAndroidJar();
    annotateParallelMethods();
    annotatePartialDesugaringMembers(specification);
    annotateClasses();
    return builder.build();
  }

  private void annotateClasses() {
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
      @SuppressWarnings("ReferenceEquality") boolean analyzeMissingMembers(
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

  private void annotatePartialDesugaringMembers(StringResource specification) throws IOException {
    if (builder.hasOnlyExtraMethods()) {
      return;
    }
    // The first difference should be at 18 so we're safe starting at J and not B.
    for (int api = AndroidApiLevel.J.getLevel();
        api <= MAX_TESTED_ANDROID_API_LEVEL.getLevel();
        api++) {
      AndroidApiLevel androidApiLevel = AndroidApiLevel.getAndroidApiLevel(api);
      MachineDesugaredLibrarySpecification machineSpecification =
          getMachineSpecification(androidApiLevel, specification);
      options.setMinApiLevel(androidApiLevel);
      options.resetDesugaredLibrarySpecificationForTesting();
      options.setDesugaredLibrarySpecification(machineSpecification);

      AppInfo initialAppInfo =
          AppInfo.createInitialAppInfo(appForMax, GlobalSyntheticsStrategy.forNonSynthesizing());
      AppView<?> appView =
          AppView.createForD8(initialAppInfo, options.getTypeRewriter(), Timing.empty());
      AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();

      // This should depend only on machine specification and min api.
      List<DexMethod> backports = generateListOfBackportedMethods();

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
            // This is not supported through desugared library. We look-up to see if it is
            // supported at the given min-api level by the library.
            MethodResolutionResult methodResolutionResult =
                appInfo.resolveMethod(
                    dexMethod,
                    appInfo
                        .contextIndependentDefinitionFor(dexMethod.getHolderType())
                        .isInterface());
            if (methodResolutionResult.isSingleResolution()) {
              ComputedApiLevel computedApiLevel =
                  appView
                      .apiLevelCompute()
                      .computeApiLevelForLibraryReferenceIgnoringDesugaredLibrary(
                          methodResolutionResult.getResolvedMethod().getReference(),
                          ComputedApiLevel.unknown());
              if (!computedApiLevel.isKnownApiLevel()) {
                throw new RuntimeException(
                    "API database does not recognize the method "
                        + encodedMethod.getReference().toSourceString());
              }
              if (finalApi < computedApiLevel.asKnownApiLevel().getApiLevel().getLevel()) {
                builder.annotateMethod(dexMethod, MethodAnnotation.createMissingInMinApi(finalApi));
              }
            } else {
              assert methodResolutionResult.isFailedResolution();
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
            if (fieldResolutionResult.isSingleFieldResolutionResult()) {
              DexEncodedField resolvedField = fieldResolutionResult.getResolvedField();
              ComputedApiLevel computedApiLevel =
                  appView
                      .apiLevelCompute()
                      .computeApiLevelForLibraryReferenceIgnoringDesugaredLibrary(
                          resolvedField.getReference(), ComputedApiLevel.unknown());
              if (!computedApiLevel.isKnownApiLevel()) {
                throw new RuntimeException(
                    "API database does not recognize the field "
                        + encodedField.getReference().toSourceString());
              }
              if (finalApi < computedApiLevel.asKnownApiLevel().getApiLevel().getLevel()) {
                builder.annotateField(
                    encodedField.getReference(), FieldAnnotation.createMissingInMinApi(finalApi));
              }
            } else {
              assert fieldResolutionResult.isFailedResolution();
              builder.annotateField(
                  encodedField.getReference(), FieldAnnotation.createMissingInMinApi(finalApi));
            }
          });
    }
  }

  private void annotateParallelMethods() {
    for (DexMethod parallelMethod : getParallelMethods()) {
      builder.annotateMethodIfPresent(parallelMethod, MethodAnnotation.getParallelStreamMethod());
    }
  }

  private void annotateMethodsNotOnLatestAndroidJar() {
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

  private void collectSupportedMembersInMinApi(
      Collection<ProgramResourceProvider> desugaredLibraryImplementation,
      StringResource specification)
      throws IOException {

    MachineDesugaredLibrarySpecification machineSpecification =
        getMachineSpecification(minApi, specification);

    options.setMinApiLevel(minApi);
    options.resetDesugaredLibrarySpecificationForTesting();
    options.setDesugaredLibrarySpecification(machineSpecification);

    AndroidApp.Builder appBuilder = AndroidApp.builder();
    for (ProgramResourceProvider programResource : desugaredLibraryImplementation) {
      appBuilder.addProgramResourceProvider(programResource);
    }
    AndroidApp implementation = appBuilder.build();
    DirectMappedDexApplication implementationApplication =
        new ApplicationReader(implementation, options, Timing.empty()).read().toDirect();

    List<DexMethod> backports = generateListOfBackportedMethods();

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
        addBackports(clazz, backports);
        builder.annotateClass(clazz.type, ClassAnnotation.getAdditionnalMembersOnClass());
      } else {
        // All methods in maintained or rewritten classes are supported.
        if ((clazz.accessFlags.isPublic() || clazz.accessFlags.isProtected())
            && machineSpecification.isContextTypeMaintainedOrRewritten(clazz.type)
            && appForMax.definitionFor(clazz.type) != null) {
          for (DexEncodedMethod method : clazz.methods()) {
            if (!method.isPublic() && !method.isProtectedMethod()) {
              continue;
            }
            builder.addSupportedMethod(clazz, method);
          }
          for (DexEncodedField field : clazz.fields()) {
            if (!field.isPublic() && !field.isProtected()) {
              continue;
            }
            builder.addSupportedField(clazz, field);
          }
        }
        addBackports(clazz, backports);
      }
    }

    // All retargeted methods are supported.
    machineSpecification.forEachRetargetMethod(
        method -> registerMethod(method, implementationApplication, backports));

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
              dexClass = appForMax.definitionFor(field.getHolderType());
              DexEncodedField dexEncodedField = dexClass.lookupField(field);
              assert dexEncodedField != null;
              builder.addSupportedField(dexClass, dexEncodedField);
              builder.annotateClass(dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
            });

    if (addBackports) {
      List<DexMethod> extraMethods = new ArrayList<>();
      for (DexMethod backport : backports) {
        if (implementationApplication.definitionFor(backport.getHolderType()) == null) {
          extraMethods.add(backport);
        }
      }
      extraMethods.sort(Comparator.naturalOrder());
      builder.setExtraMethods(extraMethods);
    }
  }

  private List<DexMethod> generateListOfBackportedMethods() throws IOException {
    if (androidPlatformBuild) {
      return ImmutableList.of();
    }
    return BackportedMethodRewriter.generateListOfBackportedMethods(appForMax, options);
  }

  private void registerMethod(
      DexMethod method, DexApplication implementationApplication, List<DexMethod> backports) {
    DexClass dexClass = implementationApplication.definitionFor(method.getHolderType());
    if (dexClass != null) {
      DexEncodedMethod dexEncodedMethod = dexClass.lookupMethod(method);
      if (dexEncodedMethod != null) {
        builder.addSupportedMethod(dexClass, dexEncodedMethod);
        builder.annotateClass(dexClass.type, ClassAnnotation.getAdditionnalMembersOnClass());
        return;
      }
    }
    dexClass = appForMax.definitionFor(method.getHolderType());
    DexEncodedMethod dexEncodedMethod = lookupBackportMethod(dexClass, method);
    if (dexEncodedMethod != null) {
      builder.addSupportedMethod(dexClass, dexEncodedMethod);
      builder.annotateClass(dexClass.getType(), ClassAnnotation.getAdditionnalMembersOnClass());
      backports.remove(method);
    }
  }

  private DexEncodedMethod lookupBackportMethod(DexClass maxClass, DexMethod backport) {
    if (maxClass == null) {
      throw new Error(
          "Missing class from Android "
              + MAX_TESTED_ANDROID_API_LEVEL
              + ": "
              + backport.getHolderType());
    }
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
    assert dexEncodedMethod != null;
    return dexEncodedMethod;
  }

  @SuppressWarnings("ReferenceEquality")
  private void addBackports(DexProgramClass clazz, List<DexMethod> backports) {
    for (DexMethod backport : backports) {
      if (clazz.type == backport.getHolderType()) {
        DexClass maxClass = appForMax.definitionFor(clazz.type);
        DexEncodedMethod dexEncodedMethod = lookupBackportMethod(maxClass, backport);
        builder.addSupportedMethod(clazz, dexEncodedMethod);
      }
    }
  }

  private MachineDesugaredLibrarySpecification getMachineSpecification(
      AndroidApiLevel api, StringResource specification) throws IOException {
    if (specification == null) {
      return MachineDesugaredLibrarySpecification.empty();
    }
    DesugaredLibrarySpecification librarySpecification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            specification, options.itemFactory, options.reporter, false, api.getLevel());
    return librarySpecification.toMachineSpecification(appForMax, Timing.empty());
  }

  private DirectMappedDexApplication createAppForMax(
      Collection<ClassFileResourceProvider> androidJar) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassFileResourceProvider libraryResource : androidJar) {
      builder.addLibraryResourceProvider(libraryResource);
    }
    AndroidApp inputApp = builder.build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication appForMax = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return appForMax.toDirect();
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
