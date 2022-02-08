// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class HumanToMachineWrapperConverter {

  private final AppInfoWithClassHierarchy appInfo;
  private final Set<DexType> missingClasses = Sets.newIdentityHashSet();

  public HumanToMachineWrapperConverter(AppInfoWithClassHierarchy appInfo) {
    this.appInfo = appInfo;
  }

  public void convertWrappers(
      HumanRewritingFlags rewritingFlags,
      MachineRewritingFlags.Builder builder,
      BiConsumer<String, Set<? extends DexReference>> warnConsumer) {
    for (DexType wrapperType : rewritingFlags.getWrapperConversions()) {
      DexClass wrapperClass = appInfo.definitionFor(wrapperType);
      if (wrapperClass == null) {
        missingClasses.add(wrapperType);
        continue;
      }
      List<DexMethod> methods;
      if (wrapperClass.isEnum()) {
        methods = ImmutableList.of();
      } else {
        methods = allImplementedMethods(wrapperClass);
        methods.sort(DexMethod::compareTo);
      }
      builder.addWrapper(wrapperType, methods);
    }
    warnConsumer.accept("The following types to wrap are missing: ", missingClasses);
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
            assert !virtualMethod.isFinal()
                : "Cannot wrap final method " + virtualMethod + " while wrapping " + wrapperClass;
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
        assert superClass != null
            : "Missing supertype " + dexClass.superType + " while wrapping " + wrapperClass;
        workList.add(superClass);
      }
    }
    return implementedMethods;
  }
}
