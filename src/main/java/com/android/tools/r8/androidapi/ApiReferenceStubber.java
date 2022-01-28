// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowExceptionCode;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ApiReferenceStubber {

  private class ReferencesToApiLevelUseRegistry extends UseRegistry<ProgramMethod> {

    public ReferencesToApiLevelUseRegistry(ProgramMethod context) {
      super(appView, context);
    }

    @Override
    public void registerInitClass(DexType type) {
      checkReferenceToLibraryClass(type);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      checkReferenceToLibraryClass(method);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      checkReferenceToLibraryClass(field.type);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      checkReferenceToLibraryClass(field.type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      checkReferenceToLibraryClass(field.type);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      checkReferenceToLibraryClass(field.type);
    }

    @Override
    public void registerTypeReference(DexType type) {
      checkReferenceToLibraryClass(type);
    }

    private void checkReferenceToLibraryClass(DexReference reference) {
      DexType rewrittenType = appView.graphLens().lookupType(reference.getContextType());
      findReferencedLibraryClasses(rewrittenType);
      if (reference.isDexMethod()) {
        findReferencedLibraryMethod(reference.asDexMethod());
      }
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Map<DexLibraryClass, Set<DexMethod>> libraryClassesToMock =
      new ConcurrentHashMap<>();
  private final Set<DexType> seenTypes = Sets.newConcurrentHashSet();
  private final AndroidApiLevelCompute apiLevelCompute;
  private final LegacyDesugaredLibrarySpecification desugaredLibraryConfiguration;

  public ApiReferenceStubber(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    apiLevelCompute = appView.apiLevelCompute();
    desugaredLibraryConfiguration = appView.options().desugaredLibrarySpecification;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().isGeneratingClassFiles()
        || !appView.options().apiModelingOptions().enableStubbingOfClasses) {
      return;
    }
    ThreadUtils.processItems(appView.appInfo().classes(), this::processClass, executorService);
    if (libraryClassesToMock.isEmpty()) {
      return;
    }
    libraryClassesToMock.forEach(
        (clazz, methods) ->
            mockMissingLibraryClass(
                clazz,
                methods,
                ThrowExceptionCode.create(appView.dexItemFactory().noClassDefFoundErrorType)));
    // Commit the synthetic items.
    CommittedItems committedItems = appView.getSyntheticItems().commit(appView.appInfo().app());
    if (appView.hasLiveness()) {
      AppView<AppInfoWithLiveness> appInfoWithLivenessAppView = appView.withLiveness();
      appInfoWithLivenessAppView.setAppInfo(
          appInfoWithLivenessAppView.appInfo().rebuildWithLiveness(committedItems));
    } else {
      appView
          .withClassHierarchy()
          .setAppInfo(appView.appInfo().rebuildWithClassHierarchy(committedItems));
    }
  }

  public void processClass(DexProgramClass clazz) {
    if (appView
        .getSyntheticItems()
        .isSyntheticOfKind(clazz.getType(), SyntheticKind.API_MODEL_OUTLINE)) {
      return;
    }
    findReferencedLibraryClasses(clazz.type);
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> method.registerCodeReferences(new ReferencesToApiLevelUseRegistry(method)));
  }

  private void findReferencedLibraryMethod(DexMethod method) {
    DexType holderType = method.getHolderType();
    if (!holderType.isClassType()) {
      return;
    }
    DexType rewrittenType = appView.graphLens().lookupType(holderType);
    DexClass clazz = appView.definitionFor(rewrittenType);
    if (clazz == null || !clazz.isLibraryClass()) {
      return;
    }
    ComputedApiLevel apiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(method, ComputedApiLevel.unknown());
    if (apiLevel.isGreaterThan(appView.computedMinApiLevel())) {
      ComputedApiLevel holderApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(
              rewrittenType, ComputedApiLevel.unknown());
      if (holderApiLevel.isUnknownApiLevel()) {
        // Do not mock methods or classes where the holder is unknown.
        return;
      }
      if (holderApiLevel.isGreaterThan(appView.computedMinApiLevel())) {
        libraryClassesToMock
            .computeIfAbsent(clazz.asLibraryClass(), ignored -> Sets.newConcurrentHashSet())
            .add(method);
      }
    }
  }

  private void findReferencedLibraryClasses(DexType type) {
    if (!type.isClassType()) {
      return;
    }
    WorkList<DexType> workList = WorkList.newIdentityWorkList(type, seenTypes);
    while (workList.hasNext()) {
      DexClass clazz = appView.definitionFor(workList.next());
      if (clazz == null) {
        continue;
      }
      if (clazz.isLibraryClass()) {
        ComputedApiLevel androidApiLevel =
            apiLevelCompute.computeApiLevelForLibraryReference(
                clazz.type, ComputedApiLevel.unknown());
        if (androidApiLevel.isGreaterThan(appView.computedMinApiLevel())
            && !androidApiLevel.isUnknownApiLevel()) {
          libraryClassesToMock.computeIfAbsent(
              clazz.asLibraryClass(), ignored -> Sets.newConcurrentHashSet());
        }
      }
      workList.addIfNotSeen(clazz.allImmediateSupertypes());
    }
  }

  private void mockMissingLibraryClass(
      DexLibraryClass libraryClass,
      Set<DexMethod> methodsToStub,
      ThrowExceptionCode throwExceptionCode) {
    if (libraryClass.getType() == appView.dexItemFactory().objectType
        || libraryClass.getType().toDescriptorString().startsWith("Ljava/")) {
      return;
    }
    if (desugaredLibraryConfiguration.isSupported(libraryClass.getType(), appView)) {
      return;
    }
    appView
        .appInfo()
        .getSyntheticItems()
        .addSyntheticClassWithLibraryContext(
            appView,
            libraryClass,
            SyntheticKind.API_MODEL_STUB,
            libraryClass.getType(),
            classBuilder -> {
              classBuilder
                  .setSuperType(libraryClass.getSuperType())
                  .setInterfaces(Arrays.asList(libraryClass.getInterfaces().values))
                  .setVirtualMethods(
                      buildLibraryMethodsForProgram(
                          libraryClass, libraryClass.virtualMethods(), methodsToStub));
              // Based on b/138781768#comment57 there is no significant reason to synthesize fields.
              if (libraryClass.isInterface()) {
                classBuilder.setInterface();
              }
              if (!libraryClass.isFinal()) {
                classBuilder.unsetFinal();
              }
              List<DexEncodedMethod> directMethods =
                  (!libraryClass.isInterface()
                          || appView.options().canUseDefaultAndStaticInterfaceMethods())
                      ? buildLibraryMethodsForProgram(
                          libraryClass, libraryClass.directMethods(), methodsToStub)
                      : new ArrayList<>();
              // Add throwing static initializer
              directMethods.add(
                  DexEncodedMethod.syntheticBuilder()
                      .setMethod(
                          appView.dexItemFactory().createClassInitializer(libraryClass.getType()))
                      .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                      .setCode(throwExceptionCode)
                      .build());
              classBuilder.setDirectMethods(directMethods);
            });
  }

  private List<DexEncodedMethod> buildLibraryMethodsForProgram(
      DexLibraryClass clazz, Iterable<DexEncodedMethod> methods, Set<DexMethod> methodsToMock) {
    List<DexEncodedMethod> newMethods = new ArrayList<>();
    methods.forEach(
        method -> {
          if (methodsToMock.contains(method.getReference())) {
            DexEncodedMethod newMethod = buildLibraryMethodForProgram(clazz, method);
            if (newMethod != null) {
              newMethods.add(newMethod);
            }
          }
        });
    return newMethods;
  }

  private DexEncodedMethod buildLibraryMethodForProgram(
      DexLibraryClass clazz, DexEncodedMethod method) {
    assert !clazz.isInterface()
        || !method.isStatic()
        || appView.options().canUseDefaultAndStaticInterfaceMethods();
    DexMethod newMethod = method.getReference().withHolder(clazz.type, appView.dexItemFactory());
    DexEncodedMethod.Builder methodBuilder =
        DexEncodedMethod.syntheticBuilder(method)
            .setMethod(newMethod)
            .modifyAccessFlags(MethodAccessFlags::setSynthetic);
    if (method.isInstanceInitializer()) {
      methodBuilder.setCode(DefaultInstanceInitializerCode.get());
    } else if (method.isVirtualMethod() && clazz.isInterface()) {
      methodBuilder.modifyAccessFlags(MethodAccessFlags::setAbstract);
    } else if (method.isAbstract()) {
      methodBuilder.modifyAccessFlags(MethodAccessFlags::setAbstract);
    } else {
      // To allow us not adding a trivial throwing code body we set the access flag as native.
      methodBuilder.modifyAccessFlags(MethodAccessFlags::setNative);
    }
    return methodBuilder.build();
  }
}
