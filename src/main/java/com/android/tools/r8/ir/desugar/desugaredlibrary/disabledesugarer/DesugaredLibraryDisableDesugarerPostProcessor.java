// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DesugaredLibraryDisableDesugarerPostProcessor implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryDisableDesugarerHelper helper;

  public DesugaredLibraryDisableDesugarerPostProcessor(AppView<?> appView) {
    this.appView = appView;
    this.helper = new DesugaredLibraryDisableDesugarerHelper(appView);
  }

  public static DesugaredLibraryDisableDesugarerPostProcessor create(AppView<?> appView) {
    return DesugaredLibraryDisableDesugarerHelper.shouldCreate(appView)
        ? new DesugaredLibraryDisableDesugarerPostProcessor(appView)
        : null;
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    for (DexType multiDexType : appView.dexItemFactory().multiDexTypes) {
      DexClass clazz =
          appView.appInfoForDesugaring().definitionForWithoutExistenceAssert(multiDexType);
      if (clazz != null && clazz.isProgramClass()) {
        rewriteMultiDexProgramClass(clazz.asProgramClass());
      }
    }
  }

  private void rewriteMultiDexProgramClass(DexProgramClass multiDexProgramClass) {
    multiDexProgramClass.setInstanceFields(
        rewriteFields(multiDexProgramClass.instanceFields(), multiDexProgramClass));
    multiDexProgramClass.setStaticFields(
        rewriteFields(multiDexProgramClass.staticFields(), multiDexProgramClass));
  }

  private DexEncodedField[] rewriteFields(
      List<DexEncodedField> fields, DexProgramClass multiDexProgramClass) {
    List<DexEncodedField> newFields = new ArrayList<>();
    for (DexEncodedField field : fields) {
      DexField rewrittenField = helper.rewriteField(field.getReference(), multiDexProgramClass);
      newFields.add(
          rewrittenField != null ? field.toTypeSubstitutedField(appView, rewrittenField) : field);
    }
    return newFields.toArray(DexEncodedField[]::new);
  }
}
