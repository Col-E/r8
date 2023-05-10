// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.IterableUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class EmulatedInterfaceApplicationRewriter {

  private final AppView<?> appView;
  private final Map<DexType, DexType> emulatedInterfaces;

  public EmulatedInterfaceApplicationRewriter(AppView<?> appView) {
    this.appView = appView;
    emulatedInterfaces = new IdentityHashMap<>();
    appView
        .options()
        .machineDesugaredLibrarySpecification
        .getEmulatedInterfaces()
        .forEach(
            (ei, descriptor) -> {
              emulatedInterfaces.put(ei, descriptor.getRewrittenType());
            });
  }

  public void rewriteApplication(DexApplication.Builder<?> builder) {
    assert appView.options().isDesugaredLibraryCompilation();
    ArrayList<DexProgramClass> newProgramClasses = new ArrayList<>();
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (emulatedInterfaces.containsKey(clazz.type)) {
        newProgramClasses.add(rewriteEmulatedInterface(clazz));
      } else {
        newProgramClasses.add(clazz);
      }
    }
    builder.replaceProgramClasses(newProgramClasses);
  }

  // The method transforms emulated interface such as they now have the rewritten type and
  // implement the rewritten version of each emulated interface they implement.
  private DexProgramClass rewriteEmulatedInterface(DexProgramClass emulatedInterface) {
    if (appView.isAlreadyLibraryDesugared(emulatedInterface)) {
      return emulatedInterface;
    }
    DexType newType = emulatedInterfaces.get(emulatedInterface.type);
    assert newType != null;
    DexEncodedMethod[] newVirtualMethods = computeNewVirtualMethods(emulatedInterface, newType);
    DexEncodedMethod[] newDirectMethods = DexEncodedMethod.EMPTY_ARRAY;
    assert emulatedInterface.getSuperType() == appView.dexItemFactory().objectType;
    assert !emulatedInterface.hasFields();
    assert emulatedInterface.getNestHost() == null;
    assert !emulatedInterface.hasNestMemberAttributes();
    assert !emulatedInterface.hasFields();
    DexProgramClass newEmulatedInterface =
        new DexProgramClass(
            newType,
            emulatedInterface.getOriginKind(),
            emulatedInterface.getOrigin(),
            emulatedInterface.getAccessFlags(),
            appView.dexItemFactory().objectType,
            DexTypeList.empty(),
            emulatedInterface.getSourceFile(),
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            null, // Note that we clear the enclosing and inner class attributes.
            Collections.emptyList(),
            emulatedInterface.getClassSignature(),
            emulatedInterface.annotations(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            MethodCollectionFactory.fromMethods(newDirectMethods, newVirtualMethods),
            false,
            emulatedInterface.getChecksumSupplier());
    newEmulatedInterface.addExtraInterfaces(
        getRewrittenInterfacesOfEmulatedInterface(emulatedInterface), appView.dexItemFactory());
    return newEmulatedInterface;
  }

  private List<GenericSignature.ClassTypeSignature> getRewrittenInterfacesOfEmulatedInterface(
      DexProgramClass emulatedInterface) {
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
              classSignature.getSuperInterfaceSignatures().get(i);
          assert itf == classTypeSignature.type();
          typeArguments = classTypeSignature.typeArguments();
        }
        newInterfaces.add(
            new GenericSignature.ClassTypeSignature(emulatedInterfaces.get(itf), typeArguments));
      }
    }
    return newInterfaces;
  }

  private DexEncodedMethod[] computeNewVirtualMethods(
      DexProgramClass emulatedInterface, DexType newName) {
    Iterable<ProgramMethod> emulatedMethods =
        emulatedInterface.virtualProgramMethods(
            m ->
                appView
                        .options()
                        .machineDesugaredLibrarySpecification
                        .getEmulatedInterfaceEmulatedDispatchMethodDescriptor(m.getReference())
                    != null);
    List<ProgramMethod> methodArray = IterableUtils.toNewArrayList(emulatedMethods);
    DexEncodedMethod[] newMethods = new DexEncodedMethod[methodArray.size()];
    for (int i = 0; i < newMethods.length; i++) {
      newMethods[i] =
          methodArray
              .get(i)
              .getDefinition()
              .toRenamedHolderMethod(newName, appView.dexItemFactory());
    }
    return newMethods;
  }
}
