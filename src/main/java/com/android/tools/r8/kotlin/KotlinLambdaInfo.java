// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.consume;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import java.util.function.Consumer;
import kotlinx.metadata.KmLambda;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;

// Holds information about a KmLambda
public class KotlinLambdaInfo implements EnqueuerMetadataTraceable {

  private final KotlinFunctionInfo function;
  private final boolean hasBacking;

  private KotlinLambdaInfo(KotlinFunctionInfo function, boolean hasBacking) {
    this.function = function;
    this.hasBacking = hasBacking;
  }

  static KotlinLambdaInfo create(
      DexClass clazz, KmLambda lambda, DexItemFactory factory, Reporter reporter) {
    if (lambda == null) {
      assert false;
      return null;
    }
    KotlinFunctionInfo kotlinFunctionInfo =
        KotlinFunctionInfo.create(lambda.function, factory, reporter);
    JvmMethodSignature signature = JvmExtensionsKt.getSignature(lambda.function);
    if (signature != null) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (toJvmMethodSignature(method.getReference()).asString().equals(signature.asString())) {
          method.setKotlinMemberInfo(kotlinFunctionInfo);
          return new KotlinLambdaInfo(kotlinFunctionInfo, true);
        }
      }
    }
    return new KotlinLambdaInfo(kotlinFunctionInfo, false);
  }

  boolean rewrite(Consumer<KmLambda> consumer, DexClass clazz, AppView<?> appView) {
    KmLambda kmLambda = consume(new KmLambda(), consumer);
    if (!hasBacking) {
      return function.rewrite(kmLambda::setFunction, null, appView);
    }
    DexEncodedMethod backing = null;
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo() == function) {
        backing = method;
        break;
      }
    }
    return function.rewrite(kmLambda::setFunction, backing, appView);
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    function.trace(definitionSupplier);
  }
}
