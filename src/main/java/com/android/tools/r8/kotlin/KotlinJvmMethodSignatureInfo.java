// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.jvm.JvmMethodSignature;

/**
 * The JvmSignature for a method or property does not always correspond to the actual signature, see
 * b/154201250. We therefore need to model the signature as well.
 */
public class KotlinJvmMethodSignatureInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinTypeReference> EMPTY_PARAMETERS_LIST = ImmutableList.of();

  private final String name;
  private final KotlinTypeReference returnType;
  private final List<KotlinTypeReference> parameters;
  private final String invalidDescriptor;

  private KotlinJvmMethodSignatureInfo(
      String name, KotlinTypeReference returnType, List<KotlinTypeReference> parameters) {
    this.name = name;
    this.returnType = returnType;
    this.parameters = parameters;
    this.invalidDescriptor = null;
  }

  private KotlinJvmMethodSignatureInfo(String name, String invalidDescriptor) {
    this.name = name;
    this.invalidDescriptor = invalidDescriptor;
    this.parameters = EMPTY_PARAMETERS_LIST;
    this.returnType = null;
  }

  public static KotlinJvmMethodSignatureInfo create(
      JvmMethodSignature methodSignature, DexItemFactory factory) {
    if (methodSignature == null) {
      return null;
    }
    String name = methodSignature.getName();
    String descriptor = methodSignature.getDesc();
    if (!KotlinMetadataUtils.isValidMethodDescriptor(descriptor)) {
      // If the method descriptor is invalid, keep it as invalid.
      return new KotlinJvmMethodSignatureInfo(methodSignature.getName(), descriptor);
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(descriptor);
    KotlinTypeReference returnType =
        KotlinTypeReference.fromDescriptor(returnTypeDescriptor, factory);
    String[] descriptors = DescriptorUtils.getArgumentTypeDescriptors(descriptor);
    if (descriptors.length == 0) {
      return new KotlinJvmMethodSignatureInfo(name, returnType, EMPTY_PARAMETERS_LIST);
    }
    ImmutableList.Builder<KotlinTypeReference> parameters = ImmutableList.builder();
    for (String paramDescriptor : descriptors) {
      parameters.add(KotlinTypeReference.fromDescriptor(paramDescriptor, factory));
    }
    return new KotlinJvmMethodSignatureInfo(name, returnType, parameters.build());
  }

  boolean rewrite(
      Consumer<JvmMethodSignature> consumer, DexEncodedMethod method, AppView<?> appView) {
    if (invalidDescriptor != null) {
      consumer.accept(new JvmMethodSignature(name, invalidDescriptor));
      return false;
    }
    assert returnType != null;
    String finalName = name;
    boolean rewritten = false;
    if (method != null) {
      String methodName = method.getReference().name.toString();
      String rewrittenName = appView.getNamingLens().lookupName(method.getReference()).toString();
      if (!methodName.equals(rewrittenName)) {
        finalName = rewrittenName;
        rewritten = true;
      }
    }
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    String defValue = appView.dexItemFactory().objectType.toDescriptorString();
    for (KotlinTypeReference parameter : parameters) {
      rewritten |= parameter.toRenamedDescriptorOrDefault(descBuilder::append, appView, defValue);
    }
    descBuilder.append(")");
    rewritten |= returnType.toRenamedDescriptorOrDefault(descBuilder::append, appView, defValue);
    consumer.accept(new JvmMethodSignature(finalName, descBuilder.toString()));
    return rewritten;
  }

  @Override
  public String toString() {
    if (invalidDescriptor != null) {
      return name + "(" + invalidDescriptor + ")";
    }
    assert returnType != null;
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append(name);
    descBuilder.append("(");
    for (KotlinTypeReference parameter : parameters) {
      descBuilder.append(parameter.toString());
    }
    descBuilder.append(")");
    descBuilder.append(returnType.toString());
    return descBuilder.toString();
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (invalidDescriptor != null) {
      return;
    }
    assert returnType != null;
    returnType.trace(definitionSupplier);
    forEachApply(parameters, param -> param::trace, definitionSupplier);
  }
}
