// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexClass;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmLambdaVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public final class KotlinSyntheticClass extends KotlinInfo<KotlinClassMetadata.SyntheticClass> {
  public enum Flavour {
    KotlinStyleLambda,
    JavaStyleLambda,
    Unclassified
  }

  private final Flavour flavour;

  static KotlinSyntheticClass fromKotlinClassMetadata(
      KotlinClassMetadata kotlinClassMetadata, Kotlin kotlin, DexClass clazz) {
    assert kotlinClassMetadata instanceof KotlinClassMetadata.SyntheticClass;
    KotlinClassMetadata.SyntheticClass syntheticClass =
        (KotlinClassMetadata.SyntheticClass) kotlinClassMetadata;
    if (isKotlinStyleLambda(syntheticClass, kotlin, clazz)) {
      return new KotlinSyntheticClass(Flavour.KotlinStyleLambda, syntheticClass);
    } else if (isJavaStyleLambda(syntheticClass, kotlin, clazz)) {
      return new KotlinSyntheticClass(Flavour.JavaStyleLambda, syntheticClass);
    } else {
      return new KotlinSyntheticClass(Flavour.Unclassified, syntheticClass);
    }
  }

  private KotlinSyntheticClass(Flavour flavour, KotlinClassMetadata.SyntheticClass metadata) {
    super(metadata);
    this.flavour = flavour;
  }

  @Override
  void processMetadata(KotlinClassMetadata.SyntheticClass metadata) {
    if (metadata.isLambda()) {
      // To avoid lazy parsing/verifying metadata.
      metadata.accept(new LambdaVisitorForNonNullParameterHints());
    }
  }

  private class LambdaVisitorForNonNullParameterHints extends KmLambdaVisitor {
    @Override
    public KmFunctionVisitor visitFunction(int functionFlags, String functionName) {
      return new NonNullParameterHintCollector.FunctionVisitor(nonNullparamHints);
    }
  }

  public boolean isLambda() {
    return isKotlinStyleLambda() || isJavaStyleLambda();
  }

  public boolean isKotlinStyleLambda() {
    return flavour == Flavour.KotlinStyleLambda;
  }

  public boolean isJavaStyleLambda() {
    return flavour == Flavour.JavaStyleLambda;
  }

  @Override
  public final Kind getKind() {
    return Kind.Synthetic;
  }

  @Override
  public final boolean isSyntheticClass() {
    return true;
  }

  @Override
  public KotlinSyntheticClass asSyntheticClass() {
    return this;
  }

  /**
   * Returns {@code true} if the given {@link DexClass} is a Kotlin-style lambda:
   *   a class that
   *     1) is recognized as lambda in its Kotlin metadata;
   *     2) directly extends kotlin.jvm.internal.Lambda
   */
  private static boolean isKotlinStyleLambda(
      KotlinClassMetadata.SyntheticClass metadata, Kotlin kotlin, DexClass clazz) {
    return metadata.isLambda()
        && clazz.superType == kotlin.functional.lambdaType;
  }

  /**
   * Returns {@code true} if the given {@link DexClass} is a Java-style lambda:
   *   a class that
   *     1) is recognized as lambda in its Kotlin metadata;
   *     2) doesn't extend any other class;
   *     3) directly implements only one Java SAM.
   */
  private static boolean isJavaStyleLambda(
      KotlinClassMetadata.SyntheticClass metadata, Kotlin kotlin, DexClass clazz) {
    return metadata.isLambda()
        && clazz.superType == kotlin.factory.objectType
        && clazz.interfaces.size() == 1;
  }

}
