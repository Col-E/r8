// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Reporter;
import kotlinx.metadata.KmLambda;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import kotlinx.metadata.jvm.KotlinClassMetadata.SyntheticClass;
import kotlinx.metadata.jvm.KotlinClassMetadata.SyntheticClass.Writer;

// Holds information about a Metadata.SyntheticClass object.
public class KotlinSyntheticClassInfo implements KotlinClassLevelInfo {

  private final KotlinLambdaInfo lambda;
  private final String packageName;
  private final int[] metadataVersion;

  public enum Flavour {
    KotlinStyleLambda,
    JavaStyleLambda,
    Unclassified
  }

  private final Flavour flavour;

  private KotlinSyntheticClassInfo(
      KotlinLambdaInfo lambda, Flavour flavour, String packageName, int[] metadataVersion) {
    this.lambda = lambda;
    this.flavour = flavour;
    this.packageName = packageName;
    this.metadataVersion = metadataVersion;
  }

  static KotlinSyntheticClassInfo create(
      SyntheticClass syntheticClass,
      String packageName,
      int[] metadataVersion,
      DexClass clazz,
      Kotlin kotlin,
      DexItemFactory factory,
      Reporter reporter) {
    KmLambda lambda = null;
    if (syntheticClass.isLambda()) {
      lambda = syntheticClass.toKmLambda();
      assert lambda != null;
    }
    return new KotlinSyntheticClassInfo(
        lambda != null ? KotlinLambdaInfo.create(clazz, lambda, factory, reporter) : null,
        getFlavour(syntheticClass, clazz, kotlin),
        packageName,
        metadataVersion);
  }

  public boolean isLambda() {
    return lambda != null && flavour != Flavour.Unclassified;
  }

  public boolean isKotlinStyleLambda() {
    return flavour == Flavour.KotlinStyleLambda;
  }

  public boolean isJavaStyleLambda() {
    return flavour == Flavour.JavaStyleLambda;
  }

  @Override
  public boolean isSyntheticClass() {
    return true;
  }

  @Override
  public KotlinSyntheticClassInfo asSyntheticClass() {
    return this;
  }

  @Override
  public KotlinClassHeader rewrite(DexClass clazz, AppView<?> appView, NamingLens namingLens) {
    Writer writer = new Writer();
    if (lambda != null) {
      KmLambda kmLambda = new KmLambda();
      if (lambda.rewrite(() -> kmLambda, clazz, appView, namingLens)) {
        kmLambda.accept(writer);
      }
    }
    return writer.write().getHeader();
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (lambda != null) {
      lambda.trace(definitionSupplier);
    }
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public int[] getMetadataVersion() {
    return metadataVersion;
  }

  public static Flavour getFlavour(
      KotlinClassMetadata.SyntheticClass metadata, DexClass clazz, Kotlin kotlin) {
    // Returns KotlinStyleLambda if the given clazz is a Kotlin-style lambda:
    //   a class that
    //     1) is recognized as lambda in its Kotlin metadata;
    //     2) directly extends kotlin.jvm.internal.Lambda
    if (metadata.isLambda() && clazz.superType == kotlin.functional.lambdaType) {
      return Flavour.KotlinStyleLambda;
    }
    // Returns JavaStyleLambda if the given clazz is a Java-style lambda:
    //  a class that
    //    1) is recognized as lambda in its Kotlin metadata;
    //    2) doesn't extend any other class;
    //    3) directly implements only one Java SAM.
    if (metadata.isLambda()
        && clazz.superType == kotlin.factory.objectType
        && clazz.interfaces.size() == 1) {
      return Flavour.JavaStyleLambda;
    }
    return Flavour.Unclassified;
  }
}
