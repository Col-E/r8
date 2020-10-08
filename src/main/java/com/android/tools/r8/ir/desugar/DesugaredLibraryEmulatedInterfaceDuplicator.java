// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DesugaredLibraryEmulatedInterfaceDuplicator {

  final AppView<?> appView;
  final Map<DexType, DexType> emulatedInterfaces;

  public DesugaredLibraryEmulatedInterfaceDuplicator(AppView<?> appView) {
    this.appView = appView;
    emulatedInterfaces =
        appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();
  }

  public void duplicateEmulatedInterfaces() {
    // All classes implementing an emulated interface now implements the interface and the
    // emulated one, as well as hidden overrides, for correct emulated dispatch.
    // We not that duplicated interfaces won't feature the correct type parameters in the
    // class signature since such signature is expected to be unused.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.type == appView.dexItemFactory().objectType) {
        continue;
      }
      if (emulatedInterfaces.containsKey(clazz.type)) {
        transformEmulatedInterfaces(clazz);
      } else {
        duplicateEmulatedInterfaces(clazz);
      }
    }
  }

  private void transformEmulatedInterfaces(DexProgramClass clazz) {
    List<ClassTypeSignature> newInterfaces = new ArrayList<>();
    GenericSignature.ClassSignature classSignature = clazz.getClassSignature();
    for (int i = 0; i < clazz.interfaces.size(); i++) {
      DexType itf = clazz.interfaces.values[i];
      assert emulatedInterfaces.containsKey(itf);
      List<FieldTypeSignature> typeArguments;
      if (classSignature == null) {
        typeArguments = Collections.emptyList();
      } else {
        ClassTypeSignature classTypeSignature = classSignature.superInterfaceSignatures().get(i);
        assert itf == classTypeSignature.type();
        typeArguments = classTypeSignature.typeArguments();
      }
      newInterfaces.add(new ClassTypeSignature(emulatedInterfaces.get(itf), typeArguments));
    }
    clazz.replaceInterfaces(newInterfaces);
  }

  private void duplicateEmulatedInterfaces(DexProgramClass clazz) {
    List<DexType> extraInterfaces = new ArrayList<>();
    LinkedList<DexClass> workList = new LinkedList<>();
    Set<DexType> processed = Sets.newIdentityHashSet();
    workList.add(clazz);
    while (!workList.isEmpty()) {
      DexClass dexClass = workList.removeFirst();
      if (processed.contains(dexClass.type)) {
        continue;
      }
      processed.add(dexClass.type);
      if (dexClass.superType != appView.dexItemFactory().objectType) {
        processSuperType(clazz.superType, extraInterfaces, workList);
      }
      for (DexType itf : dexClass.interfaces) {
        processSuperType(itf, extraInterfaces, workList);
      }
    }
    extraInterfaces = removeDuplicates(extraInterfaces);
    List<ClassTypeSignature> extraInterfaceSignatures = new ArrayList<>();
    for (DexType extraInterface : extraInterfaces) {
      extraInterfaceSignatures.add(new ClassTypeSignature(extraInterface));
    }
    clazz.addExtraInterfaces(extraInterfaceSignatures);
  }

  private List<DexType> removeDuplicates(List<DexType> extraInterfaces) {
    if (extraInterfaces.size() <= 1) {
      return extraInterfaces;
    }
    // TODO(b/161399032): It would be nice to remove duplicate based on inheritance, i.e.,
    //  if there is ConcurrentMap<K,V> and Map<K,V>, Map<K,V> can be removed.
    return new ArrayList<>(new HashSet<>(extraInterfaces));
  }

  void processSuperType(
      DexType superType, List<DexType> extraInterfaces, LinkedList<DexClass> workList) {
    if (emulatedInterfaces.containsKey(superType)) {
      extraInterfaces.add(emulatedInterfaces.get(superType));
    } else {
      DexClass superClass = appView.definitionFor(superType);
      if (shouldProcessSuperclass(superClass)) {
        workList.add(superClass);
      }
    }
  }

  private boolean shouldProcessSuperclass(DexClass superclazz) {
    if (appView.options().isDesugaredLibraryCompilation()) {
      return false;
    }
    // TODO(b/161399032): Pay-as-you-go design: stop duplication on library boundaries.
    return superclazz != null && superclazz.isLibraryClass();
  }
}
