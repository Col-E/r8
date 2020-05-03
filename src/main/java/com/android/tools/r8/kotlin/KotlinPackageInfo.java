// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.HashMap;
import java.util.Map;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.jvm.JvmExtensionsKt;

// Holds information about a KmPackage object.
public class KotlinPackageInfo {

  private final String moduleName;
  private final KotlinDeclarationContainerInfo containerInfo;

  private KotlinPackageInfo(String moduleName, KotlinDeclarationContainerInfo containerInfo) {
    this.moduleName = moduleName;
    this.containerInfo = containerInfo;
  }

  public static KotlinPackageInfo create(KmPackage kmPackage, DexClass clazz, AppView<?> appView) {
    Map<String, DexEncodedField> fieldMap = new HashMap<>();
    for (DexEncodedField field : clazz.fields()) {
      fieldMap.put(toJvmFieldSignature(field.field).asString(), field);
    }
    Map<String, DexEncodedMethod> methodMap = new HashMap<>();
    for (DexEncodedMethod method : clazz.methods()) {
      methodMap.put(toJvmMethodSignature(method.method).asString(), method);
    }
    return new KotlinPackageInfo(
        JvmExtensionsKt.getModuleName(kmPackage),
        KotlinDeclarationContainerInfo.create(kmPackage, methodMap, fieldMap, appView));
  }

  public void rewrite(
      KmPackage kmPackage,
      DexClass clazz,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    containerInfo.rewrite(
        kmPackage::visitFunction,
        kmPackage::visitProperty,
        kmPackage::visitTypeAlias,
        clazz,
        appView,
        namingLens);
    JvmExtensionsKt.setModuleName(kmPackage, moduleName);
  }
}
