// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getCompatibleKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;
import static kotlinx.metadata.jvm.KotlinClassMetadata.Companion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClassInfo implements KotlinClassLevelInfo {

  private final int flags;
  private final String name;
  private final boolean nameCanBeSynthesizedFromClassOrAnonymousObjectOrigin;
  private final String moduleName;
  private final List<KotlinConstructorInfo> constructorsWithNoBacking;
  private final KotlinDeclarationContainerInfo declarationContainerInfo;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinTypeInfo> superTypes;

  private final List<KotlinTypeReference> sealedSubClasses;

  private final List<KotlinTypeReference> nestedClasses;
  private final List<String> enumEntries;
  private final KotlinVersionRequirementInfo versionRequirements;
  private final KotlinTypeReference anonymousObjectOrigin;
  private final String packageName;
  private final KotlinLocalDelegatedPropertyInfo localDelegatedProperties;
  private final int[] metadataVersion;
  private final String inlineClassUnderlyingPropertyName;
  private final KotlinTypeInfo inlineClassUnderlyingType;
  private final int jvmFlags;
  private final String companionObjectName;
  // Collection of context receiver types
  private final List<KotlinTypeInfo> contextReceiverTypes;

  // List of tracked assignments of kotlin metadata.
  private final KotlinMetadataMembersTracker originalMembersWithKotlinInfo;

  private KotlinClassInfo(
      int flags,
      String name,
      boolean nameCanBeSynthesizedFromClassOrAnonymousObjectOrigin,
      String moduleName,
      KotlinDeclarationContainerInfo declarationContainerInfo,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinConstructorInfo> constructorsWithNoBacking,
      List<KotlinTypeInfo> superTypes,
      List<KotlinTypeReference> sealedSubClasses,
      List<KotlinTypeReference> nestedClasses,
      List<String> enumEntries,
      KotlinVersionRequirementInfo versionRequirements,
      KotlinTypeReference anonymousObjectOrigin,
      String packageName,
      KotlinLocalDelegatedPropertyInfo localDelegatedProperties,
      int[] metadataVersion,
      String inlineClassUnderlyingPropertyName,
      KotlinTypeInfo inlineClassUnderlyingType,
      KotlinMetadataMembersTracker originalMembersWithKotlinInfo,
      int jvmFlags,
      String companionObjectName,
      List<KotlinTypeInfo> contextReceiverTypes) {
    this.flags = flags;
    this.name = name;
    this.nameCanBeSynthesizedFromClassOrAnonymousObjectOrigin =
        nameCanBeSynthesizedFromClassOrAnonymousObjectOrigin;
    this.moduleName = moduleName;
    this.declarationContainerInfo = declarationContainerInfo;
    this.typeParameters = typeParameters;
    this.constructorsWithNoBacking = constructorsWithNoBacking;
    this.superTypes = superTypes;
    this.sealedSubClasses = sealedSubClasses;
    this.nestedClasses = nestedClasses;
    this.enumEntries = enumEntries;
    this.versionRequirements = versionRequirements;
    this.anonymousObjectOrigin = anonymousObjectOrigin;
    this.packageName = packageName;
    this.localDelegatedProperties = localDelegatedProperties;
    this.metadataVersion = metadataVersion;
    this.inlineClassUnderlyingPropertyName = inlineClassUnderlyingPropertyName;
    this.inlineClassUnderlyingType = inlineClassUnderlyingType;
    this.originalMembersWithKotlinInfo = originalMembersWithKotlinInfo;
    this.jvmFlags = jvmFlags;
    this.companionObjectName = companionObjectName;
    this.contextReceiverTypes = contextReceiverTypes;
  }

  public static KotlinClassInfo create(
      KotlinClassMetadata.Class metadata,
      String packageName,
      int[] metadataVersion,
      DexClass hostClass,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode) {
    DexItemFactory factory = appView.dexItemFactory();
    Reporter reporter = appView.reporter();
    KmClass kmClass = metadata.getKmClass();
    Map<String, DexEncodedField> fieldMap = new HashMap<>();
    for (DexEncodedField field : hostClass.fields()) {
      fieldMap.put(toJvmFieldSignature(field.getReference()).asString(), field);
    }
    Map<String, DexEncodedMethod> methodMap = new HashMap<>();
    for (DexEncodedMethod method : hostClass.methods()) {
      methodMap.put(toJvmMethodSignature(method.getReference()).asString(), method);
    }
    ImmutableList.Builder<KotlinConstructorInfo> notBackedConstructors = ImmutableList.builder();
    KotlinMetadataMembersTracker originalMembersWithKotlinInfo =
        new KotlinMetadataMembersTracker(appView);
    for (KmConstructor kmConstructor : kmClass.getConstructors()) {
      KotlinConstructorInfo constructorInfo =
          KotlinConstructorInfo.create(kmConstructor, factory, reporter);
      JvmMethodSignature signature = JvmExtensionsKt.getSignature(kmConstructor);
      if (signature != null) {
        DexEncodedMethod method = methodMap.get(signature.asString());
        if (method != null) {
          method.setKotlinMemberInfo(constructorInfo);
          originalMembersWithKotlinInfo.add(method.getReference());
          continue;
        }
      }
      // We could not find a definition for the constructor - add it to ensure the same output.
      notBackedConstructors.add(constructorInfo);
    }
    KotlinDeclarationContainerInfo container =
        KotlinDeclarationContainerInfo.create(
            kmClass,
            methodMap,
            fieldMap,
            factory,
            reporter,
            keepByteCode,
            originalMembersWithKotlinInfo);
    KotlinTypeReference anonymousObjectOrigin = getAnonymousObjectOrigin(kmClass, factory);
    boolean nameCanBeDeducedFromClassOrOrigin =
        kmClass.name.equals(
                KotlinMetadataUtils.getKotlinClassName(
                    hostClass, hostClass.getType().toDescriptorString()))
            || (anonymousObjectOrigin != null
                && kmClass.name.equals(anonymousObjectOrigin.toKotlinClassifier(true)));
    return new KotlinClassInfo(
        kmClass.getFlags(),
        kmClass.name,
        nameCanBeDeducedFromClassOrOrigin,
        JvmExtensionsKt.getModuleName(kmClass),
        container,
        KotlinTypeParameterInfo.create(kmClass.getTypeParameters(), factory, reporter),
        notBackedConstructors.build(),
        getSuperTypes(kmClass.getSupertypes(), factory, reporter),
        getSealedSubClasses(kmClass.getSealedSubclasses(), factory),
        getNestedClasses(hostClass, kmClass.getNestedClasses(), factory),
        setEnumEntries(kmClass, hostClass),
        KotlinVersionRequirementInfo.create(kmClass.getVersionRequirements()),
        anonymousObjectOrigin,
        packageName,
        KotlinLocalDelegatedPropertyInfo.create(
            JvmExtensionsKt.getLocalDelegatedProperties(kmClass), factory, reporter),
        metadataVersion,
        kmClass.getInlineClassUnderlyingPropertyName(),
        KotlinTypeInfo.create(kmClass.getInlineClassUnderlyingType(), factory, reporter),
        originalMembersWithKotlinInfo,
        JvmExtensionsKt.getJvmFlags(kmClass),
        setCompanionObject(kmClass, hostClass, reporter),
        ListUtils.map(
            kmClass.getContextReceiverTypes(),
            contextRecieverType -> KotlinTypeInfo.create(contextRecieverType, factory, reporter)));
  }

  private static KotlinTypeReference getAnonymousObjectOrigin(
      KmClass kmClass, DexItemFactory factory) {
    String anonymousObjectOriginName = JvmExtensionsKt.getAnonymousObjectOriginName(kmClass);
    if (anonymousObjectOriginName != null) {
      return KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
          anonymousObjectOriginName, factory, anonymousObjectOriginName);
    }
    return null;
  }

  private static List<KotlinTypeReference> getNestedClasses(
      DexClass clazz, List<String> nestedClasses, DexItemFactory factory) {
    ImmutableList.Builder<KotlinTypeReference> nestedTypes = ImmutableList.builder();
    for (String nestedClass : nestedClasses) {
      String binaryName =
          clazz.type.toBinaryName() + DescriptorUtils.INNER_CLASS_SEPARATOR + nestedClass;
      nestedTypes.add(
          KotlinTypeReference.fromBinaryNameOrKotlinClassifier(binaryName, factory, nestedClass));
    }
    return nestedTypes.build();
  }

  private static List<KotlinTypeReference> getSealedSubClasses(
      List<String> sealedSubClasses, DexItemFactory factory) {
    ImmutableList.Builder<KotlinTypeReference> sealedTypes = ImmutableList.builder();
    for (String sealedSubClass : sealedSubClasses) {
      String binaryName =
          sealedSubClass.replace(
              DescriptorUtils.JAVA_PACKAGE_SEPARATOR, DescriptorUtils.INNER_CLASS_SEPARATOR);
      sealedTypes.add(
          KotlinTypeReference.fromBinaryNameOrKotlinClassifier(
              binaryName, factory, sealedSubClass));
    }
    return sealedTypes.build();
  }

  private static List<KotlinTypeInfo> getSuperTypes(
      List<KmType> superTypes, DexItemFactory factory, Reporter reporter) {
    ImmutableList.Builder<KotlinTypeInfo> superTypeInfos = ImmutableList.builder();
    for (KmType superType : superTypes) {
      superTypeInfos.add(KotlinTypeInfo.create(superType, factory, reporter));
    }
    return superTypeInfos.build();
  }

  private static String setCompanionObject(KmClass kmClass, DexClass hostClass, Reporter reporter) {
    String companionObjectName = kmClass.getCompanionObject();
    if (companionObjectName == null) {
      return companionObjectName;
    }
    for (DexEncodedField field : hostClass.fields()) {
      if (field.getReference().name.toString().equals(companionObjectName)) {
        field.setKotlinMemberInfo(new KotlinCompanionInfo(companionObjectName));
        return companionObjectName;
      }
    }
    reporter.warning(
        KotlinMetadataDiagnostic.missingCompanionObject(hostClass, companionObjectName));
    return companionObjectName;
  }

  private static List<String> setEnumEntries(KmClass kmClass, DexClass hostClass) {
    List<String> enumEntries = kmClass.getEnumEntries();
    if (enumEntries.isEmpty()) {
      return enumEntries;
    }
    Collection<String> enumEntryStrings =
        enumEntries.size() < 16 ? enumEntries : Sets.newHashSet(enumEntries);
    hostClass
        .fields()
        .forEach(
            field -> {
              String fieldName = field.getName().toString();
              if (enumEntryStrings.contains(fieldName)) {
                field.setKotlinMemberInfo(new KotlinEnumEntryInfo(fieldName));
              }
            });
    return enumEntries;
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public KotlinClassInfo asClass() {
    return this;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
    KmClass kmClass = new KmClass();
    // TODO(b/154348683): Set flags.
    kmClass.setFlags(flags);
    // Set potentially renamed class name.
    DexString originalDescriptor = clazz.type.descriptor;
    DexString rewrittenDescriptor = appView.getNamingLens().lookupDescriptor(clazz.type);
    boolean rewritten = !originalDescriptor.equals(rewrittenDescriptor);
    if (!nameCanBeSynthesizedFromClassOrAnonymousObjectOrigin) {
      kmClass.setName(this.name);
    } else {
      String rewrittenName = null;
      // When the class has an anonymousObjectOrigin and the name equals the identifier there, we
      // keep the name tied to the anonymousObjectOrigin.
      if (anonymousObjectOrigin != null
          && name.equals(anonymousObjectOrigin.toKotlinClassifier(true))) {
        Box<String> rewrittenOrigin = new Box<>();
        anonymousObjectOrigin.toRenamedBinaryNameOrDefault(rewrittenOrigin::set, appView, null);
        if (rewrittenOrigin.isSet()) {
          rewrittenName = "." + rewrittenOrigin.get();
        }
      }
      if (rewrittenName == null) {
        rewrittenName =
            KotlinMetadataUtils.getKotlinClassName(clazz, rewrittenDescriptor.toString());
      }
      kmClass.setName(rewrittenName);
      rewritten |= !name.equals(rewrittenName);
    }
    // Find a companion object.
    boolean foundCompanion = false;
    int numberOfEnumEntries = 0;
    for (DexEncodedField field : clazz.fields()) {
      KotlinFieldLevelInfo kotlinInfo = field.getKotlinInfo();
      if (kotlinInfo.isCompanion()) {
        rewritten |=
            kotlinInfo
                .asCompanion()
                .rewrite(kmClass, field.getReference(), appView.getNamingLens());
        foundCompanion = true;
      } else if (kotlinInfo.isEnumEntry()) {
        KotlinEnumEntryInfo kotlinEnumEntryInfo = kotlinInfo.asEnumEntry();
        rewritten |=
            kotlinEnumEntryInfo.rewrite(kmClass, field.getReference(), appView.getNamingLens());
        if (numberOfEnumEntries >= enumEntries.size()
            || !enumEntries.get(numberOfEnumEntries).equals(kotlinEnumEntryInfo.getEnumEntry())) {
          rewritten = true;
        }
        numberOfEnumEntries += 1;
      }
    }
    // If we did not find a companion but it was there on input we have to emit a new metadata
    // object.
    if (!foundCompanion && companionObjectName != null) {
      rewritten = true;
    }
    // If we could remove enum entries but were unable to rename them we still have to emit a new
    // metadata object.
    if (numberOfEnumEntries < enumEntries.size()) {
      rewritten = true;
    }
    // Take all not backed constructors because we will never find them in definitions.
    for (KotlinConstructorInfo constructorInfo : constructorsWithNoBacking) {
      rewritten |= constructorInfo.rewrite(kmClass, null, appView);
    }
    // Find all constructors.
    KotlinMetadataMembersTracker rewrittenReferences = new KotlinMetadataMembersTracker(appView);
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo().isConstructor()) {
        KotlinConstructorInfo constructorInfo = method.getKotlinInfo().asConstructor();
        rewritten |= constructorInfo.rewrite(kmClass, method, appView);
        rewrittenReferences.add(method.getReference());
      }
    }
    // Rewrite functions, type-aliases and type-parameters.
    rewritten |=
        declarationContainerInfo.rewrite(
            kmClass.getFunctions()::add,
            kmClass.getProperties()::add,
            kmClass.getTypeAliases()::add,
            clazz,
            appView,
            rewrittenReferences);
    // Rewrite type parameters.
    rewritten |=
        rewriteList(
            appView, typeParameters, kmClass.getTypeParameters(), KotlinTypeParameterInfo::rewrite);
    // Rewrite super types.
    List<KmType> rewrittenSuperTypes = kmClass.getSupertypes();
    for (KotlinTypeInfo superType : superTypes) {
      // Ensure the rewritten super type is not this type.
      if (clazz.getType() != superType.rewriteType(appView.graphLens())) {
        rewritten |= superType.rewrite(rewrittenSuperTypes::add, appView);
      } else {
        rewritten = true;
      }
    }
    // Rewrite nested classes.
    List<String> rewrittenNestedClasses = kmClass.getNestedClasses();
    for (KotlinTypeReference nestedClass : this.nestedClasses) {
      Box<String> nestedDescriptorBox = new Box<>();
      boolean nestedClassRewritten =
          nestedClass.toRenamedBinaryNameOrDefault(nestedDescriptorBox::set, appView, null);
      if (nestedDescriptorBox.isSet()) {
        if (nestedClassRewritten) {
          // If the class is a nested class, it should be on the form Foo.Bar$Baz, where Baz
          // is the name we should record.
          String nestedDescriptor = nestedDescriptorBox.get();
          int innerClassIndex = nestedDescriptor.lastIndexOf(DescriptorUtils.INNER_CLASS_SEPARATOR);
          rewrittenNestedClasses.add(nestedDescriptor.substring(innerClassIndex + 1));
        } else {
          rewrittenNestedClasses.add(nestedClass.getOriginalName());
        }
      }
      rewritten |= nestedClassRewritten;
    }
    // Rewrite sealed sub-classes.
    List<String> rewrittenSealedClasses = kmClass.getSealedSubclasses();
    for (KotlinTypeReference sealedSubClass : sealedSubClasses) {
      rewritten |=
          sealedSubClass.toRenamedBinaryNameOrDefault(
              sealedName -> {
                if (sealedName != null) {
                  rewrittenSealedClasses.add(
                      sealedName.replace(
                          DescriptorUtils.INNER_CLASS_SEPARATOR,
                          DescriptorUtils.JAVA_PACKAGE_SEPARATOR));
                }
              },
              appView,
              null);
    }
    rewritten |= versionRequirements.rewrite(kmClass.getVersionRequirements()::addAll);
    if (inlineClassUnderlyingPropertyName != null && inlineClassUnderlyingType != null) {
      kmClass.setInlineClassUnderlyingPropertyName(inlineClassUnderlyingPropertyName);
      rewritten |=
          inlineClassUnderlyingType.rewrite(kmClass::setInlineClassUnderlyingType, appView);
    }
    rewritten |=
        rewriteList(
            appView,
            contextReceiverTypes,
            kmClass.getContextReceiverTypes(),
            KotlinTypeInfo::rewrite);
    JvmExtensionsKt.setJvmFlags(kmClass, jvmFlags);
    JvmExtensionsKt.setModuleName(kmClass, moduleName);
    if (anonymousObjectOrigin != null) {
      rewritten |=
          anonymousObjectOrigin.toRenamedBinaryNameOrDefault(
              renamedAnon -> {
                if (renamedAnon != null) {
                  JvmExtensionsKt.setAnonymousObjectOriginName(kmClass, renamedAnon);
                }
              },
              appView,
              null);
    }
    rewritten |=
        localDelegatedProperties.rewrite(
            JvmExtensionsKt.getLocalDelegatedProperties(kmClass)::add, appView);
    return Pair.create(
        Companion.writeClass(kmClass, getCompatibleKotlinInfo(), 0),
        rewritten || !originalMembersWithKotlinInfo.isEqual(rewrittenReferences, appView));
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public int[] getMetadataVersion() {
    return metadataVersion;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(constructorsWithNoBacking, constructor -> constructor::trace, definitionSupplier);
    declarationContainerInfo.trace(definitionSupplier);
    forEachApply(typeParameters, param -> param::trace, definitionSupplier);
    forEachApply(superTypes, type -> type::trace, definitionSupplier);
    forEachApply(sealedSubClasses, sealedClass -> sealedClass::trace, definitionSupplier);
    forEachApply(nestedClasses, nested -> nested::trace, definitionSupplier);
    forEachApply(contextReceiverTypes, nested -> nested::trace, definitionSupplier);
    localDelegatedProperties.trace(definitionSupplier);
    // TODO(b/154347404): trace enum entries.
    if (anonymousObjectOrigin != null) {
      anonymousObjectOrigin.trace(definitionSupplier);
    }
  }
}
