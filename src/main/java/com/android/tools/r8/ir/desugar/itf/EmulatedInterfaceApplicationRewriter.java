// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.utils.IterableUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class EmulatedInterfaceApplicationRewriter {

  private final AppView<?> appView;
  private final Map<DexType, DexType> emulatedInterfaces;

  public EmulatedInterfaceApplicationRewriter(AppView<?> appView) {
    this.appView = appView;
    this.emulatedInterfaces =
        appView.options().desugaredLibraryConfiguration.getEmulateLibraryInterface();
  }

  public void rewriteApplication(DexApplication.Builder<?> builder) {
    assert appView.options().isDesugaredLibraryCompilation();
    ArrayList<DexProgramClass> newProgramClasses = new ArrayList<>();
    for (DexProgramClass clazz : builder.getProgramClasses()) {
      if (emulatedInterfaces.containsKey(clazz.type)) {
        newProgramClasses.add(rewriteEmulatedInterface(clazz));
      } else if (clazz.isInterface()
          && !appView.rewritePrefix.hasRewrittenType(clazz.type, appView)
          && isEmulatedInterfaceSubInterface(clazz)) {
        // Intentionally filter such classes out.
        handleEmulatedInterfaceSubInterface(clazz);
      } else {
        newProgramClasses.add(clazz);
      }
    }
    builder.replaceProgramClasses(newProgramClasses);
  }

  private void handleEmulatedInterfaceSubInterface(DexProgramClass clazz) {
    // TODO(b/183918843): Investigate how to specify these in the json file.
    // These are interfaces which needs a companion class for desugared library to work, but
    // the interface is not supported outside of desugared library. The interface has to be
    // present during the compilation for the companion class to be generated, but filtered out
    // afterwards. The companion class needs to be rewritten to have the desugared library
    // prefix since all classes in desugared library should have the prefix, we used the
    // questionable method convertJavaNameToDesugaredLibrary to generate a correct type.
    String newName =
        appView
            .options()
            .desugaredLibraryConfiguration
            .convertJavaNameToDesugaredLibrary(clazz.type);
    InterfaceMethodRewriter.addCompanionClassRewriteRule(clazz.type, newName, appView);
  }

  private boolean isEmulatedInterfaceSubInterface(DexClass subInterface) {
    assert !emulatedInterfaces.containsKey(subInterface.type);
    LinkedList<DexType> workList = new LinkedList<>(Arrays.asList(subInterface.interfaces.values));
    while (!workList.isEmpty()) {
      DexType next = workList.removeFirst();
      if (emulatedInterfaces.containsKey(next)) {
        return true;
      }
      DexClass nextClass = appView.definitionFor(next);
      if (nextClass != null) {
        workList.addAll(Arrays.asList(nextClass.interfaces.values));
      }
    }
    return false;
  }

  // The method transforms emulated interface such as they now have the rewritten type and
  // implement the rewritten version of each emulated interface they implement.
  private DexProgramClass rewriteEmulatedInterface(DexProgramClass emulatedInterface) {
    if (appView.isAlreadyLibraryDesugared(emulatedInterface)) {
      return emulatedInterface;
    }
    DexType newType = emulatedInterfaces.get(emulatedInterface.type);
    assert newType != null;
    DexEncodedMethod[] newVirtualMethods =
        renameHolder(emulatedInterface.virtualMethods(), newType);
    DexEncodedMethod[] newDirectMethods = renameHolder(emulatedInterface.directMethods(), newType);
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
            null, // Note that we clear the enclosing and inner class attributes.
            Collections.emptyList(),
            emulatedInterface.getClassSignature(),
            emulatedInterface.annotations(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            newDirectMethods,
            newVirtualMethods,
            false,
            emulatedInterface.getChecksumSupplier());
    newEmulatedInterface.addExtraInterfaces(
        getRewrittenInterfacesOfEmulatedInterface(emulatedInterface));
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
              classSignature.superInterfaceSignatures().get(i);
          assert itf == classTypeSignature.type();
          typeArguments = classTypeSignature.typeArguments();
        }
        newInterfaces.add(
            new GenericSignature.ClassTypeSignature(emulatedInterfaces.get(itf), typeArguments));
      }
    }
    return newInterfaces;
  }

  private DexEncodedMethod[] renameHolder(Iterable<DexEncodedMethod> methods, DexType newName) {
    List<DexEncodedMethod> methodArray = IterableUtils.toNewArrayList(methods);
    DexEncodedMethod[] newMethods = new DexEncodedMethod[methodArray.size()];
    for (int i = 0; i < newMethods.length; i++) {
      newMethods[i] = methodArray.get(i).toRenamedHolderMethod(newName, appView.dexItemFactory());
    }
    return newMethods;
  }
}
