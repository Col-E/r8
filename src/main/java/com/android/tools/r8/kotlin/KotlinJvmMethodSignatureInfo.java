// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toRenamedDescriptorOrDefault;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
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

  private static final List<DexType> EMPTY_PARAMETERS_LIST = ImmutableList.of();

  private final String name;
  private final DexType returnType;
  private final List<DexType> parameters;

  private KotlinJvmMethodSignatureInfo(String name, DexType returnType, List<DexType> parameters) {
    this.name = name;
    this.returnType = returnType;
    this.parameters = parameters;
  }

  public static KotlinJvmMethodSignatureInfo create(
      JvmMethodSignature methodSignature, AppView<?> appView) {
    if (methodSignature == null) {
      return null;
    }
    String kotlinDescriptor = methodSignature.getDesc();
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(kotlinDescriptor);
    DexItemFactory factory = appView.dexItemFactory();
    DexType returnType = factory.createType(returnTypeDescriptor);
    String[] descriptors = DescriptorUtils.getArgumentTypeDescriptors(kotlinDescriptor);
    if (descriptors.length == 0) {
      return new KotlinJvmMethodSignatureInfo(
          methodSignature.getName(), returnType, EMPTY_PARAMETERS_LIST);
    }
    ImmutableList.Builder<DexType> parameters = ImmutableList.builder();
    for (String descriptor : descriptors) {
      parameters.add(factory.createType(descriptor));
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
    for (DexType parameter : parameters) {
      descBuilder.append(toRenamedDescriptorOrDefault(parameter, appView, namingLens, defValue));
    }
    descBuilder.append(")");
    descBuilder.append(toRenamedDescriptorOrDefault(returnType, appView, namingLens, defValue));
    return new JvmMethodSignature(finalName, descBuilder.toString());
  }
}
