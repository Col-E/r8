// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.jvm.JvmMethodSignature;

/**
 * The JvmSignature for a method or property does not always correspond to the actual signature, see
 * b/154201250. We therefore need to model the signature as well.
 */
public class KotlinJvmMethodSignatureInfo {

  private static final List<KotlinTypeReference> EMPTY_PARAMETERS_LIST = ImmutableList.of();

  private final String name;
  private final KotlinTypeReference returnType;
  private final List<KotlinTypeReference> parameters;

  private KotlinJvmMethodSignatureInfo(
      String name, KotlinTypeReference returnType, List<KotlinTypeReference> parameters) {
    this.name = name;
    this.returnType = returnType;
    this.parameters = parameters;
  }

  public static KotlinJvmMethodSignatureInfo create(
      JvmMethodSignature methodSignature, DexDefinitionSupplier definitionSupplier) {
    if (methodSignature == null) {
      return null;
    }
    String kotlinDescriptor = methodSignature.getDesc();
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(kotlinDescriptor);
    KotlinTypeReference returnType =
        KotlinTypeReference.createFromDescriptor(returnTypeDescriptor, definitionSupplier);
    String[] descriptors = DescriptorUtils.getArgumentTypeDescriptors(kotlinDescriptor);
    if (descriptors.length == 0) {
      return new KotlinJvmMethodSignatureInfo(
          methodSignature.getName(), returnType, EMPTY_PARAMETERS_LIST);
    }
    ImmutableList.Builder<KotlinTypeReference> parameters = ImmutableList.builder();
    for (String descriptor : descriptors) {
      parameters.add(KotlinTypeReference.createFromDescriptor(descriptor, definitionSupplier));
    }
    return new KotlinJvmMethodSignatureInfo(
        methodSignature.getName(), returnType, parameters.build());
  }

  public JvmMethodSignature rewrite(
      DexEncodedMethod method, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
    String finalName = name;
    if (method != null) {
      String methodName = method.method.name.toString();
      String rewrittenName = namingLens.lookupName(method.method).toString();
      if (!methodName.equals(rewrittenName)) {
        finalName = rewrittenName;
      }
    }
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    String defValue = appView.dexItemFactory().objectType.toDescriptorString();
    for (KotlinTypeReference parameter : parameters) {
      descBuilder.append(parameter.toRenamedDescriptorOrDefault(appView, namingLens, defValue));
    }
    descBuilder.append(")");
    descBuilder.append(returnType.toRenamedDescriptorOrDefault(appView, namingLens, defValue));
    return new JvmMethodSignature(finalName, descBuilder.toString());
  }
}
