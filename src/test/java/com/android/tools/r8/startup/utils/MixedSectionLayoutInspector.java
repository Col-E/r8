// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.utils;

import com.android.tools.r8.dex.MixedSectionLayoutStrategy;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Collection;
import java.util.function.BiFunction;

public abstract class MixedSectionLayoutInspector
    implements BiFunction<MixedSectionLayoutStrategy, VirtualFile, MixedSectionLayoutStrategy> {

  public void inspectAnnotationLayout(int virtualFile, Collection<DexAnnotation> layout) {
    // Intentionally empty.
  }

  public void inspectAnnotationDirectoryLayout(
      int virtualFile, Collection<DexAnnotationDirectory> layout) {
    // Intentionally empty.
  }

  public void inspectAnnotationSetLayout(int virtualFile, Collection<DexAnnotationSet> layout) {
    // Intentionally empty.
  }

  public void inspectAnnotationSetRefListLayout(
      int virtualFile, Collection<ParameterAnnotationsList> layout) {
    // Intentionally empty.
  }

  public void inspectClassDataLayout(int virtualFile, Collection<DexProgramClass> layout) {
    // Intentionally empty.
  }

  public void inspectCodeLayout(int virtualFile, Collection<ProgramMethod> layout) {
    // Intentionally empty.
  }

  public void inspectEncodedArrayLayout(int virtualFile, Collection<DexEncodedArray> layout) {
    // Intentionally empty.
  }

  public void inspectStringDataLayout(int virtualFile, Collection<DexString> layout) {
    // Intentionally empty.
  }

  public void inspectTypeListLayout(int virtualFile, Collection<DexTypeList> layout) {
    // Intentionally empty.
  }

  @Override
  public MixedSectionLayoutStrategy apply(
      MixedSectionLayoutStrategy mixedSectionLayoutStrategy, VirtualFile virtualFile) {
    return new MixedSectionLayoutStrategy() {

      @Override
      public Collection<DexAnnotation> getAnnotationLayout() {
        Collection<DexAnnotation> layout = mixedSectionLayoutStrategy.getAnnotationLayout();
        inspectAnnotationLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexAnnotationDirectory> getAnnotationDirectoryLayout() {
        Collection<DexAnnotationDirectory> layout =
            mixedSectionLayoutStrategy.getAnnotationDirectoryLayout();
        inspectAnnotationDirectoryLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexAnnotationSet> getAnnotationSetLayout() {
        Collection<DexAnnotationSet> layout = mixedSectionLayoutStrategy.getAnnotationSetLayout();
        inspectAnnotationSetLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<ParameterAnnotationsList> getAnnotationSetRefListLayout() {
        Collection<ParameterAnnotationsList> layout =
            mixedSectionLayoutStrategy.getAnnotationSetRefListLayout();
        inspectAnnotationSetRefListLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexProgramClass> getClassDataLayout() {
        Collection<DexProgramClass> layout = mixedSectionLayoutStrategy.getClassDataLayout();
        inspectClassDataLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<ProgramMethod> getCodeLayout() {
        Collection<ProgramMethod> layout = mixedSectionLayoutStrategy.getCodeLayout();
        inspectCodeLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexEncodedArray> getEncodedArrayLayout() {
        Collection<DexEncodedArray> layout = mixedSectionLayoutStrategy.getEncodedArrayLayout();
        inspectEncodedArrayLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexString> getStringDataLayout() {
        Collection<DexString> layout = mixedSectionLayoutStrategy.getStringDataLayout();
        inspectStringDataLayout(virtualFile.getId(), layout);
        return layout;
      }

      @Override
      public Collection<DexTypeList> getTypeListLayout() {
        Collection<DexTypeList> layout = mixedSectionLayoutStrategy.getTypeListLayout();
        inspectTypeListLayout(virtualFile.getId(), layout);
        return layout;
      }
    };
  }
}
