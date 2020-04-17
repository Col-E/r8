// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmClassifier;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmTypeVisitor;

// Provides access to Kotlin information about a kotlin type.
public class KotlinTypeInfo {

  static final List<KotlinTypeProjectionInfo> EMPTY_ARGUMENTS = ImmutableList.of();

  private final KmClassifier classifier;
  private final List<KotlinTypeProjectionInfo> arguments;

  private KotlinTypeInfo(KmClassifier classifier, List<KotlinTypeProjectionInfo> arguments) {
    this.classifier = classifier;
    this.arguments = arguments;
  }

  static KotlinTypeInfo create(KmType kmType) {
    if (kmType == null) {
      return null;
    }
    if (kmType.getArguments().isEmpty()) {
      return new KotlinTypeInfo(kmType.classifier, EMPTY_ARGUMENTS);
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> arguments = new ImmutableList.Builder<>();
    for (KmTypeProjection argument : kmType.getArguments()) {
      arguments.add(KotlinTypeProjectionInfo.create(argument));
    }
    return new KotlinTypeInfo(kmType.classifier, arguments.build());
  }

  public boolean isTypeAlias() {
    return classifier instanceof KmClassifier.TypeAlias;
  }

  public KmClassifier.TypeAlias asTypeAlias() {
    return (KmClassifier.TypeAlias) classifier;
  }

  public boolean isClass() {
    return classifier instanceof KmClassifier.Class;
  }

  public KmClassifier.Class asClass() {
    return (KmClassifier.Class) classifier;
  }

  public boolean isTypeParameter() {
    return classifier instanceof KmClassifier.TypeParameter;
  }

  public KmClassifier.TypeParameter asTypeParameter() {
    return (KmClassifier.TypeParameter) classifier;
  }

  public boolean isObjectArray() {
    if (isClass()) {
      KmClassifier.Class classifier = (KmClassifier.Class) this.classifier;
      return classifier.getName().equals(ClassClassifiers.arrayBinaryName) && arguments.size() == 1;
    }
    return false;
  }

  public List<KotlinTypeProjectionInfo> getArguments() {
    return arguments;
  }

  public KotlinTypeProjectionInfo getArgumentOrNull(int index) {
    List<KotlinTypeProjectionInfo> arguments = getArguments();
    return arguments.size() > index ? getArguments().get(index) : null;
  }

  public KotlinTypeInfo toRenamed(KotlinMetadataSynthesizer synthesizer) {
    DexType originalType = getLiveDexTypeFromClassClassifier(synthesizer.appView);
    if (isClass() && originalType == null) {
      return null;
    }
    KmClassifier renamedClassifier = classifier;
    if (originalType != null) {
      String typeClassifier = synthesizer.toRenamedClassifier(originalType);
      if (typeClassifier != null) {
        renamedClassifier = new KmClassifier.Class(typeClassifier);
      }
    }
    if (arguments.isEmpty()) {
      return renamedClassifier == classifier
          ? this
          : new KotlinTypeInfo(renamedClassifier, EMPTY_ARGUMENTS);
    }
    ImmutableList.Builder<KotlinTypeProjectionInfo> builder = ImmutableList.builder();
    for (KotlinTypeProjectionInfo argument : arguments) {
      builder.add(
          new KotlinTypeProjectionInfo(
              argument.variance,
              argument.typeInfo == null ? null : argument.typeInfo.toRenamed(synthesizer)));
    }
    return new KotlinTypeInfo(renamedClassifier, builder.build());
  }

  private DexType getLiveDexTypeFromClassClassifier(AppView<AppInfoWithLiveness> appView) {
    if (!isClass()) {
      return null;
    }
    String descriptor = DescriptorUtils.getDescriptorFromKotlinClassifier(asClass().getName());
    DexType type = appView.dexItemFactory().createType(descriptor);
    if (appView.appInfo().wasPruned(type)) {
      return null;
    }
    return type;
  }

  public KmType asKmType() {
    KmType kmType = new KmType(flagsOf());
    visit(kmType);
    return kmType;
  }

  public void visit(KmTypeVisitor visitor) {
    if (isClass()) {
      visitor.visitClass(asClass().getName());
    } else if (isTypeAlias()) {
      visitor.visitTypeAlias(asTypeAlias().getName());
    } else {
      assert isTypeParameter();
      visitor.visitTypeParameter(asTypeParameter().getId());
    }
    for (KotlinTypeProjectionInfo argument : arguments) {
      argument.visit(visitor);
    }
  }
}
