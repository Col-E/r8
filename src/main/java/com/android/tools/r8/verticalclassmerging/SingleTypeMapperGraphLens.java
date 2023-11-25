// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.ir.code.InvokeType.VIRTUAL;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;

public class SingleTypeMapperGraphLens extends NonIdentityGraphLens {

  private final AppView<AppInfoWithLiveness> appView;
  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses;

  private final DexProgramClass source;
  private final DexProgramClass target;

  public SingleTypeMapperGraphLens(
      AppView<AppInfoWithLiveness> appView,
      VerticalClassMergerGraphLens.Builder lensBuilder,
      MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses,
      DexProgramClass source,
      DexProgramClass target) {
    super(appView.dexItemFactory(), GraphLens.getIdentityLens());
    this.appView = appView;
    this.lensBuilder = lensBuilder;
    this.mergedClasses = mergedClasses;
    this.source = source;
    this.target = target;
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    throw new Unreachable();
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    throw new Unreachable();
  }

  @Override
  public final DexType getNextClassType(DexType type) {
    return type.isIdenticalTo(source.getType())
        ? target.getType()
        : mergedClasses.getOrDefault(type, type);
  }

  @Override
  public DexField getPreviousFieldSignature(DexField field) {
    throw new Unreachable();
  }

  @Override
  public DexField getNextFieldSignature(DexField field) {
    throw new Unreachable();
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    throw new Unreachable();
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    throw new Unreachable();
  }

  @Override
  public MethodLookupResult lookupMethod(
      DexMethod method, DexMethod context, InvokeType type, GraphLens codeLens) {
    // First look up the method using the existing graph lens (for example, the type will have
    // changed if the method was publicized by ClassAndMemberPublicizer).
    MethodLookupResult lookup = appView.graphLens().lookupMethod(method, context, type, codeLens);
    // Then check if there is a renaming due to the vertical class merger.
    DexMethod newMethod = lensBuilder.methodMap.get(lookup.getReference());
    if (newMethod == null) {
      return lookup;
    }
    MethodLookupResult.Builder methodLookupResultBuilder =
        MethodLookupResult.builder(this)
            .setReference(newMethod)
            .setPrototypeChanges(lookup.getPrototypeChanges())
            .setType(lookup.getType());
    if (lookup.getType() == InvokeType.INTERFACE) {
      // If an interface has been merged into a class, invoke-interface needs to be translated
      // to invoke-virtual.
      DexClass clazz = appView.definitionFor(newMethod.holder);
      if (clazz != null && !clazz.accessFlags.isInterface()) {
        assert appView.definitionFor(method.holder).accessFlags.isInterface();
        methodLookupResultBuilder.setType(VIRTUAL);
      }
    }
    return methodLookupResultBuilder.build();
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    // This is unreachable since we override the implementation of lookupMethod() above.
    throw new Unreachable();
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    throw new Unreachable();
  }

  @Override
  public DexField lookupField(DexField field, GraphLens codeLens) {
    return lensBuilder.fieldMap.getOrDefault(field, field);
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    // This is unreachable since we override the implementation of lookupField() above.
    throw new Unreachable();
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    return true;
  }
}
