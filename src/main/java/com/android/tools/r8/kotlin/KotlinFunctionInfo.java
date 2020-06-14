// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor;

// Holds information about KmFunction
public final class KotlinFunctionInfo implements KotlinMethodLevelInfo {
  // Original flags
  private final int flags;
  // Original name;
  private final String name;
  // Information from original KmValueParameter(s) if available.
  private final List<KotlinValueParameterInfo> valueParameters;
  // Information from original KmFunction.returnType. Null if this is from a KmConstructor.
  public final KotlinTypeInfo returnType;
  // Information from original KmFunction.receiverType. Null if this is from a KmConstructor.
  private final KotlinTypeInfo receiverParameterType;
  // Information about original type parameters. Null if this is from a KmConstructor.
  private final List<KotlinTypeParameterInfo> typeParameters;
  // Information about the signature
  private final KotlinJvmMethodSignatureInfo signature;
  // Information about the lambdaClassOrigin.
  private final KotlinTypeReference lambdaClassOrigin;
  // Information about version requirements.
  private final KotlinVersionRequirementInfo versionRequirements;
  // Kotlin contract information.
  private final KotlinContractInfo contract;
  // A value describing if any of the parameters are crossinline.
  private final boolean crossInlineParameter;

  private KotlinFunctionInfo(
      int flags,
      String name,
      KotlinTypeInfo returnType,
      KotlinTypeInfo receiverParameterType,
      List<KotlinValueParameterInfo> valueParameters,
      List<KotlinTypeParameterInfo> typeParameters,
      KotlinJvmMethodSignatureInfo signature,
      KotlinTypeReference lambdaClassOrigin,
      KotlinVersionRequirementInfo versionRequirements,
      KotlinContractInfo contract,
      boolean crossInlineParameter) {
    this.flags = flags;
    this.name = name;
    this.returnType = returnType;
    this.receiverParameterType = receiverParameterType;
    this.valueParameters = valueParameters;
    this.typeParameters = typeParameters;
    this.signature = signature;
    this.lambdaClassOrigin = lambdaClassOrigin;
    this.versionRequirements = versionRequirements;
    this.contract = contract;
    this.crossInlineParameter = crossInlineParameter;
  }

  public boolean hasCrossInlineParameter() {
    return crossInlineParameter;
  }

  static KotlinFunctionInfo create(
      KmFunction kmFunction, DexItemFactory factory, Reporter reporter) {
    boolean isCrossInline = false;
    List<KotlinValueParameterInfo> valueParameters =
        KotlinValueParameterInfo.create(kmFunction.getValueParameters(), factory, reporter);
    for (KotlinValueParameterInfo valueParameter : valueParameters) {
      if (valueParameter.isCrossInline()) {
        isCrossInline = true;
        break;
      }
    }
    return new KotlinFunctionInfo(
        kmFunction.getFlags(),
        kmFunction.getName(),
        KotlinTypeInfo.create(kmFunction.getReturnType(), factory, reporter),
        KotlinTypeInfo.create(kmFunction.getReceiverParameterType(), factory, reporter),
        valueParameters,
        KotlinTypeParameterInfo.create(kmFunction.getTypeParameters(), factory, reporter),
        KotlinJvmMethodSignatureInfo.create(JvmExtensionsKt.getSignature(kmFunction), factory),
        getlambdaClassOrigin(kmFunction, factory),
        KotlinVersionRequirementInfo.create(kmFunction.getVersionRequirements()),
        KotlinContractInfo.create(kmFunction.getContract(), factory, reporter),
        isCrossInline);
  }

  private static KotlinTypeReference getlambdaClassOrigin(
      KmFunction kmFunction, DexItemFactory factory) {
    String lambdaClassOriginName = JvmExtensionsKt.getLambdaClassOriginName(kmFunction);
    if (lambdaClassOriginName != null) {
      return KotlinTypeReference.fromBinaryName(lambdaClassOriginName, factory);
    }
    return null;
  }

  public void rewrite(
      KmVisitorProviders.KmFunctionVisitorProvider visitorProvider,
      DexEncodedMethod method,
      AppView<?> appView,
      NamingLens namingLens) {
    // TODO(b/154348683): Check method for flags to pass in.
    String finalName = this.name;
    if (method != null) {
      String methodName = method.method.name.toString();
      String rewrittenName = namingLens.lookupName(method.method).toString();
      if (!methodName.equals(rewrittenName)) {
        finalName = rewrittenName;
      }
    }
    KmFunctionVisitor kmFunction = visitorProvider.get(flags, finalName);
    // TODO(b/154348149): ReturnType could have been merged to a subtype.
    returnType.rewrite(kmFunction::visitReturnType, appView, namingLens);
    for (KotlinValueParameterInfo valueParameterInfo : valueParameters) {
      valueParameterInfo.rewrite(kmFunction::visitValueParameter, appView, namingLens);
    }
    for (KotlinTypeParameterInfo typeParameterInfo : typeParameters) {
      typeParameterInfo.rewrite(kmFunction::visitTypeParameter, appView, namingLens);
    }
    if (receiverParameterType != null) {
      receiverParameterType.rewrite(kmFunction::visitReceiverParameterType, appView, namingLens);
    }
    versionRequirements.rewrite(kmFunction::visitVersionRequirement);
    JvmFunctionExtensionVisitor extensionVisitor =
        (JvmFunctionExtensionVisitor) kmFunction.visitExtensions(JvmFunctionExtensionVisitor.TYPE);
    if (signature != null && extensionVisitor != null) {
      extensionVisitor.visit(signature.rewrite(method, appView, namingLens));
    }
    if (lambdaClassOrigin != null && extensionVisitor != null) {
      String lambdaClassOriginName =
          lambdaClassOrigin.toRenamedBinaryNameOrDefault(appView, namingLens, null);
      if (lambdaClassOriginName != null) {
        extensionVisitor.visitLambdaClassOriginName(lambdaClassOriginName);
      }
    }
    contract.rewrite(kmFunction::visitContract, appView, namingLens);
  }

  @Override
  public boolean isFunction() {
    return true;
  }

  @Override
  public KotlinFunctionInfo asFunction() {
    return this;
  }

  public boolean isExtensionFunction() {
    return receiverParameterType != null;
  }

  public KotlinJvmMethodSignatureInfo getSignature() {
    return signature;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(valueParameters, param -> param::trace, definitionSupplier);
    returnType.trace(definitionSupplier);
    if (receiverParameterType != null) {
      receiverParameterType.trace(definitionSupplier);
    }
    forEachApply(typeParameters, param -> param::trace, definitionSupplier);
    if (signature != null) {
      signature.trace(definitionSupplier);
    }
    if (lambdaClassOrigin != null) {
      lambdaClassOrigin.trace(definitionSupplier);
    }
    contract.trace(definitionSupplier);
  }
}
