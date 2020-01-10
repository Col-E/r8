// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.parameterTypesFromJvmMethodSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.returnTypeFromJvmMethodSignature;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToInternalName;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKmType;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmConstructorProcessor;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmFunctionProcessor;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmPropertyProcessor;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.JvmMethodSignature;

public class KotlinMetadataSynthesizer {

  static boolean isExtension(KmFunction kmFunction) {
    return kmFunction.getReceiverParameterType() != null;
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
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    // For library or classpath class, synthesize @Metadata always.
    // For a program class, make sure it is live.
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(type)) {
      return null;
    }
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    // For library or classpath class, we should not have renamed it.
    assert clazz.isProgramClass() || renamedType == type
        : type.toSourceString() + " -> " + renamedType.toSourceString();
    // TODO(b/70169921): Mysterious, why attempts to properly set flags bothers kotlinc?
    //   and/or why wiping out flags works for KmType but not KmFunction?!
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToInternalName(renamedType.toDescriptorString()));
    return kmType;
  }

  private static boolean isCompatible(String desc, DexType type) {
    if (desc == null || type == null) {
      return false;
    }
    return desc.equals(type.toDescriptorString());
  }

  private static boolean isCompatible(KmType kmType, DexType type, AppView<?> appView) {
    if (kmType == null || type == null) {
      return false;
    }
    String descriptor = null;
    if (appView.dexItemFactory().kotlin.knownTypeConversion.containsKey(type)) {
      DexType convertedType = appView.dexItemFactory().kotlin.knownTypeConversion.get(type);
      descriptor = convertedType.toDescriptorString();
    }
    if (descriptor == null) {
      descriptor = type.toDescriptorString();
    }
    assert descriptor != null;
    return descriptor.equals(getDescriptorFromKmType(kmType));
  }

  private static boolean isCompatibleJvmMethodSignature(
      JvmMethodSignature signature, DexEncodedMethod method) {
    String methodName = method.method.name.toString();
    if (!signature.getName().equals(methodName)) {
      return false;
    }
    if (!isCompatible(
        returnTypeFromJvmMethodSignature(signature), method.method.proto.returnType)) {
      return false;
    }
    List<String> parameterTypes = parameterTypesFromJvmMethodSignature(signature);
    if (parameterTypes == null || parameterTypes.size() != method.method.proto.parameters.size()) {
      return false;
    }
    for (int i = 0; i < method.method.proto.parameters.size(); i++) {
      if (!isCompatible(parameterTypes.get(i), method.method.proto.parameters.values[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean isCompatibleConstructor(
      KmConstructor constructor, DexEncodedMethod method, AppView<?> appView) {
    // Note that targets for @JvmName don't include constructor. So, it's not necessary to process
    // JvmMethodSignature inside JvmConstructorExtension.
    List<KmValueParameter> parameters = constructor.getValueParameters();
    if (method.method.proto.parameters.size() != parameters.size()) {
      return false;
    }
    for (int i = 0; i < method.method.proto.parameters.size(); i++) {
      KmType kmType = parameters.get(i).getType();
      if (!isCompatible(kmType, method.method.proto.parameters.values[i], appView)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isCompatibleFunction(
      KmFunction function, DexEncodedMethod method, AppView<?> appView) {
    // Check if a custom name is set to avoid name clash.
    KmFunctionProcessor kmFunctionProcessor = new KmFunctionProcessor(function);
    JvmMethodSignature jvmMethodSignature = kmFunctionProcessor.signature();
    if (jvmMethodSignature != null && isCompatibleJvmMethodSignature(jvmMethodSignature, method)) {
      return true;
    }

    if (!function.getName().equals(method.method.name.toString())) {
      return false;
    }
    if (!isCompatible(function.getReturnType(), method.method.proto.returnType, appView)) {
      return false;
    }
    List<KmValueParameter> parameters = function.getValueParameters();
    if (method.method.proto.parameters.size() != parameters.size()) {
      return false;
    }
    for (int i = 0; i < method.method.proto.parameters.size(); i++) {
      KmType kmType = parameters.get(i).getType();
      if (!isCompatible(kmType, method.method.proto.parameters.values[i], appView)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isCompatibleExtension(
      KmFunction extension, DexEncodedMethod method, AppView<?> appView) {
    // Check if a custom name is set to avoid name clash.
    KmFunctionProcessor kmFunctionProcessor = new KmFunctionProcessor(extension);
    JvmMethodSignature jvmMethodSignature = kmFunctionProcessor.signature();
    if (jvmMethodSignature != null && isCompatibleJvmMethodSignature(jvmMethodSignature, method)) {
      return true;
    }

    if (!extension.getName().equals(method.method.name.toString())) {
      return false;
    }
    if (!isCompatible(extension.getReturnType(), method.method.proto.returnType, appView)) {
      return false;
    }
    List<KmValueParameter> parameters = extension.getValueParameters();
    if (method.method.proto.parameters.size() != parameters.size() + 1) {
      return false;
    }
    assert method.method.proto.parameters.size() > 0;
    assert extension.getReceiverParameterType() != null;
    if (!isCompatible(
        extension.getReceiverParameterType(), method.method.proto.parameters.values[0], appView)) {
      return false;
    }
    for (int i = 1; i < method.method.proto.parameters.size(); i++) {
      KmType kmType = parameters.get(i - 1).getType();
      if (!isCompatible(kmType, method.method.proto.parameters.values[i], appView)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isCompatibleProperty(
      KmProperty kmProperty, DexEncodedMethod method, AppView<?> appView) {
    KmPropertyProcessor kmPropertyProcessor = new KmPropertyProcessor(kmProperty);
    // Check if a custom getter is defined via @get:JvmName("myGetter").
    JvmMethodSignature getterSignature = kmPropertyProcessor.getterSignature();
    if (getterSignature != null && isCompatibleJvmMethodSignature(getterSignature, method)) {
      return true;
    }
    // Check if a custom setter is defined via @set:JvmName("mySetter").
    JvmMethodSignature setterSignature = kmPropertyProcessor.setterSignature();
    if (setterSignature != null && isCompatibleJvmMethodSignature(setterSignature, method)) {
      return true;
    }

    // E.g., property `prop: T` is mapped to `getProp()T`, `setProp(T)V`, `prop$annotations()V`.
    // For boolean property, though, getter is mapped to `isProp()Z`.
    // TODO(b/70169921): Avoid decoding.
    String methodName = method.method.name.toString();
    if (!methodName.startsWith("is")
        && !methodName.startsWith("get")
        && !methodName.startsWith("set")
        && !methodName.endsWith("$annotations")) {
      return false;
    }

    String propertyName = kmProperty.getName();
    assert propertyName.length() > 0;
    String annotations = propertyName + "$annotations";
    if (methodName.equals(annotations)) {
      return true;
    }
    String capitalized =
        Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    String returnTypeDescriptor = getDescriptorFromKmType(kmProperty.returnType);
    String getterPrefix =
        returnTypeDescriptor != null && returnTypeDescriptor.endsWith("Boolean;") ? "is" : "get";
    String getter = getterPrefix + capitalized;
    if (methodName.equals(getter)
        && method.method.proto.parameters.size() == 0
        && isCompatible(kmProperty.returnType, method.method.proto.returnType, appView)) {
      return true;
    }
    String setter = "set" + capitalized;
    if (methodName.equals(setter)
        && method.method.proto.returnType.isVoidType()
        && method.method.proto.parameters.size() == 1
        && isCompatible(kmProperty.returnType, method.method.proto.parameters.values[0], appView)) {
      return true;
    }
    return false;
  }

  static KmConstructor toRenamedKmConstructor(
      DexEncodedMethod method,
      KmConstructor original,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    // Make sure it is an instance initializer and live.
    if (!method.isInstanceInitializer()
        || !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    // TODO(b/70169921): {@link KmConstructor.extensions} is private, i.e., no way to alter!
    //   Thus, we rely on original metadata for now.
    KmConstructorProcessor kmConstructorProcessor = new KmConstructorProcessor(original);
    JvmMethodSignature jvmMethodSignature = kmConstructorProcessor.signature();
    KmConstructor kmConstructor =
        jvmMethodSignature != null
            ? original
            // TODO(b/70169921): Consult kotlinx.metadata.Flag.Constructor to set IS_PRIMARY.
            : new KmConstructor(method.accessFlags.getAsKotlinFlags());
    List<KmValueParameter> parameters = kmConstructor.getValueParameters();
    parameters.clear();
    populateKmValueParameters(parameters, method, appView, lens, false);
    return kmConstructor;
  }

  static KmFunction toRenamedKmFunction(
      DexEncodedMethod method,
      KmFunction original,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, original, appView, lens, false);
  }

  static KmFunction toRenamedKmFunctionAsExtension(
      DexEncodedMethod method,
      KmFunction original,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, original, appView, lens, true);
  }

  private static KmFunction toRenamedKmFunctionHelper(
      DexEncodedMethod method,
      KmFunction original,
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
    // TODO(b/70169921): {@link KmFunction.extensions} is private, i.e., no way to alter!
    //   Thus, we rely on original metadata for now.
    assert !isExtension || original != null;
    KmFunction kmFunction =
        isExtension
            ? original
            // TODO(b/70169921): Consult kotlinx.metadata.Flag.Function for kind (e.g., suspend).
            : new KmFunction(method.accessFlags.getAsKotlinFlags(), renamedMethod.name.toString());
    KmType kmReturnType = toRenamedKmType(method.method.proto.returnType, appView, lens);
    assert kmReturnType != null;
    kmFunction.setReturnType(kmReturnType);
    if (isExtension) {
      assert method.method.proto.parameters.values.length > 0;
      KmType kmReceiverType =
          toRenamedKmType(method.method.proto.parameters.values[0], appView, lens);
      assert kmReceiverType != null;
      kmFunction.setReceiverParameterType(kmReceiverType);
    }
    List<KmValueParameter> parameters = kmFunction.getValueParameters();
    parameters.clear();
    populateKmValueParameters(parameters, method, appView, lens, isExtension);
    return kmFunction;
  }

  private static void populateKmValueParameters(
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
      assert kmParamType != null;
      kmValueParameter.setType(kmParamType);
      parameters.add(kmValueParameter);
    }
  }
}
