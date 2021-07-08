// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.emulateInterfaceLibraryMethod;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.synthetic.EmulateInterfaceSyntheticCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmulatedInterfaceProcessor implements InterfaceDesugaringProcessor {

  private final AppView<?> appView;
  private final InterfaceMethodRewriter rewriter;
  private final Map<DexType, DexType> emulatedInterfaces;
  private final Map<DexType, List<DexType>> emulatedInterfacesHierarchy;

  EmulatedInterfaceProcessor(AppView<?> appView, InterfaceMethodRewriter rewriter) {
    this.appView = appView;
    this.rewriter = rewriter;
    emulatedInterfaces =
        appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();
    // Avoid the computation outside L8 since it is not needed.
    emulatedInterfacesHierarchy =
        appView.options().isDesugaredLibraryCompilation()
            ? processEmulatedInterfaceHierarchy()
            : Collections.emptyMap();
  }

  private Map<DexType, List<DexType>> processEmulatedInterfaceHierarchy() {
    Map<DexType, List<DexType>> emulatedInterfacesHierarchy = new IdentityHashMap<>();
    Set<DexType> processed = Sets.newIdentityHashSet();
    ArrayList<DexType> emulatedInterfacesSorted = new ArrayList<>(emulatedInterfaces.keySet());
    emulatedInterfacesSorted.sort(DexType::compareTo);
    for (DexType interfaceType : emulatedInterfacesSorted) {
      processEmulatedInterfaceHierarchy(interfaceType, processed, emulatedInterfacesHierarchy);
    }
    return emulatedInterfacesHierarchy;
  }

  private void processEmulatedInterfaceHierarchy(
      DexType interfaceType,
      Set<DexType> processed,
      Map<DexType, List<DexType>> emulatedInterfacesHierarchy) {
    if (processed.contains(interfaceType)) {
      return;
    }
    emulatedInterfacesHierarchy.put(interfaceType, new ArrayList<>());
    processed.add(interfaceType);
    DexClass theInterface = appView.definitionFor(interfaceType);
    if (theInterface == null) {
      return;
    }
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(theInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeLast();
      if (emulatedInterfaces.containsKey(next)) {
        processEmulatedInterfaceHierarchy(next, processed, emulatedInterfacesHierarchy);
        emulatedInterfacesHierarchy.get(next).add(interfaceType);
        DexClass nextClass = appView.definitionFor(next);
        if (nextClass != null) {
          workList.addAll(Arrays.asList(nextClass.interfaces.values));
        }
      }
    }
  }

  DexProgramClass ensureEmulateInterfaceLibrary(
      DexProgramClass emulatedInterface, ProgramMethodSet synthesizedMethods) {
    assert rewriter.isEmulatedInterface(emulatedInterface.type);
    DexProgramClass emulateInterfaceClass =
        appView
            .getSyntheticItems()
            .ensureFixedClass(
                SyntheticNaming.SyntheticKind.EMULATED_INTERFACE_CLASS,
                emulatedInterface,
                appView,
                builder ->
                    emulatedInterface.forEachProgramMethodMatching(
                        DexEncodedMethod::isDefaultMethod,
                        method ->
                            builder.addMethod(
                                methodBuilder ->
                                    synthesizeEmulatedInterfaceMethod(
                                        method, emulatedInterface, methodBuilder))),
                ignored -> {});
    emulateInterfaceClass.forEachProgramMethod(synthesizedMethods::add);
    assert emulateInterfaceClass.getType()
        == InterfaceMethodRewriter.getEmulateLibraryInterfaceClassType(
            emulatedInterface.type, appView.dexItemFactory());
    return emulateInterfaceClass;
  }

  private void synthesizeEmulatedInterfaceMethod(
      ProgramMethod method, DexProgramClass theInterface, SyntheticMethodBuilder methodBuilder) {
    assert !method.getDefinition().isStatic();
    DexMethod libraryMethod =
        method
            .getReference()
            .withHolder(emulatedInterfaces.get(theInterface.type), appView.dexItemFactory());
    DexMethod companionMethod =
        rewriter.ensureDefaultAsMethodOfProgramCompanionClassStub(method).getReference();
    List<Pair<DexType, DexMethod>> extraDispatchCases =
        getDispatchCases(method, theInterface, companionMethod);
    DexMethod emulatedMethod = emulateInterfaceLibraryMethod(method, appView.dexItemFactory());
    methodBuilder
        .setName(emulatedMethod.getName())
        .setProto(emulatedMethod.getProto())
        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
        .setCode(
            theMethod ->
                new EmulateInterfaceSyntheticCfCodeProvider(
                        theMethod.getHolderType(),
                        companionMethod,
                        libraryMethod,
                        extraDispatchCases,
                        appView)
                    .generateCfCode());
  }

  private List<Pair<DexType, DexMethod>> getDispatchCases(
      ProgramMethod method, DexProgramClass theInterface, DexMethod companionMethod) {
    // To properly emulate the library interface call, we need to compute the interfaces
    // inheriting from the interface and manually implement the dispatch with instance of.
    // The list guarantees that an interface is always after interfaces it extends,
    // hence reverse iteration.
    List<DexType> subInterfaces = emulatedInterfacesHierarchy.get(theInterface.type);
    List<Pair<DexType, DexMethod>> extraDispatchCases = new ArrayList<>();
    // In practice, there is usually a single case (except for tests),
    // so we do not bother to make the following loop more clever.
    Map<DexString, Map<DexType, DexType>> retargetCoreLibMember =
        appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember();
    for (DexString methodName : retargetCoreLibMember.keySet()) {
      if (method.getName() == methodName) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass inClass = appView.definitionFor(inType);
          if (inClass != null && implementsInterface(inClass, theInterface.type)) {
            extraDispatchCases.add(
                new Pair<>(
                    inType,
                    appView
                        .dexItemFactory()
                        .createMethod(
                            retargetCoreLibMember.get(methodName).get(inType),
                            appView
                                .dexItemFactory()
                                .protoWithDifferentFirstParameter(companionMethod.proto, inType),
                            method.getName())));
          }
        }
      }
    }
    if (subInterfaces != null) {
      for (int i = subInterfaces.size() - 1; i >= 0; i--) {
        DexClass subInterfaceClass = appView.definitionFor(subInterfaces.get(i));
        assert subInterfaceClass != null;
        assert subInterfaceClass.isProgramClass();
        // Else computation of subInterface would have failed.
        // if the method is implemented, extra dispatch is required.
        DexEncodedMethod result = subInterfaceClass.lookupVirtualMethod(method.getReference());
        if (result != null && !result.isAbstract()) {
          assert result.isDefaultMethod();
          extraDispatchCases.add(
              new Pair<>(
                  subInterfaceClass.type,
                  InterfaceMethodRewriter.defaultAsMethodOfCompanionClass(
                      result.getReference(), appView.dexItemFactory())));
        }
      }
    } else {
      assert extraDispatchCases.size() <= 1;
    }
    return extraDispatchCases;
  }

  private boolean implementsInterface(DexClass clazz, DexType interfaceType) {
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(clazz.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeLast();
      if (interfaceType == next) {
        return true;
      }
      DexClass nextClass = appView.definitionFor(next);
      if (nextClass != null) {
        workList.addAll(Arrays.asList(nextClass.interfaces.values));
      }
    }
    return false;
  }

  @Override
  public void process(DexProgramClass emulatedInterface, ProgramMethodSet synthesizedMethods) {
    if (!appView.options().isDesugaredLibraryCompilation()
        || !rewriter.isEmulatedInterface(emulatedInterface.type)
        || appView.isAlreadyLibraryDesugared(emulatedInterface)) {
      return;
    }
    if (needsEmulateInterfaceLibrary(emulatedInterface)) {
      ensureEmulateInterfaceLibrary(emulatedInterface, synthesizedMethods);
    }
  }

  private boolean needsEmulateInterfaceLibrary(DexProgramClass emulatedInterface) {
    return Iterables.any(emulatedInterface.methods(), DexEncodedMethod::isDefaultMethod);
  }

  @Override
  public void finalizeProcessing(ProgramMethodSet synthesizedMethods) {
    warnMissingEmulatedInterfaces();
  }

  private void warnMissingEmulatedInterfaces() {
    for (DexType interfaceType : emulatedInterfaces.keySet()) {
      DexClass theInterface = appView.definitionFor(interfaceType);
      if (theInterface == null) {
        warnMissingEmulatedInterface(interfaceType);
      }
    }
  }

  private void warnMissingEmulatedInterface(DexType interfaceType) {
    StringDiagnostic warning =
        new StringDiagnostic(
            "Cannot emulate interface "
                + interfaceType.getName()
                + " because the interface is missing.");
    appView.options().reporter.warning(warning);
  }
}
