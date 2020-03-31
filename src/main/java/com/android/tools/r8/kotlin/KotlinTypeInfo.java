// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmClassifier;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;

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

  public boolean isObjectArray() {
    if (!isClass()) {
      KmClassifier.Class classifier = (KmClassifier.Class) this.classifier;
      return classifier.getName().equals(ClassClassifiers.arrayDescriptor) && arguments.size() == 1;
    }
    return false;
  }

  public List<KotlinTypeProjectionInfo> getArguments() {
    return arguments;
  }

  public KotlinTypeProjectionInfo getArgumentOrNull(int index) {
    List<KotlinTypeProjectionInfo> arguments = getArguments();
    return arguments.size() >= index ? getArguments().get(index) : null;
  }
}
