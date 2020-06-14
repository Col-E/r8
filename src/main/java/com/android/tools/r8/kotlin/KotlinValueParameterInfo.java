// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.KmValueParameterVisitor;
import kotlinx.metadata.internal.metadata.deserialization.Flags;

// Provides access to Kotlin information about value parameter.
class KotlinValueParameterInfo implements EnqueuerMetadataTraceable {
  private static final List<KotlinValueParameterInfo> EMPTY_VALUE_PARAMETERS = ImmutableList.of();
  // Original parameter name.
  final String name;
  // Original parameter flags, e.g., has default value.
  final int flags;
  // Original information about the type.
  final KotlinTypeInfo type;
  // Indicates whether the formal parameter is originally `vararg`.
  final KotlinTypeInfo varargElementType;

  private KotlinValueParameterInfo(
      int flags, String name, KotlinTypeInfo type, KotlinTypeInfo varargElementType) {
    this.name = name;
    this.flags = flags;
    this.type = type;
    this.varargElementType = varargElementType;
  }

  boolean isCrossInline() {
    return Flags.IS_CROSSINLINE.get(flags);
  }

  static KotlinValueParameterInfo create(
      KmValueParameter kmValueParameter, DexItemFactory factory, Reporter reporter) {
    if (kmValueParameter == null) {
      return null;
    }
    KmType kmType = kmValueParameter.getType();
    return new KotlinValueParameterInfo(
        kmValueParameter.getFlags(),
        kmValueParameter.getName(),
        KotlinTypeInfo.create(kmType, factory, reporter),
        KotlinTypeInfo.create(kmValueParameter.getVarargElementType(), factory, reporter));
  }

  static List<KotlinValueParameterInfo> create(
      List<KmValueParameter> parameters, DexItemFactory factory, Reporter reporter) {
    if (parameters.isEmpty()) {
      return EMPTY_VALUE_PARAMETERS;
    }
    ImmutableList.Builder<KotlinValueParameterInfo> builder = ImmutableList.builder();
    for (KmValueParameter parameter : parameters) {
      builder.add(create(parameter, factory, reporter));
    }
    return builder.build();
  }

  void rewrite(
      KmVisitorProviders.KmValueParameterVisitorProvider visitorProvider,
      AppView<?> appView,
      NamingLens namingLens) {
    KmValueParameterVisitor kmValueParameterVisitor = visitorProvider.get(flags, name);
    type.rewrite(kmValueParameterVisitor::visitType, appView, namingLens);
    if (varargElementType != null) {
      varargElementType.rewrite(
          kmValueParameterVisitor::visitVarargElementType, appView, namingLens);
    }
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    type.trace(definitionSupplier);
    if (varargElementType != null) {
      varargElementType.trace(definitionSupplier);
    }
  }
}
