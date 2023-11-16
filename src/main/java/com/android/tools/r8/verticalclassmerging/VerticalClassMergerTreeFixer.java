// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import java.util.List;

class VerticalClassMergerTreeFixer extends TreeFixerBase {

  private final AppView<AppInfoWithLiveness> appView;
  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final VerticallyMergedClasses mergedClasses;
  private final List<SynthesizedBridgeCode> synthesizedBridges;

  VerticalClassMergerTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      VerticalClassMergerGraphLens.Builder lensBuilder,
      VerticallyMergedClasses mergedClasses,
      List<SynthesizedBridgeCode> synthesizedBridges) {
    super(appView);
    this.appView = appView;
    this.lensBuilder =
        VerticalClassMergerGraphLens.Builder.createBuilderForFixup(lensBuilder, mergedClasses);
    this.mergedClasses = mergedClasses;
    this.synthesizedBridges = synthesizedBridges;
  }

  VerticalClassMergerGraphLens fixupTypeReferences() {
    // Globally substitute merged class types in protos and holders.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.getMethodCollection().replaceMethods(this::fixupMethod);
      clazz.setStaticFields(fixupFields(clazz.staticFields()));
      clazz.setInstanceFields(fixupFields(clazz.instanceFields()));
      clazz.setPermittedSubclassAttributes(
          fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()));
    }
    for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
      synthesizedBridge.updateMethodSignatures(this::fixupMethodReference);
    }
    VerticalClassMergerGraphLens lens = lensBuilder.build(appView, mergedClasses);
    if (lens != null) {
      new AnnotationFixer(lens, appView.graphLens()).run(appView.appInfo().classes());
    }
    return lens;
  }

  @Override
  public DexType mapClassType(DexType type) {
    while (mergedClasses.hasBeenMergedIntoSubtype(type)) {
      type = mergedClasses.getTargetFor(type);
    }
    return type;
  }

  @Override
  public void recordClassChange(DexType from, DexType to) {
    // Fixup of classes is not used so no class type should change.
    throw new Unreachable();
  }

  @Override
  public void recordFieldChange(DexField from, DexField to) {
    if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
      lensBuilder.map(from, to);
    }
  }

  @Override
  public void recordMethodChange(DexMethod from, DexMethod to) {
    if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
      lensBuilder.map(from, to).recordMove(from, to);
    }
  }

  @Override
  public DexEncodedMethod recordMethodChange(DexEncodedMethod method, DexEncodedMethod newMethod) {
    recordMethodChange(method.getReference(), newMethod.getReference());
    if (newMethod.isNonPrivateVirtualMethod()) {
      // Since we changed the return type or one of the parameters, this method cannot be a
      // classpath or library method override, since we only class merge program classes.
      assert !method.isLibraryMethodOverride().isTrue();
      newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
    }
    return newMethod;
  }
}
