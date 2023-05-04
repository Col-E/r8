// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterPostProcessingEventConsumer;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

// The rewrite of virtual calls requires to go through emulate dispatch. This class is responsible
// for inserting interfaces on library boundaries and forwarding methods in the program, and to
// synthesize the interfaces and emulated dispatch classes in the desugared library.
public class DesugaredLibraryRetargeterPostProcessor implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedDispatchMethods;

  public DesugaredLibraryRetargeterPostProcessor(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    emulatedDispatchMethods =
        appView.options().machineDesugaredLibrarySpecification.getEmulatedVirtualRetarget();
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService) {
    assert !appView.options().isDesugaredLibraryCompilation();
    ensureInterfacesAndForwardingMethodsSynthesized(programClasses, eventConsumer);
  }

  private void ensureInterfacesAndForwardingMethodsSynthesized(
      Collection<DexProgramClass> programClasses,
      DesugaredLibraryRetargeterPostProcessingEventConsumer eventConsumer) {
    assert !appView.options().isDesugaredLibraryCompilation();
    Map<DexType, List<DexMethod>> map = Maps.newIdentityHashMap();
    emulatedDispatchMethods.forEach(
        (method, descriptor) -> {
          map.putIfAbsent(method.getHolderType(), new ArrayList<>(1));
          map.get(method.getHolderType()).add(method);
        });
    for (DexProgramClass clazz : programClasses) {
      if (clazz.superType == null) {
        assert clazz.type == appView.dexItemFactory().objectType : clazz.type.toSourceString();
        continue;
      }
      DexClass superclass = appView.definitionFor(clazz.superType);
      // Only performs computation if superclass is a library class, but not object to filter out
      // the most common case.
      if (superclass != null
          && superclass.isLibraryClass()
          && superclass.type != appView.dexItemFactory().objectType) {
        map.forEach(
            (type, methods) -> {
              if (inherit(superclass.asLibraryClass(), type, emulatedDispatchMethods)) {
                ensureInterfacesAndForwardingMethodsSynthesized(eventConsumer, clazz, methods);
              }
            });
      }
    }
  }

  private boolean inherit(
      DexLibraryClass clazz,
      DexType typeToInherit,
      Map<DexMethod, EmulatedDispatchMethodDescriptor> retarget) {
    DexLibraryClass current = clazz;
    while (current.type != appView.dexItemFactory().objectType) {
      if (current.type == typeToInherit) {
        return true;
      }
      DexClass dexClass = appView.definitionFor(current.superType);
      if (dexClass == null || dexClass.isClasspathClass()) {
        reportInvalidLibrarySupertype(current, retarget.keySet());
        return false;
      } else if (dexClass.isProgramClass()) {
        // If dexClass is a program class, then it is already correctly desugared.
        return false;
      }
      current = dexClass.asLibraryClass();
    }
    return false;
  }

  private void ensureInterfacesAndForwardingMethodsSynthesized(
      DesugaredLibraryRetargeterPostProcessingEventConsumer eventConsumer,
      DexProgramClass clazz,
      List<DexMethod> methods) {
    // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
    // methods.
    // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
    // applies up to 24.
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return;
    }
    for (DexMethod method : methods) {
      EmulatedDispatchMethodDescriptor descriptor = emulatedDispatchMethods.get(method);
      DexClass newInterface =
          syntheticHelper.ensureEmulatedInterfaceDispatchMethod(descriptor, eventConsumer);
      if (clazz.interfaces.contains(newInterface.type)) {
        // The class has already been desugared.
        continue;
      }
      if (appView.dexItemFactory().multiDexTypes.contains(clazz.getType())) {
        continue;
      }
      clazz.addExtraInterfaces(
          Collections.singletonList(new ClassTypeSignature(newInterface.type)),
          appView.dexItemFactory());
      eventConsumer.acceptInterfaceInjection(clazz, newInterface);
      DexMethod itfMethod =
          syntheticHelper.emulatedInterfaceDispatchMethod(newInterface, descriptor);
      if (clazz.lookupVirtualMethod(method) == null) {
        DexEncodedMethod newMethod = createForwardingMethod(itfMethod, descriptor, clazz);
        clazz.addVirtualMethod(newMethod);
        eventConsumer.acceptDesugaredLibraryRetargeterForwardingMethod(
            new ProgramMethod(clazz, newMethod), descriptor);
      }
    }
  }

  private DexEncodedMethod createForwardingMethod(
      DexMethod target, EmulatedDispatchMethodDescriptor descriptor, DexClass clazz) {
    // NOTE: Never add a forwarding method to methods of classes unknown or coming from android.jar
    // even if this results in invalid code, these classes are never desugared.
    // In desugared library, emulated interface methods can be overridden by retarget lib members.
    DexMethod forwardMethod = syntheticHelper.forwardingMethod(descriptor);
    assert forwardMethod != null && forwardMethod != target;
    DexClassAndMethod resolvedMethod =
        appView.appInfoForDesugaring().resolveMethodLegacy(target, true).getResolutionPair();
    assert resolvedMethod != null;
    DexEncodedMethod desugaringForwardingMethod =
        DexEncodedMethod.createDesugaringForwardingMethod(
            resolvedMethod, clazz, forwardMethod, appView.dexItemFactory());
    desugaringForwardingMethod.setLibraryMethodOverride(OptionalBool.TRUE);
    return desugaringForwardingMethod;
  }

  private void reportInvalidLibrarySupertype(
      DexLibraryClass libraryClass, Set<DexMethod> retarget) {
    DexClass dexClass = appView.definitionFor(libraryClass.superType);
    String message;
    if (dexClass == null) {
      message = "missing";
    } else if (dexClass.isClasspathClass()) {
      message = "a classpath class";
    } else {
      message = "INVALID";
      assert false;
    }
    appView
        .options()
        .warningInvalidLibrarySuperclassForDesugar(
            dexClass == null ? libraryClass.getOrigin() : dexClass.getOrigin(),
            libraryClass.type,
            libraryClass.superType,
            message,
            retarget);
  }
}
