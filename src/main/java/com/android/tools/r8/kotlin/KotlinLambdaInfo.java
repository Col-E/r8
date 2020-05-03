// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import kotlinx.metadata.KmLambda;
import kotlinx.metadata.KmLambdaVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;

// Holds information about a KmLambda
public class KotlinLambdaInfo {

  private final KotlinFunctionInfo function;

  private KotlinLambdaInfo(KotlinFunctionInfo function) {
    this.function = function;
  }

  static KotlinLambdaInfo create(DexClass clazz, KmLambda lambda, AppView<?> appView) {
    if (lambda == null) {
      assert false;
      return null;
    }
    JvmMethodSignature signature = JvmExtensionsKt.getSignature(lambda.function);
    if (signature == null) {
      assert false;
      return null;
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (toJvmMethodSignature(method.method).asString().equals(signature.asString())) {
        KotlinFunctionInfo kotlinFunctionInfo = KotlinFunctionInfo.create(lambda.function, appView);
        method.setKotlinMemberInfo(kotlinFunctionInfo);
        return new KotlinLambdaInfo(kotlinFunctionInfo);
      }
    }
    // TODO(b/155536535): Resolve this assert for NestTreeShakeJarVerificationTest.
    // assert false;
    return null;
  }

  boolean rewrite(
      KmVisitorProviders.KmLambdaVisitorProvider visitorProvider,
      DexClass clazz,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinMemberInfo() == function) {
        KmLambdaVisitor kmLambdaVisitor = visitorProvider.get();
        function.rewrite(kmLambdaVisitor::visitFunction, method, appView, namingLens);
        return true;
      }
    }
    return false;
  }
}
