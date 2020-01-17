// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToInternalName;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.JvmExtensionsKt;

class KotlinMetadataSynthesizer {

  static boolean isExtension(KmFunction kmFunction) {
    return kmFunction.getReceiverParameterType() != null;
  }

  static boolean isExtension(KmProperty kmProperty) {
    return kmProperty.getReceiverParameterType() != null;
  }

  static KmType toKmType(String descriptor) {
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToInternalName(descriptor));
    return kmType;
  }

  static KmType toRenamedKmType(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    // E.g., [Ljava/lang/String; -> Lkotlin/Array;
    if (type.isArrayType()) {
      return toKmType(addKotlinPrefix("Array;"));
    }
    // E.g., void -> Lkotlin/Unit;
    if (appView.dexItemFactory().kotlin.knownTypeConversion.containsKey(type)) {
      KmType kmType = new KmType(flagsOf());
      DexType convertedType = appView.dexItemFactory().kotlin.knownTypeConversion.get(type);
      assert convertedType != null;
      kmType.visitClass(descriptorToInternalName(convertedType.toDescriptorString()));
      return kmType;
    }
    // For library or classpath class, synthesize @Metadata always.
    // For a program class, make sure it is live.
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(type)) {
      return null;
    }
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    // For library or classpath class, we should not have renamed it.
    DexClass clazz = appView.definitionFor(type);
    assert clazz == null || clazz.isProgramClass() || renamedType == type
        : type.toSourceString() + " -> " + renamedType.toSourceString();
    // TODO(b/70169921): Mysterious, why attempts to properly set flags bothers kotlinc?
    //   and/or why wiping out flags works for KmType but not KmFunction?!
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToInternalName(renamedType.toDescriptorString()));
    return kmType;
  }

  static KmConstructor toRenamedKmConstructor(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    // Make sure it is an instance initializer and live.
    if (!method.isInstanceInitializer()
        || !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    KmConstructor kmConstructor = new KmConstructor(method.accessFlags.getAsKotlinFlags());
    JvmExtensionsKt.setSignature(kmConstructor, toJvmMethodSignature(method.method));
    List<KmValueParameter> parameters = kmConstructor.getValueParameters();
    if (!populateKmValueParameters(parameters, method, appView, lens, false)) {
      return null;
    }
    return kmConstructor;
  }

  static KmFunction toRenamedKmFunction(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, appView, lens, false);
  }

  static KmFunction toRenamedKmFunctionAsExtension(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, appView, lens, true);
  }

  private static KmFunction toRenamedKmFunctionHelper(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens,
      boolean isExtension) {
    // For library overrides, synthesize @Metadata always.
    // For regular methods, make sure it is live.
    if (!method.isLibraryMethodOverride().isTrue()
        && !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    DexMethod renamedMethod = lens.lookupMethod(method.method, appView.dexItemFactory());
    // For a library method override, we should not have renamed it.
    assert !method.isLibraryMethodOverride().isTrue() || renamedMethod == method.method
        : method.toSourceString() + " -> " + renamedMethod.toSourceString();
    KmFunction kmFunction =
        new KmFunction(method.accessFlags.getAsKotlinFlags(), renamedMethod.name.toString());
    JvmExtensionsKt.setSignature(kmFunction, toJvmMethodSignature(method.method));
    KmType kmReturnType = toRenamedKmType(method.method.proto.returnType, appView, lens);
    if (kmReturnType == null) {
      return null;
    }
    kmFunction.setReturnType(kmReturnType);
    if (isExtension) {
      assert method.method.proto.parameters.values.length > 0
          : method.method.toSourceString();
      KmType kmReceiverType =
          toRenamedKmType(method.method.proto.parameters.values[0], appView, lens);
      if (kmReceiverType == null) {
        return null;
      }
      kmFunction.setReceiverParameterType(kmReceiverType);
    }
    List<KmValueParameter> parameters = kmFunction.getValueParameters();
    if (!populateKmValueParameters(parameters, method, appView, lens, isExtension)) {
      return null;
    }
    return kmFunction;
  }

  private static boolean populateKmValueParameters(
      List<KmValueParameter> parameters,
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens,
      boolean isExtension) {
    for (int i = isExtension ? 1 : 0; i < method.method.proto.parameters.values.length; i++) {
      DexType paramType = method.method.proto.parameters.values[i];
      DebugLocalInfo debugLocalInfo = method.getParameterInfo().get(i);
      String parameterName = debugLocalInfo != null ? debugLocalInfo.name.toString() : ("p" + i);
      // TODO(b/70169921): Consult kotlinx.metadata.Flag.ValueParameter.
      KmValueParameter kmValueParameter = new KmValueParameter(flagsOf(), parameterName);
      KmType kmParamType = toRenamedKmType(paramType, appView, lens);
      if (kmParamType == null) {
        return false;
      }
      kmValueParameter.setType(kmParamType);
      parameters.add(kmValueParameter);
    }
    return true;
  }
}
