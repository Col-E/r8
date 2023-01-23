// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.ListUtils;
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
  // Collection of context receiver types
  private final List<KotlinTypeInfo> contextReceiverTypes;

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
      boolean crossInlineParameter,
      List<KotlinTypeInfo> contextReceiverTypes) {
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
    this.contextReceiverTypes = contextReceiverTypes;
  }

  public boolean hasCrossInlineParameter() {
    return crossInlineParameter;
  }

  static KotlinFunctionInfo create(
      KmFunction kmFunction,
      DexItemFactory factory,
      Reporter reporter,
      boolean readMethodSignature) {
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
        readMethodSignature
            ? KotlinJvmMethodSignatureInfo.create(JvmExtensionsKt.getSignature(kmFunction), factory)
            : null,
        getlambdaClassOrigin(kmFunction, factory),
        KotlinVersionRequirementInfo.create(kmFunction.getVersionRequirements()),
        KotlinContractInfo.create(kmFunction.getContract(), factory, reporter),
        isCrossInline,
        ListUtils.map(
            kmFunction.getContextReceiverTypes(),
            contextRecieverType -> KotlinTypeInfo.create(contextRecieverType, factory, reporter)));
  }

  private static KotlinTypeReference getlambdaClassOrigin(
      KmFunction kmFunction, DexItemFactory factory) {
    String lambdaClassOriginName = JvmExtensionsKt.getLambdaClassOriginName(kmFunction);
    if (lambdaClassOriginName != null) {
      return KotlinTypeReference.fromBinaryName(
          lambdaClassOriginName, factory, lambdaClassOriginName);
    }
    return null;
  }

  public String getName() {
    return name;
  }

  boolean rewrite(
      KmVisitorProviders.KmFunctionVisitorProvider visitorProvider,
      DexEncodedMethod method,
      AppView<?> appView) {
    // TODO(b/154348683): Check method for flags to pass in.
    boolean rewritten = false;
    String finalName = name;
    // Only rewrite the kotlin method name if it was equal to the method name when reading the
    // metadata.
    if (method != null) {
      String methodName = method.getReference().name.toString();
      String rewrittenName = appView.getNamingLens().lookupName(method.getReference()).toString();
      if (!methodName.equals(rewrittenName)) {
        rewritten = true;
        finalName = rewrittenName;
      }
    }
    KmFunctionVisitor kmFunction = visitorProvider.get(flags, finalName);
    // TODO(b/154348149): ReturnType could have been merged to a subtype.
    rewritten |= returnType.rewrite(kmFunction::visitReturnType, appView);
    for (KotlinValueParameterInfo valueParameterInfo : valueParameters) {
      rewritten |= valueParameterInfo.rewrite(kmFunction::visitValueParameter, appView);
    }
    for (KotlinTypeParameterInfo typeParameterInfo : typeParameters) {
      rewritten |= typeParameterInfo.rewrite(kmFunction::visitTypeParameter, appView);
    }
    for (KotlinTypeInfo contextReceiverType : contextReceiverTypes) {
      rewritten |= contextReceiverType.rewrite(kmFunction::visitContextReceiverType, appView);
    }
    if (receiverParameterType != null) {
      rewritten |= receiverParameterType.rewrite(kmFunction::visitReceiverParameterType, appView);
    }
    rewritten |= versionRequirements.rewrite(kmFunction::visitVersionRequirement);
    JvmFunctionExtensionVisitor extensionVisitor =
        (JvmFunctionExtensionVisitor) kmFunction.visitExtensions(JvmFunctionExtensionVisitor.TYPE);
    if (signature != null && extensionVisitor != null) {
      rewritten |= signature.rewrite(extensionVisitor::visit, method, appView);
    }
    if (lambdaClassOrigin != null && extensionVisitor != null) {
      rewritten |=
          lambdaClassOrigin.toRenamedBinaryNameOrDefault(
              lambdaClassOriginName -> {
                if (lambdaClassOriginName != null) {
                  extensionVisitor.visitLambdaClassOriginName(lambdaClassOriginName);
                }
              },
              appView,
              null);
    }
    rewritten |= contract.rewrite(kmFunction::visitContract, appView);
    return rewritten;
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

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(valueParameters, param -> param::trace, definitionSupplier);
    returnType.trace(definitionSupplier);
    if (receiverParameterType != null) {
      receiverParameterType.trace(definitionSupplier);
    }
    forEachApply(typeParameters, param -> param::trace, definitionSupplier);
    forEachApply(contextReceiverTypes, type -> type::trace, definitionSupplier);
    if (signature != null) {
      signature.trace(definitionSupplier);
    }
    if (lambdaClassOrigin != null) {
      lambdaClassOrigin.trace(definitionSupplier);
    }
    contract.trace(definitionSupplier);
  }
}
