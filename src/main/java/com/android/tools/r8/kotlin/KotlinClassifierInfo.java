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
import kotlinx.metadata.KmClassifier;
import kotlinx.metadata.KmClassifier.TypeAlias;
import kotlinx.metadata.KmClassifier.TypeParameter;
import kotlinx.metadata.KmTypeVisitor;

public abstract class KotlinClassifierInfo {

  public static KotlinClassifierInfo create(KmClassifier classifier, AppView<?> appView) {
    if (classifier instanceof KmClassifier.Class) {
      String typeName = ((KmClassifier.Class) classifier).getName();
      // If this name starts with '.', it represents a local class or an anonymous object. This is
      // used by the Kotlin compiler to prevent lookup of this name in the resolution:
      // .kotlin/random/FallbackThreadLocalRandom$implStorage$1
      if (typeName.startsWith(".")) {
        return new KotlinUnknownClassClassifierInfo(typeName);
      }
      String descriptor = DescriptorUtils.getDescriptorFromKotlinClassifier(typeName);
      if (DescriptorUtils.isClassDescriptor(descriptor)) {
        DexType type = appView.dexItemFactory().createType(descriptor);
        return new KotlinClassClassifierInfo(type);
      } else {
        return new KotlinUnknownClassClassifierInfo(typeName);
      }
    } else if (classifier instanceof KmClassifier.TypeAlias) {
      return new KotlinTypeAliasClassifierInfo(((TypeAlias) classifier).getName());
    } else if (classifier instanceof KmClassifier.TypeParameter) {
      return new KotlinTypeParameterClassifierInfo(((TypeParameter) classifier).getId());
    } else {
      appView
          .options()
          .reporter
          .warning(KotlinMetadataDiagnostic.unknownClassifier(classifier.toString()));
      return new KotlinUnknownClassifierInfo(classifier.toString());
    }
  }

  abstract void rewrite(
      KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens);

  boolean isLive(AppView<AppInfoWithLiveness> appView) {
    return true;
  }

  public static class KotlinClassClassifierInfo extends KotlinClassifierInfo {

    private final DexType type;

    private KotlinClassClassifierInfo(DexType type) {
      this.type = type;
    }

    @Override
    void rewrite(
        KmTypeVisitor visitor, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      assert isLive(appView);
      DexString descriptor = namingLens.lookupDescriptor(type);
      String classifier = DescriptorUtils.descriptorToKotlinClassifier(descriptor.toString());
      visitor.visitClass(classifier);
    }

    @Override
    boolean isLive(AppView<AppInfoWithLiveness> appView) {
      return !appView.appInfo().wasPruned(type);
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
