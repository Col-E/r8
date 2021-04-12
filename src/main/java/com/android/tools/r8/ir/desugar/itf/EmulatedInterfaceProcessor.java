// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
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
  // All created emulated interface classes indexed by emulated interface type.
  final Map<DexProgramClass, DexProgramClass> syntheticClasses = new IdentityHashMap<>();

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

  // The method transforms emulated interface such as they implement the rewritten version
  // of each emulated interface they implement. Such change should have no effect on the look-up
  // results, since each class implementing an emulated interface should also implement the
  // rewritten one.
  private void replaceInterfacesInEmulatedInterface(DexProgramClass emulatedInterface) {
    List<GenericSignature.ClassTypeSignature> newInterfaces = new ArrayList<>();
    ClassSignature classSignature = emulatedInterface.getClassSignature();
    for (int i = 0; i < emulatedInterface.interfaces.size(); i++) {
      DexType itf = emulatedInterface.interfaces.values[i];
      if (emulatedInterfaces.containsKey(itf)) {
        List<GenericSignature.FieldTypeSignature> typeArguments;
        if (classSignature == null) {
          typeArguments = Collections.emptyList();
        } else {
          GenericSignature.ClassTypeSignature classTypeSignature =
              classSignature.superInterfaceSignatures().get(i);
          assert itf == classTypeSignature.type();
          typeArguments = classTypeSignature.typeArguments();
        }
        newInterfaces.add(
            new GenericSignature.ClassTypeSignature(emulatedInterfaces.get(itf), typeArguments));
      }
    }
    emulatedInterface.replaceInterfaces(newInterfaces);
  }

  private void renameEmulatedInterface(DexProgramClass emulatedInterface) {
    DexType newType = emulatedInterfaces.get(emulatedInterface.type);
    assert newType != null;
    emulatedInterface.type = newType;
    emulatedInterface.setVirtualMethods(renameHolder(emulatedInterface.virtualMethods(), newType));
    emulatedInterface.setDirectMethods(renameHolder(emulatedInterface.directMethods(), newType));
  }

  private DexEncodedMethod[] renameHolder(Iterable<DexEncodedMethod> methods, DexType newName) {
    List<DexEncodedMethod> methods1 = IterableUtils.toNewArrayList(methods);
    DexEncodedMethod[] newMethods = new DexEncodedMethod[methods1.size()];
    for (int i = 0; i < newMethods.length; i++) {
      newMethods[i] = methods1.get(i).toRenamedHolderMethod(newName, appView.dexItemFactory());
    }
    return newMethods;
  }

  void generateEmulateInterfaceLibrary(DexProgramClass emulatedInterface) {
    assert rewriter.isEmulatedInterface(emulatedInterface.type);
    DexProgramClass theProgramInterface = emulatedInterface.asProgramClass();
    DexProgramClass synthesizedClass = synthesizeEmulateInterfaceLibraryClass(theProgramInterface);
    if (synthesizedClass != null) {
      syntheticClasses.put(emulatedInterface, synthesizedClass);
    }
  }

  private DexProgramClass synthesizeEmulateInterfaceLibraryClass(DexProgramClass theInterface) {
    List<DexEncodedMethod> emulationMethods = new ArrayList<>();
    theInterface.forEachProgramMethodMatching(
        DexEncodedMethod::isDefaultMethod,
        method -> {
          DexMethod libraryMethod =
              method
                  .getReference()
                  .withHolder(emulatedInterfaces.get(theInterface.type), appView.dexItemFactory());
          DexMethod companionMethod =
              method.getAccessFlags().isStatic()
                  ? rewriter.staticAsMethodOfCompanionClass(method)
                  : rewriter.defaultAsMethodOfCompanionClass(method);
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
                                      .protoWithDifferentFirstParameter(
                                          companionMethod.proto, inType),
                                  method.getName())));
                }
              }
            }
          }
          if (subInterfaces != null) {
            for (int i = subInterfaces.size() - 1; i >= 0; i--) {
              DexClass subInterfaceClass = appView.definitionFor(subInterfaces.get(i));
              assert subInterfaceClass != null;
              // Else computation of subInterface would have failed.
              // if the method is implemented, extra dispatch is required.
              DexEncodedMethod result =
                  subInterfaceClass.lookupVirtualMethod(method.getReference());
              if (result != null && !result.isAbstract()) {
                extraDispatchCases.add(
                    new Pair<>(
                        subInterfaceClass.type,
                        appView
                            .dexItemFactory()
                            .createMethod(
                                rewriter.getCompanionClassType(subInterfaceClass.type),
                                appView
                                    .dexItemFactory()
                                    .protoWithDifferentFirstParameter(
                                        companionMethod.proto, subInterfaceClass.type),
                                companionMethod.name)));
              }
            }
          }
          emulationMethods.add(
              DexEncodedMethod.toEmulateDispatchLibraryMethod(
                  method.getHolderType(),
                  rewriter.emulateInterfaceLibraryMethod(method),
                  companionMethod,
                  libraryMethod,
                  extraDispatchCases,
                  appView));
        });
    if (emulationMethods.isEmpty()) {
      return null;
    }
    DexType emulateLibraryClassType =
        InterfaceMethodRewriter.getEmulateLibraryInterfaceClassType(
            theInterface.type, appView.dexItemFactory());
    ClassAccessFlags emulateLibraryClassFlags = theInterface.accessFlags.copy();
    emulateLibraryClassFlags.unsetAbstract();
    emulateLibraryClassFlags.unsetInterface();
    emulateLibraryClassFlags.unsetAnnotation();
    emulateLibraryClassFlags.setFinal();
    emulateLibraryClassFlags.setSynthetic();
    emulateLibraryClassFlags.setPublic();
    DexProgramClass clazz =
        new DexProgramClass(
            emulateLibraryClassType,
            null,
            new SynthesizedOrigin("interface desugaring (libs)", getClass()),
            emulateLibraryClassFlags,
            appView.dexItemFactory().objectType,
            DexTypeList.empty(),
            theInterface.sourceFile,
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            // All synthesized methods are static in this case.
            emulationMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
            DexEncodedMethod.EMPTY_ARRAY,
            appView.dexItemFactory().getSkipNameValidationForTesting(),
            DexProgramClass::checksumFromType);
    return clazz;
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
    generateEmulateInterfaceLibrary(emulatedInterface);
  }

  @Override
  public void finalizeProcessing(Builder<?> builder, ProgramMethodSet synthesizedMethods) {
    warnMissingEmulatedInterfaces();
    if (!appView.options().isDesugaredLibraryCompilation()) {
      assert syntheticClasses.isEmpty();
      return;
    }
    for (DexType interfaceType : emulatedInterfaces.keySet()) {
      DexClass theInterface = appView.definitionFor(interfaceType);
      if (theInterface != null && theInterface.isProgramClass()) {
        DexProgramClass emulatedInterface = theInterface.asProgramClass();
        if (!appView.isAlreadyLibraryDesugared(emulatedInterface)) {
          replaceInterfacesInEmulatedInterface(emulatedInterface);
          renameEmulatedInterface(emulatedInterface);
        }
      }
    }
    syntheticClasses.forEach(
        (interfaceClass, synthesizedClass) -> {
          builder.addSynthesizedClass(synthesizedClass);
          appView.appInfo().addSynthesizedClass(synthesizedClass, interfaceClass);
          synthesizedClass.forEachProgramMethod(synthesizedMethods::add);
        });
    // TODO(b/183918843): Investigate what to do for the filtering, the minimum would be to make
    // the rewriting rule explicit instead of using the synthesized class prefix.
    filterEmulatedInterfaceSubInterfaces(builder);
  }

  private void filterEmulatedInterfaceSubInterfaces(Builder<?> builder) {
    ArrayList<DexProgramClass> filteredProgramClasses = new ArrayList<>();
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (clazz.isInterface()
          && !rewriter.isEmulatedInterface(clazz.type)
          && !appView.rewritePrefix.hasRewrittenType(clazz.type, appView)
          && isEmulatedInterfaceSubInterface(clazz)) {
        String prefix =
            DescriptorUtils.getJavaTypeFromBinaryName(
                appView
                    .options()
                    .desugaredLibraryConfiguration
                    .getSynthesizedLibraryClassesPackagePrefix());
        String interfaceType = clazz.type.toString();
        // TODO(b/183918843): We are currently computing a new name for the companion class
        // by replacing the initial package prefix by the synthesized library class package
        // prefix, it would be better to make the rewriting explicit in the desugared library
        // json file.
        int firstPackage = interfaceType.indexOf('.');
        String newName = prefix + interfaceType.substring(firstPackage + 1);
        rewriter.addCompanionClassRewriteRule(clazz.type, newName);
      } else {
        filteredProgramClasses.add(clazz);
      }
    }
    builder.replaceProgramClasses(filteredProgramClasses);
  }

  private boolean isEmulatedInterfaceSubInterface(DexClass subInterface) {
    assert !rewriter.isEmulatedInterface(subInterface.type);
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(subInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeFirst();
      if (rewriter.isEmulatedInterface(next)) {
        return true;
      }
      DexClass nextClass = appView.definitionFor(next);
      if (nextClass != null) {
        workList.addAll(Arrays.asList(nextClass.interfaces.values));
      }
    }
    return false;
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
