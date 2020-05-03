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
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClassInfo implements KotlinClassLevelInfo {

  private final int flags;
  private final String name;
  private final String moduleName;
  private final List<KotlinConstructorInfo> constructorsWithNoBacking;
  private final KotlinDeclarationContainerInfo declarationContainerInfo;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinTypeInfo> superTypes;
  private final List<DexType> sealedSubClasses;
  private final List<DexType> nestedClasses;
  // TODO(b/154347404): Understand enum entries.
  private final List<String> enumEntries;
  private final DexType anonymousObjectOrigin;

  public KotlinClassInfo(
      int flags,
      String name,
      String moduleName,
      KotlinDeclarationContainerInfo declarationContainerInfo,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinConstructorInfo> constructorsWithNoBacking,
      List<KotlinTypeInfo> superTypes,
      List<DexType> sealedSubClasses,
      List<DexType> nestedClasses,
      List<String> enumEntries,
      DexType anonymousObjectOrigin) {
    this.flags = flags;
    this.name = name;
    this.moduleName = moduleName;
    this.declarationContainerInfo = declarationContainerInfo;
    this.typeParameters = typeParameters;
    this.constructorsWithNoBacking = constructorsWithNoBacking;
    this.superTypes = superTypes;
    this.sealedSubClasses = sealedSubClasses;
    this.nestedClasses = nestedClasses;
    this.enumEntries = enumEntries;
    this.anonymousObjectOrigin = anonymousObjectOrigin;
  }

  public static KotlinClassInfo create(KmClass kmClass, DexClass hostClass, AppView<?> appView) {
    Map<String, DexEncodedField> fieldMap = new HashMap<>();
    for (DexEncodedField field : hostClass.fields()) {
      fieldMap.put(toJvmFieldSignature(field.field).asString(), field);
    }
    Map<String, DexEncodedMethod> methodMap = new HashMap<>();
    for (DexEncodedMethod method : hostClass.methods()) {
      methodMap.put(toJvmMethodSignature(method.method).asString(), method);
    }
    ImmutableList.Builder<KotlinConstructorInfo> notBackedConstructors = ImmutableList.builder();
    for (KmConstructor kmConstructor : kmClass.getConstructors()) {
      KotlinConstructorInfo constructorInfo = KotlinConstructorInfo.create(kmConstructor, appView);
      JvmMethodSignature signature = JvmExtensionsKt.getSignature(kmConstructor);
      if (signature != null) {
        DexEncodedMethod method = methodMap.get(signature.asString());
        if (method != null) {
          method.setKotlinMemberInfo(constructorInfo);
          continue;
        }
      }
      // We could not find a definition for the constructor - add it to ensure the same output.
      notBackedConstructors.add(constructorInfo);
    }
    KotlinDeclarationContainerInfo container =
        KotlinDeclarationContainerInfo.create(kmClass, methodMap, fieldMap, appView);
    setCompanionObject(kmClass, hostClass, appView);
    return new KotlinClassInfo(
        kmClass.getFlags(),
        kmClass.name,
        JvmExtensionsKt.getModuleName(kmClass),
        container,
        KotlinTypeParameterInfo.create(kmClass.getTypeParameters(), appView),
        notBackedConstructors.build(),
        getSuperTypes(kmClass.getSupertypes(), appView),
        getSealedSubClasses(hostClass, kmClass.getSealedSubclasses(), appView),
        getNestedClasses(hostClass, kmClass.getNestedClasses(), appView),
        kmClass.getEnumEntries(),
        getAnonymousObjectOrigin(kmClass, appView));
  }

  private static DexType getAnonymousObjectOrigin(KmClass kmClass, AppView<?> appView) {
    String anonymousObjectOriginName = JvmExtensionsKt.getAnonymousObjectOriginName(kmClass);
    if (anonymousObjectOriginName != null) {
      return appView
          .dexItemFactory()
          .createType(DescriptorUtils.getDescriptorFromClassBinaryName(anonymousObjectOriginName));
    }
    return null;
  }

  private static List<DexType> getNestedClasses(
      DexClass clazz, List<String> nestedClasses, AppView<?> appView) {
    ImmutableList.Builder<DexType> nestedTypes = ImmutableList.builder();
    for (String nestedClass : nestedClasses) {
      String binaryName =
          clazz.type.toBinaryName() + DescriptorUtils.INNER_CLASS_SEPARATOR + nestedClass;
      DexType nestedType =
          appView
              .dexItemFactory()
              .createType(DescriptorUtils.getDescriptorFromClassBinaryName(binaryName));
      nestedTypes.add(nestedType);
    }
    return nestedTypes.build();
  }

  private static List<DexType> getSealedSubClasses(
      DexClass clazz, List<String> sealedSubclasses, AppView<?> appView) {
    ImmutableList.Builder<DexType> sealedTypes = ImmutableList.builder();
    for (String sealedSubClass : sealedSubclasses) {
      String binaryName =
          sealedSubClass.replace(
              DescriptorUtils.JAVA_PACKAGE_SEPARATOR, DescriptorUtils.INNER_CLASS_SEPARATOR);
      DexType sealedType =
          appView
              .dexItemFactory()
              .createType(DescriptorUtils.getDescriptorFromClassBinaryName(binaryName));
      sealedTypes.add(sealedType);
    }
    return sealedTypes.build();
  }

  private static List<KotlinTypeInfo> getSuperTypes(List<KmType> superTypes, AppView<?> appView) {
    ImmutableList.Builder<KotlinTypeInfo> superTypeInfos = ImmutableList.builder();
    for (KmType superType : superTypes) {
      superTypeInfos.add(KotlinTypeInfo.create(superType, appView));
    }
    return superTypeInfos.build();
  }

  private static void setCompanionObject(KmClass kmClass, DexClass hostClass, AppView<?> appView) {
    String companionObjectName = kmClass.getCompanionObject();
    if (companionObjectName == null) {
      return;
    }
    for (DexEncodedField field : hostClass.fields()) {
      if (field.field.name.toString().equals(companionObjectName)) {
        field.setKotlinMemberInfo(new KotlinCompanionInfo());
        return;
      }
    }
    appView
        .options()
        .reporter
        .warning(KotlinMetadataDiagnostic.missingCompanionObject(hostClass, companionObjectName));
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public KotlinClassInfo asClass() {
    return this;
  }

  @Override
  public KotlinClassHeader rewrite(
      DexClass clazz, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
    KmClass kmClass = new KmClass();
    // TODO(b/154348683): Set flags.
    kmClass.setFlags(flags);
    // Set potentially renamed class name.
    DexString originalDescriptor = clazz.type.descriptor;
    DexString rewrittenDescriptor = namingLens.lookupDescriptor(clazz.type);
    // If the original descriptor equals the rewritten descriptor, we pick the original name
    // to preserve potential errors in the original name. As an example, the kotlin stdlib has
    // name: .kotlin/collections/CollectionsKt___CollectionsKt$groupingBy$1, which seems incorrect.
    kmClass.setName(
        originalDescriptor.equals(rewrittenDescriptor)
            ? this.name
            : KotlinMetadataUtils.kotlinNameFromDescriptor(rewrittenDescriptor));
    // Find a companion object.
    for (DexEncodedField field : clazz.fields()) {
      if (field.getKotlinMemberInfo().isCompanion()) {
        field.getKotlinMemberInfo().asCompanion().rewrite(kmClass, field.field, namingLens);
      }
    }
    // Take all not backed constructors because we will never find them in definitions.
    for (KotlinConstructorInfo constructorInfo : constructorsWithNoBacking) {
      constructorInfo.rewrite(kmClass, null, appView, namingLens);
    }
    // Find all constructors.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinMemberInfo().isConstructor()) {
        KotlinConstructorInfo constructorInfo = method.getKotlinMemberInfo().asConstructor();
        constructorInfo.rewrite(kmClass, method, appView, namingLens);
      }
    }
    // Rewrite functions, type-aliases and type-parameters.
    declarationContainerInfo.rewrite(
        kmClass::visitFunction,
        kmClass::visitProperty,
        kmClass::visitTypeAlias,
        clazz,
        appView,
        namingLens);
    // Rewrite type parameters.
    for (KotlinTypeParameterInfo typeParameter : typeParameters) {
      typeParameter.rewrite(kmClass::visitTypeParameter, appView, namingLens);
    }
    // Rewrite super types.
    for (KotlinTypeInfo superType : superTypes) {
      superType.rewrite(kmClass::visitSupertype, appView, namingLens);
    }
    // Rewrite nested classes.
    for (DexType nestedClass : nestedClasses) {
      if (appView.definitionFor(nestedClass) != null) {
        String descriptor =
            KotlinMetadataUtils.kotlinNameFromDescriptor(namingLens.lookupDescriptor(nestedClass));
        // If the class is a nested class, it should be on the form Foo.Bar$Baz, where Baz is the
        // name we should record.
        int innerClassIndex = descriptor.lastIndexOf(DescriptorUtils.INNER_CLASS_SEPARATOR);
        kmClass.visitNestedClass(descriptor.substring(innerClassIndex + 1));
      }
    }
    // Rewrite sealed sub classes.
    for (DexType sealedSubClass : sealedSubClasses) {
      if (appView.definitionFor(sealedSubClass) != null) {
        String descriptor =
            KotlinMetadataUtils.kotlinNameFromDescriptor(
                namingLens.lookupDescriptor(sealedSubClass));
        kmClass.visitSealedSubclass(
            descriptor.replace(
                DescriptorUtils.INNER_CLASS_SEPARATOR, DescriptorUtils.JAVA_PACKAGE_SEPARATOR));
      }
    }
    // TODO(b/154347404): Understand enum entries.
    kmClass.getEnumEntries().addAll(enumEntries);

    JvmExtensionsKt.setModuleName(kmClass, moduleName);
    if (anonymousObjectOrigin != null) {
      JvmExtensionsKt.setAnonymousObjectOriginName(
          kmClass, KotlinMetadataUtils.kotlinNameFromDescriptor(anonymousObjectOrigin.descriptor));
    }

    KotlinClassMetadata.Class.Writer writer = new KotlinClassMetadata.Class.Writer();
    kmClass.accept(writer);
    return writer.write().getHeader();
  }
}
