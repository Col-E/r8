// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
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

  private final KotlinTypeReference annotationType;
  // TODO(b/155053894): Model KmAnnotationArgument.
  private final Map<String, KmAnnotationArgument<?>> arguments;

  private KotlinAnnotationInfo(
      KotlinTypeReference annotationType, Map<String, KmAnnotationArgument<?>> arguments) {
    this.annotationType = annotationType;
    this.arguments = arguments;
  }

  private static KotlinAnnotationInfo create(
      KmAnnotation annotation, DexDefinitionSupplier definitionSupplier) {
    return new KotlinAnnotationInfo(
        KotlinTypeReference.createFromBinaryName(annotation.getClassName(), definitionSupplier),
        annotation.getArguments());
  }

  static List<KotlinAnnotationInfo> create(
      List<KmAnnotation> annotations, DexDefinitionSupplier definitionSupplier) {
    if (annotations.isEmpty()) {
      return EMPTY_ANNOTATIONS;
    }
    ImmutableList.Builder<KotlinAnnotationInfo> builder = ImmutableList.builder();
    for (KmAnnotation annotation : annotations) {
      builder.add(create(annotation, definitionSupplier));
    }
    return builder.build();
  }

  public void rewrite(
      KmVisitorProviders.KmAnnotationVisitorProvider visitorProvider,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    String renamedDescriptor =
        annotationType.toRenamedDescriptorOrDefault(appView, namingLens, null);
    if (renamedDescriptor == null) {
      // The type has been pruned
      return;
    }
    String classifier = DescriptorUtils.descriptorToKotlinClassifier(renamedDescriptor);
    KmAnnotation annotation = new KmAnnotation(classifier, arguments);
    visitorProvider.get(annotation);
  }
}
