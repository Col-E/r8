// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Pair;
import kotlinx.metadata.KmLambda;
import kotlinx.metadata.jvm.KotlinClassHeader;
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
      AppView<?> appView) {
    KmLambda lambda = syntheticClass.toKmLambda();
    assert lambda == null || syntheticClass.isLambda();
    KotlinJvmSignatureExtensionInformation extensionInformation =
        KotlinJvmSignatureExtensionInformation.readInformationFromMessage(
            syntheticClass, appView.options());
    return new KotlinSyntheticClassInfo(
        lambda != null
            ? KotlinLambdaInfo.create(
                clazz, lambda, appView.dexItemFactory(), appView.reporter(), extensionInformation)
            : null,
        getFlavour(clazz, kotlin),
        packageName,
        metadataVersion);
  }

  public boolean isLambda() {
    return lambda != null && flavour != Flavour.Unclassified;
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
  public Pair<KotlinClassHeader, Boolean> rewrite(
      DexClass clazz, AppView<?> appView, NamingLens namingLens) {
    Writer writer = new Writer();
    boolean rewritten = false;
    if (lambda != null) {
      KmLambda kmLambda = new KmLambda();
      rewritten = lambda.rewrite(() -> kmLambda, clazz, appView, namingLens);
      kmLambda.accept(writer);
    }
    return Pair.create(writer.write().getHeader(), rewritten);
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

  public static Flavour getFlavour(DexClass clazz, Kotlin kotlin) {
    // Returns KotlinStyleLambda if the given clazz has shape of a Kotlin-style lambda:
    //   a class that directly extends kotlin.jvm.internal.Lambda
    if (clazz.superType == kotlin.functional.lambdaType) {
      return Flavour.KotlinStyleLambda;
    }
    // Returns JavaStyleLambda if the given clazz has shape of a Java-style lambda:
    //  a class that
    //    1) doesn't extend any other class;
    //    2) directly implements only one Java SAM.
    if (clazz.superType == kotlin.factory.objectType && clazz.interfaces.size() == 1) {
      return Flavour.JavaStyleLambda;
    }
    return Flavour.Unclassified;
  }
}
