// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
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
        if (toJvmMethodSignature(method.method).asString().equals(signature.asString())) {
          method.setKotlinMemberInfo(kotlinFunctionInfo);
          return new KotlinLambdaInfo(kotlinFunctionInfo, true);
        }
      }
    }
    return new KotlinLambdaInfo(kotlinFunctionInfo, false);
  }

  boolean rewrite(
      KmVisitorProviders.KmLambdaVisitorProvider visitorProvider,
      DexClass clazz,
      AppView<?> appView,
      NamingLens namingLens) {
    if (!hasBacking) {
      function.rewrite(visitorProvider.get()::visitFunction, null, appView, namingLens);
      return true;
    }
    DexEncodedMethod backing = null;
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinMemberInfo() == function) {
        backing = method;
        break;
      }
    }
    if (backing == null) {
      appView
          .options()
          .reporter
          .info(
              KotlinMetadataDiagnostic.lambdaBackingNotFound(clazz.type, function.getSignature()));
      return false;
    }
    function.rewrite(visitorProvider.get()::visitFunction, backing, appView, namingLens);
    return true;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    function.trace(definitionSupplier);
  }
}
