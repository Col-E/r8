// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getCompatibleKotlinInfo;
import static kotlinx.metadata.jvm.KotlinClassMetadata.Companion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.Pair;
import kotlin.Metadata;
import kotlinx.metadata.KmLambda;
import kotlinx.metadata.jvm.KotlinClassMetadata.SyntheticClass;

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
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    if (lambda == null) {
      return Pair.create(
          Companion.writeSyntheticClass(getCompatibleKotlinInfo(), 0).getAnnotationData(), false);
    }
    Box<KmLambda> newLambda = new Box<>();
    boolean rewritten = lambda.rewrite(newLambda::set, clazz, appView);
    assert newLambda.isSet();
    return Pair.create(
        Companion.writeLambda(newLambda.get(), getCompatibleKotlinInfo(), 0).getAnnotationData(),
        rewritten);
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

  @SuppressWarnings("ReferenceEquality")
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
