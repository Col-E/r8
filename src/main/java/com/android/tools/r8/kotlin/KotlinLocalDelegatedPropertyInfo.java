// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.kotlin.KmVisitorProviders.KmPropertyVisitorProvider;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmProperty;

public class KotlinLocalDelegatedPropertyInfo implements EnqueuerMetadataTraceable {

  private static final KotlinLocalDelegatedPropertyInfo EMPTY_DELEGATED_PROPERTIES =
      new KotlinLocalDelegatedPropertyInfo(ImmutableList.of());

  private final List<KotlinPropertyInfo> propertyInfos;

  private KotlinLocalDelegatedPropertyInfo(List<KotlinPropertyInfo> propertyInfos) {
    this.propertyInfos = propertyInfos;
  }

  static KotlinLocalDelegatedPropertyInfo create(
      List<KmProperty> kmProperties, DexItemFactory factory, Reporter reporter) {
    if (kmProperties == null || kmProperties.size() == 0) {
      return EMPTY_DELEGATED_PROPERTIES;
    }
    ImmutableList.Builder<KotlinPropertyInfo> builder = ImmutableList.builder();
    for (KmProperty kmProperty : kmProperties) {
      KotlinPropertyInfo kotlinPropertyInfo =
          KotlinPropertyInfo.create(kmProperty, factory, reporter);
      // For ordinary properties, we place these on the fields and methods, but these are hooked in,
      // and do not have any jvm signatures:
      assert kotlinPropertyInfo.getFieldSignature() == null;
      assert kotlinPropertyInfo.getGetterSignature() == null;
      assert kotlinPropertyInfo.getSetterSignature() == null;
      builder.add(kotlinPropertyInfo);
    }
    return new KotlinLocalDelegatedPropertyInfo(builder.build());
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(propertyInfos, prop -> prop::trace, definitionSupplier);
  }

  public void rewrite(
      KmPropertyVisitorProvider visitorProvider, AppView<?> appView, NamingLens namingLens) {
    for (KotlinPropertyInfo propertyInfo : propertyInfos) {
      propertyInfo.rewrite(visitorProvider, null, null, null, appView, namingLens);
    }
  }
}
