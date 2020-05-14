// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.referenceTypeFromDescriptor;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import kotlinx.metadata.KmClassifier;
import kotlinx.metadata.KmClassifier.TypeAlias;
import kotlinx.metadata.KmClassifier.TypeParameter;
import kotlinx.metadata.KmTypeVisitor;

public abstract class KotlinClassifierInfo {

  public static KotlinClassifierInfo create(
      KmClassifier classifier, DexDefinitionSupplier definitionSupplier, Reporter reporter) {
    if (classifier instanceof KmClassifier.Class) {
      String originalTypeName = ((KmClassifier.Class) classifier).getName();
      // If this name starts with '.', it represents a local class or an anonymous object. This is
      // used by the Kotlin compiler to prevent lookup of this name in the resolution:
      // .kotlin/random/FallbackThreadLocalRandom$implStorage$1
      boolean isLocalOrAnonymous = originalTypeName.startsWith(".");
      String descriptor =
          DescriptorUtils.getDescriptorFromKotlinClassifier(
              isLocalOrAnonymous ? originalTypeName.substring(1) : originalTypeName);
      if (DescriptorUtils.isClassDescriptor(descriptor)) {
        return new KotlinClassClassifierInfo(
            referenceTypeFromDescriptor(descriptor, definitionSupplier), isLocalOrAnonymous);
      } else {
        return new KotlinUnknownClassClassifierInfo(originalTypeName);
      }
    } else if (classifier instanceof KmClassifier.TypeAlias) {
      return new KotlinTypeAliasClassifierInfo(((TypeAlias) classifier).getName());
    } else if (classifier instanceof KmClassifier.TypeParameter) {
      return new KotlinTypeParameterClassifierInfo(((TypeParameter) classifier).getId());
    } else {
      reporter.warning(KotlinMetadataDiagnostic.unknownClassifier(classifier.toString()));
      return new KotlinUnknownClassifierInfo(classifier.toString());
    }
  }

  abstract void rewrite(
      KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens);

  public static class KotlinClassClassifierInfo extends KotlinClassifierInfo {

    private final DexType type;
    private final boolean isLocalOrAnonymous;

    private KotlinClassClassifierInfo(DexType type, boolean isLocalOrAnonymous) {
      this.type = type;
      this.isLocalOrAnonymous = isLocalOrAnonymous;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      if (appView.appInfo().wasPruned(type)) {
        visitor.visitClass(ClassClassifiers.anyName);
        return;
      }
      DexString descriptor = namingLens.lookupDescriptor(type);
      // For local or anonymous classes, the classifier is prefixed with '.' and inner classes are
      // separated with '$'.
      if (isLocalOrAnonymous) {
        visitor.visitClass(
            "." + DescriptorUtils.getBinaryNameFromDescriptor(descriptor.toString()));
      } else {
        visitor.visitClass(DescriptorUtils.descriptorToKotlinClassifier(descriptor.toString()));
      }
    }
  }

  public static class KotlinTypeParameterClassifierInfo extends KotlinClassifierInfo {

    private final int typeId;

    private KotlinTypeParameterClassifierInfo(int typeId) {
      this.typeId = typeId;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      visitor.visitTypeParameter(typeId);
    }
  }

  public static class KotlinTypeAliasClassifierInfo extends KotlinClassifierInfo {

    private final String typeAlias;

    private KotlinTypeAliasClassifierInfo(String typeAlias) {
      this.typeAlias = typeAlias;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      visitor.visitTypeAlias(typeAlias);
    }
  }

  public static class KotlinUnknownClassClassifierInfo extends KotlinClassifierInfo {
    private final String classifier;

    private KotlinUnknownClassClassifierInfo(String classifier) {
      this.classifier = classifier;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      visitor.visitClass(classifier);
    }
  }

  public static class KotlinUnknownClassifierInfo extends KotlinClassifierInfo {
    private final String classifier;

    private KotlinUnknownClassifierInfo(String classifier) {
      this.classifier = classifier;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      visitor.visitTypeAlias(classifier);
    }
  }
}
