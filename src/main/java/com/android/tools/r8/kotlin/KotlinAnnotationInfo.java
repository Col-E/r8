// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;

// Holds information about a KmAnnotation
public class KotlinAnnotationInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinAnnotationInfo> EMPTY_ANNOTATIONS = ImmutableList.of();

  private final KotlinTypeReference annotationType;
  private final Map<String, KotlinAnnotationArgumentInfo> arguments;

  private KotlinAnnotationInfo(
      KotlinTypeReference annotationType, Map<String, KotlinAnnotationArgumentInfo> arguments) {
    this.annotationType = annotationType;
    this.arguments = arguments;
  }

  static KotlinAnnotationInfo create(KmAnnotation annotation, DexItemFactory factory) {
    return new KotlinAnnotationInfo(
        KotlinTypeReference.fromBinaryName(annotation.getClassName(), factory),
        KotlinAnnotationArgumentInfo.create(annotation.getArguments(), factory));
  }

  static List<KotlinAnnotationInfo> create(List<KmAnnotation> annotations, DexItemFactory factory) {
    if (annotations.isEmpty()) {
      return EMPTY_ANNOTATIONS;
    }
    ImmutableList.Builder<KotlinAnnotationInfo> builder = ImmutableList.builder();
    for (KmAnnotation annotation : annotations) {
      builder.add(create(annotation, factory));
    }
    return builder.build();
  }

  public void rewrite(
      KmVisitorProviders.KmAnnotationVisitorProvider visitorProvider,
      AppView<?> appView,
      NamingLens namingLens) {
    String renamedDescriptor =
        annotationType.toRenamedDescriptorOrDefault(appView, namingLens, null);
    if (renamedDescriptor == null) {
      // The type has been pruned
      return;
    }
    String classifier = DescriptorUtils.descriptorToKotlinClassifier(renamedDescriptor);
    Map<String, KmAnnotationArgument<?>> rewrittenArguments = new LinkedHashMap<>();
    arguments.forEach(
        (key, arg) -> {
          KmAnnotationArgument<?> rewrittenArg = arg.rewrite(appView, namingLens);
          if (rewrittenArg != null) {
            rewrittenArguments.put(key, rewrittenArg);
          }
        });
    visitorProvider.get(new KmAnnotation(classifier, rewrittenArguments));
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    annotationType.trace(definitionSupplier);
    arguments.forEach((ignored, arg) -> arg.trace(definitionSupplier));
  }
}
