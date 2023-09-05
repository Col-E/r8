// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.isValidMethodDescriptor;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toDefaultJvmMethodSignature;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.kotlin.KotlinMetadataUtils.KmPropertyProcessor;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import com.google.common.math.IntMath;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.internal.metadata.deserialization.Flags;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;

// Holds information about KmDeclarationContainer
public class KotlinDeclarationContainerInfo implements EnqueuerMetadataTraceable {

  private final List<KotlinTypeAliasInfo> typeAliases;
  // The functions in notBackedFunctions are KmFunctions where we could not find a representative.
  private final List<KotlinFunctionInfo> functionsWithNoBacking;
  // The properties in propertiesWithNoBacking are KmProperties where we could not find a getter,
  // setter or backing field.
  private final List<KotlinPropertyInfo> propertiesWithNoBacking;

  private KotlinDeclarationContainerInfo(
      List<KotlinTypeAliasInfo> typeAliases,
      List<KotlinFunctionInfo> functionsWithNoBacking,
      List<KotlinPropertyInfo> propertiesWithNoBacking) {
    this.typeAliases = typeAliases;
    this.functionsWithNoBacking = functionsWithNoBacking;
    this.propertiesWithNoBacking = propertiesWithNoBacking;
  }

  public static KotlinDeclarationContainerInfo create(
      KmDeclarationContainer container,
      Map<String, DexEncodedMethod> methodSignatureMap,
      Map<String, DexEncodedField> fieldSignatureMap,
      DexItemFactory factory,
      Reporter reporter,
      Consumer<DexEncodedMethod> keepByteCode,
      KotlinJvmSignatureExtensionInformation extensionInformation,
      KotlinMetadataMembersTracker originalAssignmentTracker) {
    ImmutableList.Builder<KotlinFunctionInfo> notBackedFunctions = ImmutableList.builder();
    int functionCounter = 0;
    for (KmFunction kmFunction : container.getFunctions()) {
      JvmMethodSignature signature = JvmExtensionsKt.getSignature(kmFunction);
      if (signature == null) {
        assert false;
        continue;
      }
      KotlinFunctionInfo kotlinFunctionInfo =
          KotlinFunctionInfo.create(
              kmFunction,
              factory,
              reporter,
              extensionInformation.hasJvmMethodSignatureExtensionForFunction(functionCounter++));
      DexEncodedMethod method = methodSignatureMap.get(signature.asString());
      if (method == null) {
        notBackedFunctions.add(kotlinFunctionInfo);
        if (!isValidMethodDescriptor(signature.getDesc())) {
          // TODO(b/155536535): Enable this assert.
          // appView
          //     .options()
          //     .reporter
          //     .info(KotlinMetadataDiagnostic.invalidMethodDescriptor(signature.asString()));
        } else {
          // TODO(b/154348568): Enable the assertion below.
          // assert false : "Could not find method with signature " + signature.asString();
        }
        continue;
      }
      keepIfInline(kmFunction.getFlags(), method, signature, methodSignatureMap, keepByteCode);
      method.setKotlinMemberInfo(kotlinFunctionInfo);
      originalAssignmentTracker.add(method.getReference());
    }

    ImmutableList.Builder<KotlinPropertyInfo> notBackedProperties = ImmutableList.builder();
    for (KmProperty kmProperty : container.getProperties()) {
      KotlinPropertyInfo kotlinPropertyInfo =
          KotlinPropertyInfo.create(kmProperty, factory, reporter);
      KmPropertyProcessor propertyProcessor = new KmPropertyProcessor(kmProperty);
      boolean hasBacking = false;
      if (propertyProcessor.fieldSignature() != null) {
        DexEncodedField field =
            fieldSignatureMap.get(propertyProcessor.fieldSignature().asString());
        if (field != null) {
          hasBacking = true;
          field.setKotlinMemberInfo(kotlinPropertyInfo);
          originalAssignmentTracker.add(field.getReference());
        }
      }
      if (propertyProcessor.getterSignature() != null) {
        DexEncodedMethod method =
            methodSignatureMap.get(propertyProcessor.getterSignature().asString());
        if (method != null) {
          hasBacking = true;
          keepIfAccessorInline(kmProperty.getGetterFlags(), method, keepByteCode);
          method.setKotlinMemberInfo(kotlinPropertyInfo);
          originalAssignmentTracker.add(method.getReference());
        }
      }
      if (propertyProcessor.setterSignature() != null) {
        DexEncodedMethod method =
            methodSignatureMap.get(propertyProcessor.setterSignature().asString());
        if (method != null) {
          hasBacking = true;
          keepIfAccessorInline(kmProperty.getGetterFlags(), method, keepByteCode);
          method.setKotlinMemberInfo(kotlinPropertyInfo);
          originalAssignmentTracker.add(method.getReference());
        }
      }
      if (!hasBacking) {
        notBackedProperties.add(kotlinPropertyInfo);
      }
    }
    return new KotlinDeclarationContainerInfo(
        getTypeAliases(container.getTypeAliases(), factory, reporter),
        notBackedFunctions.build(),
        notBackedProperties.build());
  }

  private static void keepIfInline(
      int flags,
      DexEncodedMethod method,
      JvmMethodSignature signature,
      Map<String, DexEncodedMethod> methodSignatureMap,
      Consumer<DexEncodedMethod> keepByteCode) {
    if (Flags.IS_INLINE.get(flags)) {
      // Check if we can find a default method. If there are more than 32 arguments another int
      // index will be added to the default method.
      for (int i = 1;
          i <= IntMath.divide(method.getParameters().size(), 32, RoundingMode.CEILING);
          i++) {
        DexEncodedMethod defaultValueMethod =
            methodSignatureMap.get(toDefaultJvmMethodSignature(signature, i).toString());
        if (defaultValueMethod != null) {
          keepByteCode.accept(defaultValueMethod);
          return;
        }
      }
      keepByteCode.accept(method);
    }
  }

  private static void keepIfAccessorInline(
      int flags, DexEncodedMethod method, Consumer<DexEncodedMethod> keepByteCode) {
    if (Flags.IS_INLINE_ACCESSOR.get(flags)) {
      keepByteCode.accept(method);
    }
  }

  private static List<KotlinTypeAliasInfo> getTypeAliases(
      List<KmTypeAlias> aliases, DexItemFactory factory, Reporter reporter) {
    ImmutableList.Builder<KotlinTypeAliasInfo> builder = ImmutableList.builder();
    for (KmTypeAlias alias : aliases) {
      builder.add(KotlinTypeAliasInfo.create(alias, factory, reporter));
    }
    return builder.build();
  }

  @SuppressWarnings("ReferenceEquality")
  boolean rewrite(
      Consumer<KmFunction> functionConsumer,
      Consumer<KmProperty> propertyConsumer,
      Consumer<KmTypeAlias> typeAliasConsumer,
      DexClass clazz,
      AppView<?> appView,
      KotlinMetadataMembersTracker rewrittenMembersWithKotlinInfo) {
    // Type aliases only have a representation here, so we can generate them directly.
    boolean rewritten =
        rewriteList(appView, typeAliases, typeAliasConsumer, KotlinTypeAliasInfo::rewrite);
    // For properties, we need to combine potentially a field, setter and getter.
    Map<KotlinPropertyInfo, KotlinPropertyGroup> properties = new LinkedHashMap<>();
    for (DexEncodedField field : clazz.fields()) {
      if (field.getKotlinInfo().isProperty()) {
        properties
            .computeIfAbsent(
                field.getKotlinInfo().asProperty(), ignored -> new KotlinPropertyGroup())
            .setBackingField(field);
        rewrittenMembersWithKotlinInfo.add(field.getReference());
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo().isFunction()) {
        rewritten |= method.getKotlinInfo().asFunction().rewrite(functionConsumer, method, appView);
        rewrittenMembersWithKotlinInfo.add(method.getReference());
        continue;
      }
      KotlinPropertyInfo kotlinPropertyInfo = method.getKotlinInfo().asProperty();
      if (kotlinPropertyInfo == null) {
        continue;
      }
      rewrittenMembersWithKotlinInfo.add(method.getReference());
      KotlinPropertyGroup kotlinPropertyGroup =
          properties.computeIfAbsent(kotlinPropertyInfo, ignored -> new KotlinPropertyGroup());
      if (method.getReference().proto.returnType == appView.dexItemFactory().voidType) {
        // This is a setter.
        kotlinPropertyGroup.setSetter(method);
      } else {
        kotlinPropertyGroup.setGetter(method);
      }
    }
    for (KotlinPropertyInfo kotlinPropertyInfo : properties.keySet()) {
      KotlinPropertyGroup kotlinPropertyGroup = properties.get(kotlinPropertyInfo);
      rewritten |=
          kotlinPropertyInfo.rewrite(
              propertyConsumer,
              kotlinPropertyGroup.backingField,
              kotlinPropertyGroup.getter,
              kotlinPropertyGroup.setter,
              appView);
    }
    // Add all not backed functions and properties.
    rewritten |=
        rewriteList(
            appView,
            functionsWithNoBacking,
            functionConsumer,
            KotlinFunctionInfo::rewriteNoBacking);
    rewritten |=
        rewriteList(
            appView,
            propertiesWithNoBacking,
            propertyConsumer,
            KotlinPropertyInfo::rewriteNoBacking);
    return rewritten;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(typeAliases, alias -> alias::trace, definitionSupplier);
    forEachApply(functionsWithNoBacking, function -> function::trace, definitionSupplier);
    forEachApply(propertiesWithNoBacking, property -> property::trace, definitionSupplier);
  }

  public static class KotlinPropertyGroup {

    private DexEncodedField backingField = null;
    private DexEncodedMethod setter = null;
    private DexEncodedMethod getter = null;

    void setBackingField(DexEncodedField backingField) {
      assert this.backingField == null;
      this.backingField = backingField;
    }

    void setGetter(DexEncodedMethod getter) {
      assert this.getter == null;
      this.getter = getter;
    }

    void setSetter(DexEncodedMethod setter) {
      assert this.setter == null;
      this.setter = setter;
    }
  }
}
