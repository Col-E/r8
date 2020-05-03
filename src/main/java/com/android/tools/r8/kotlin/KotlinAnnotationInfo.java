// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;

// Holds information about a KmAnnotation
public class KotlinAnnotationInfo {

  private static final List<KotlinAnnotationInfo> EMPTY_ANNOTATIONS = ImmutableList.of();

  private final DexType annotationType;
  // TODO(b/155053894): Model KmAnnotationArgument.
  private final Map<String, KmAnnotationArgument<?>> arguments;

  private KotlinAnnotationInfo(
      DexType annotationType, Map<String, KmAnnotationArgument<?>> arguments) {
    this.annotationType = annotationType;
    this.arguments = arguments;
  }

  private static KotlinAnnotationInfo create(KmAnnotation annotation, AppView<?> appView) {
    String descriptor = DescriptorUtils.getDescriptorFromClassBinaryName(annotation.getClassName());
    DexType type = appView.dexItemFactory().createType(descriptor);
    return new KotlinAnnotationInfo(type, annotation.getArguments());
  }

  static List<KotlinAnnotationInfo> create(List<KmAnnotation> annotations, AppView<?> appView) {
    if (annotations.isEmpty()) {
      return EMPTY_ANNOTATIONS;
    }
    ImmutableList.Builder<KotlinAnnotationInfo> builder = ImmutableList.builder();
    for (KmAnnotation annotation : annotations) {
      builder.add(create(annotation, appView));
    }
    return builder.build();
  }

  public void rewrite(
      KmVisitorProviders.KmAnnotationVisitorProvider visitorProvider,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    if (appView.appInfo().wasPruned(annotationType)) {
      return;
    }
    DexString descriptor = namingLens.lookupDescriptor(annotationType);
    String classifier = DescriptorUtils.descriptorToKotlinClassifier(descriptor.toString());
    KmAnnotation annotation = new KmAnnotation(classifier, arguments);
    visitorProvider.get(annotation);
  }
}
