// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaStructureError;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroupId;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

final class JStyleLambdaGroupIdFactory extends KotlinLambdaGroupIdFactory {
  static final JStyleLambdaGroupIdFactory INSTANCE = new JStyleLambdaGroupIdFactory();

  @Override
  LambdaGroupId validateAndCreate(
      AppView<AppInfoWithLiveness> appView, Kotlin kotlin, DexClass lambda)
      throws LambdaStructureError {
    boolean accessRelaxed =
        appView.options().getProguardConfiguration().isAccessModificationAllowed();

    assert lambda.getKotlinInfo().isSyntheticClass();
    assert lambda.getKotlinInfo().asSyntheticClass().isJavaStyleLambda();

    // Ignore ACC_SUPER.
    ClassAccessFlags copy = lambda.accessFlags.copy();
    copy.unsetSuper();
    checkAccessFlags("class access flags", copy, PUBLIC_LAMBDA_CLASS_FLAGS, LAMBDA_CLASS_FLAGS);

    // Class and interface.
    validateSuperclass(kotlin, lambda);
    DexType iface = validateInterfaces(kotlin, lambda);

    validateStaticFields(kotlin, lambda);
    String captureSignature = validateInstanceFields(lambda, accessRelaxed);
    validateDirectMethods(lambda);
    DexEncodedMethod mainMethod = validateVirtualMethods(lambda);
    String genericSignature = validateAnnotations(appView, kotlin, lambda);
    InnerClassAttribute innerClass = validateInnerClasses(lambda);

    return new JStyleLambdaGroup.GroupId(
        appView,
        captureSignature,
        iface,
        accessRelaxed ? "" : lambda.type.getPackageDescriptor(),
        genericSignature,
        mainMethod,
        innerClass,
        lambda.getEnclosingMethodAttribute());
  }

  @Override
  void validateSuperclass(Kotlin kotlin, DexClass lambda) throws LambdaStructureError {
    if (lambda.superType != kotlin.factory.objectType) {
      throw new LambdaStructureError("implements " + lambda.superType.toSourceString() +
          " instead of java.lang.Object");
    }
  }

  @Override
  DexType validateInterfaces(Kotlin kotlin, DexClass lambda) throws LambdaStructureError {
    if (lambda.interfaces.size() == 0) {
      throw new LambdaStructureError("does not implement any interfaces");
    }
    if (lambda.interfaces.size() > 1) {
      throw new LambdaStructureError(
          "implements more than one interface: " + lambda.interfaces.size());
    }

    // We don't validate that the interface is actually a functional interface,
    // since it may be desugared, or optimized in any other way which should not
    // affect lambda class merging.
    return lambda.interfaces.values[0];
  }
}
