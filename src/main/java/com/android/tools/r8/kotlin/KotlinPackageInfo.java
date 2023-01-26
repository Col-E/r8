// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.JvmExtensionsKt;

// Holds information about a KmPackage object.
public class KotlinPackageInfo implements EnqueuerMetadataTraceable {

  private final String moduleName;
  private final KotlinDeclarationContainerInfo containerInfo;
  private final KotlinLocalDelegatedPropertyInfo localDelegatedProperties;
  private final KotlinMetadataMembersTracker originalMembersWithKotlinInfo;

  private KotlinPackageInfo(
      String moduleName,
      KotlinDeclarationContainerInfo containerInfo,
      KotlinLocalDelegatedPropertyInfo localDelegatedProperties,
      KotlinMetadataMembersTracker originalMembersWithKotlinInfo) {
    this.moduleName = moduleName;
    this.containerInfo = containerInfo;
    this.localDelegatedProperties = localDelegatedProperties;
    this.originalMembersWithKotlinInfo = originalMembersWithKotlinInfo;
  }

  public static KotlinPackageInfo create(
      KmPackage kmPackage,
      DexClass clazz,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode,
      KotlinJvmSignatureExtensionInformation extensionInformation) {
    Map<String, DexEncodedField> fieldMap = new HashMap<>();
    for (DexEncodedField field : clazz.fields()) {
      fieldMap.put(toJvmFieldSignature(field.getReference()).asString(), field);
    }
    Map<String, DexEncodedMethod> methodMap = new HashMap<>();
    for (DexEncodedMethod method : clazz.methods()) {
      methodMap.put(toJvmMethodSignature(method.getReference()).asString(), method);
    }
    KotlinMetadataMembersTracker originalMembersWithKotlinInfo =
        new KotlinMetadataMembersTracker(appView);
    return new KotlinPackageInfo(
        JvmExtensionsKt.getModuleName(kmPackage),
        KotlinDeclarationContainerInfo.create(
            kmPackage,
            methodMap,
            fieldMap,
            appView.dexItemFactory(),
            appView.reporter(),
            keepByteCode,
            extensionInformation,
            originalMembersWithKotlinInfo),
        KotlinLocalDelegatedPropertyInfo.create(
            JvmExtensionsKt.getLocalDelegatedProperties(kmPackage),
            appView.dexItemFactory(),
            appView.reporter()),
        originalMembersWithKotlinInfo);
  }

  boolean rewrite(KmPackage kmPackage, DexClass clazz, AppView<?> appView) {
    KotlinMetadataMembersTracker rewrittenReferences = new KotlinMetadataMembersTracker(appView);
    boolean rewritten =
        containerInfo.rewrite(
            kmPackage.getFunctions()::add,
            kmPackage.getProperties()::add,
            kmPackage.getTypeAliases()::add,
            clazz,
            appView,
            rewrittenReferences);
    rewritten |=
        localDelegatedProperties.rewrite(
            JvmExtensionsKt.getLocalDelegatedProperties(kmPackage)::add, appView);
    JvmExtensionsKt.setModuleName(kmPackage, moduleName);
    return rewritten || !originalMembersWithKotlinInfo.isEqual(rewrittenReferences, appView);
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    containerInfo.trace(definitionSupplier);
    localDelegatedProperties.trace(definitionSupplier);
  }

  public String getModuleName() {
    return moduleName;
  }
}
