// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HumanToMachineWrapperConverter {

  private final AppInfoWithClassHierarchy appInfo;

  public HumanToMachineWrapperConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertWrappers(
      HumanRewritingFlags rewritingFlags, MachineRewritingFlags.Builder builder) {
    for (DexType wrapperConversion : rewritingFlags.getWrapperConversions()) {
      DexClass wrapperClass = appInfo.definitionFor(wrapperConversion);
      assert wrapperClass != null;
      List<DexMethod> methods = allImplementedMethods(wrapperClass);
      methods.sort(DexMethod::compareTo);
      builder.addWrapper(wrapperConversion, methods);
    }
  }

  private List<DexMethod> allImplementedMethods(DexClass wrapperClass) {
    LinkedList<DexClass> workList = new LinkedList<>();
    List<DexMethod> implementedMethods = new ArrayList<>();
    workList.add(wrapperClass);
    while (!workList.isEmpty()) {
      DexClass dexClass = workList.removeFirst();
      for (DexEncodedMethod virtualMethod : dexClass.virtualMethods()) {
        if (!virtualMethod.isPrivateMethod()) {
          assert virtualMethod.isProtectedMethod() || virtualMethod.isPublicMethod();
          boolean alreadyAdded = false;
          // This looks quadratic but given the size of the collections met in practice for
          // desugared libraries (Max ~15) it does not matter.
          for (DexMethod alreadyImplementedMethod : implementedMethods) {
            if (alreadyImplementedMethod.match(virtualMethod.getReference())) {
              alreadyAdded = true;
              break;
            }
          }
          if (!alreadyAdded) {
            assert !virtualMethod.isFinal() : "Cannot wrap final method " + virtualMethod;
            implementedMethods.add(virtualMethod.getReference());
          }
        }
      }
      for (DexType itf : dexClass.interfaces.values) {
        DexClass itfClass = appInfo.definitionFor(itf);
        if (itfClass != null) {
          workList.add(itfClass);
        }
      }
      if (dexClass.superType != appInfo.dexItemFactory().objectType) {
        DexClass superClass = appInfo.definitionFor(dexClass.superType);
        assert superClass != null; // Cannot be null since we started from a LibraryClass.
        workList.add(superClass);
      }
    }
    return implementedMethods;
  }
}
