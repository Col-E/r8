// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import com.android.tools.r8.dex.FileWriter.MixedSectionOffsets;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexWritableCode;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class DefaultMixedSectionLayoutStrategy extends MixedSectionLayoutStrategy {

  final AppView<?> appView;
  final MixedSectionOffsets mixedSectionOffsets;

  public DefaultMixedSectionLayoutStrategy(
      AppView<?> appView, MixedSectionOffsets mixedSectionOffsets) {
    this.appView = appView;
    this.mixedSectionOffsets = mixedSectionOffsets;
  }

  @Override
  public Collection<DexAnnotation> getAnnotationLayout() {
    return mixedSectionOffsets.getAnnotations();
  }

  @Override
  public Collection<DexAnnotationDirectory> getAnnotationDirectoryLayout() {
    return mixedSectionOffsets.getAnnotationDirectories();
  }

  @Override
  public Collection<DexAnnotationSet> getAnnotationSetLayout() {
    return mixedSectionOffsets.getAnnotationSets();
  }

  @Override
  public Collection<ParameterAnnotationsList> getAnnotationSetRefListLayout() {
    return mixedSectionOffsets.getAnnotationSetRefLists();
  }

  @Override
  public Collection<DexProgramClass> getClassDataLayout() {
    return mixedSectionOffsets.getClassesWithData();
  }

  @Override
  public Collection<ProgramMethod> getCodeLayout() {
    return getCodeLayoutForClasses(mixedSectionOffsets.getClassesWithData());
  }

  final Collection<ProgramMethod> getCodeLayoutForClasses(Collection<DexProgramClass> classes) {
    ProgramMethodMap<String> codeToSignatureMap = ProgramMethodMap.create();
    List<ProgramMethod> codesSorted = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            DexWritableCode code = method.getDefinition().getDexWritableCodeOrNull();
            assert code != null || method.getDefinition().shouldNotHaveCode();
            if (code != null) {
              codesSorted.add(method);
              codeToSignatureMap.put(
                  method, getKeyForDexCodeSorting(method, appView.app().getProguardMap()));
            }
          });
    }
    codesSorted.sort(Comparator.comparing(codeToSignatureMap::get));
    return codesSorted;
  }

  private static String getKeyForDexCodeSorting(ProgramMethod method, ClassNameMapper proguardMap) {
    // TODO(b/173999869): Could this instead compute sorting using dex items?
    Signature signature;
    String originalClassName;
    if (proguardMap != null) {
      signature = proguardMap.originalSignatureOf(method.getReference());
      originalClassName = proguardMap.originalNameOf(method.getHolderType());
    } else {
      signature = MethodSignature.fromDexMethod(method.getReference());
      originalClassName = method.getHolderType().toSourceString();
    }
    return originalClassName + signature;
  }

  @Override
  public Collection<DexEncodedArray> getEncodedArrayLayout() {
    return mixedSectionOffsets.getEncodedArrays();
  }

  @Override
  public Collection<DexString> getStringDataLayout() {
    return mixedSectionOffsets.getStringData();
  }

  @Override
  public Collection<DexTypeList> getTypeListLayout() {
    return mixedSectionOffsets.getTypeLists();
  }
}
