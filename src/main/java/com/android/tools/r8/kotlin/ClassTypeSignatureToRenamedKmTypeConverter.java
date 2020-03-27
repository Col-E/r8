// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import java.util.function.Function;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmVariance;

class ClassTypeSignatureToRenamedKmTypeConverter implements ClassTypeSignature.Converter<KmType> {

  private final AppView<AppInfoWithLiveness> appView;
  private final List<KmTypeParameter> typeParameters;
  private final Function<DexType, String> toRenamedClassClassifier;

  ClassTypeSignatureToRenamedKmTypeConverter(
      AppView<AppInfoWithLiveness> appView,
      List<KmTypeParameter> typeParameters,
      Function<DexType, String> toRenamedClassClassifier) {
    this.appView = appView;
    this.typeParameters = typeParameters;
    this.toRenamedClassClassifier = toRenamedClassClassifier;
  }

  @Override
  public KmType init() {
    return new KmType(flagsOf());
  }

  @Override
  public KmType visitType(DexType type, KmType result) {
    String classifier = toRenamedClassClassifier.apply(type);
    if (classifier == null) {
      return null;
    }
    result.visitClass(classifier);
    return result;
  }

  @Override
  public KmType visitTypeArgument(FieldTypeSignature typeArgument, KmType result) {
    if (result == null) {
      return null;
    }
    List<KmTypeProjection> arguments = result.getArguments();
    KotlinMetadataSynthesizerUtils.populateKmTypeFromSignature(
        typeArgument,
        () -> {
          KmType kmTypeArgument = new KmType(flagsOf());
          arguments.add(new KmTypeProjection(KmVariance.INVARIANT, kmTypeArgument));
          return kmTypeArgument;
        },
        typeParameters,
        appView.dexItemFactory());
    return result;
  }

  @Override
  public KmType visitInnerTypeSignature(ClassTypeSignature innerTypeSignature, KmType result) {
    // Do nothing
    return result;
  }

  public List<KmTypeParameter> getTypeParameters() {
    return typeParameters;
  }
}
