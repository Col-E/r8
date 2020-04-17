// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.Flag.Class.IS_COMPANION_OBJECT;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinFieldInfo;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinPropertyInfo;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.KmPropertyGroup;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

// Provides access to package/class-level Kotlin information.
public abstract class KotlinInfo<MetadataKind extends KotlinClassMetadata> {

  final DexClass clazz;
  private static final List<KmTypeParameter> EMPTY_TYPE_PARAMS = ImmutableList.of();

  KotlinInfo(MetadataKind metadata, DexClass clazz) {
    assert clazz != null;
    this.clazz = clazz;
    processMetadata(metadata);
  }

  // Subtypes will define how to process the given metadata.
  abstract void processMetadata(MetadataKind metadata);

  // Subtypes will define how to rewrite metadata after shrinking and minification.
  // Subtypes that represent subtypes of {@link KmDeclarationContainer} can use
  // {@link #rewriteDeclarationContainer} below.
  abstract void rewrite(AppView<AppInfoWithLiveness> appView, NamingLens lens);

  abstract KotlinClassHeader createHeader();

  public final List<KmTypeParameter> getTypeParameters() {
    if (!this.isClass()) {
      return EMPTY_TYPE_PARAMS;
    }
    return this.asClass().kmClass.getTypeParameters();
  }

  public enum Kind {
    Class, File, Synthetic, Part, Facade
  }

  public abstract Kind getKind();

  public boolean isClass() {
    return false;
  }

  public KotlinClass asClass() {
    return null;
  }

  public boolean isFile() {
    return false;
  }

  public KotlinFile asFile() {
    return null;
  }

  public boolean isSyntheticClass() {
    return false;
  }

  public KotlinSyntheticClass asSyntheticClass() {
    return null;
  }

  public boolean isClassPart() {
    return false;
  }

  public KotlinClassPart asClassPart() {
    return null;
  }

  public boolean isClassFacade() {
    return false;
  }

  public KotlinClassFacade asClassFacade() {
    return null;
  }

  boolean hasDeclarations() {
    return isClass() || isFile() || isClassPart();
  }

  KmDeclarationContainer getDeclarations() {
    if (isClass()) {
      return asClass().kmClass;
    } else if (isFile()) {
      return asFile().kmPackage;
    } else if (isClassPart()) {
      return asClassPart().kmPackage;
    } else {
      throw new Unreachable("Unexpected KotlinInfo: " + this);
    }
  }

  // {@link KmClass} and {@link KmPackage} are inherited from {@link KmDeclarationContainer} that
  // abstract functions and properties. Rewriting of those portions can be unified here.
  void rewriteDeclarationContainer(KotlinMetadataSynthesizer synthesizer) {
    assert clazz != null;

    KmDeclarationContainer kmDeclarationContainer = getDeclarations();
    rewriteFunctions(synthesizer, kmDeclarationContainer.getFunctions());
    rewriteProperties(synthesizer, kmDeclarationContainer.getProperties());
    rewriteTypeAliases(synthesizer, kmDeclarationContainer.getTypeAliases());
  }

  private void rewriteFunctions(KotlinMetadataSynthesizer synthesizer, List<KmFunction> functions) {
    functions.clear();
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }
      if (method.isKotlinFunction() || method.isKotlinExtensionFunction()) {
        KmFunction function = synthesizer.toRenamedKmFunction(method);
        if (function != null) {
          functions.add(function);
        }
      }
      // TODO(b/151194869): What should we do for methods that fall into this category---no mark?
    }
  }

  private void rewriteTypeAliases(
      KotlinMetadataSynthesizer synthesizer, List<KmTypeAlias> typeAliases) {
    Iterator<KmTypeAlias> iterator = typeAliases.iterator();
    while (iterator.hasNext()) {
      KmTypeAlias typeAlias = iterator.next();
      KotlinTypeInfo expandedRenamed =
          KotlinTypeInfo.create(typeAlias.expandedType).toRenamed(synthesizer);
      if (expandedRenamed == null) {
        // If the expanded type is pruned, the type-alias is also removed. Type-aliases can refer to
        // other type-aliases in the underlying type, however, we only remove a type-alias when the
        // expanded type is removed making it impossible to construct any type that references the
        // type-alias anyway.
        // TODO(b/151719926): Add a test for the above.
        iterator.remove();
        continue;
      }
      typeAlias.setExpandedType(expandedRenamed.asKmType());
      // Modify the underlying type (right-hand side) of the type-alias.
      KotlinTypeInfo underlyingRenamed =
          KotlinTypeInfo.create(typeAlias.underlyingType).toRenamed(synthesizer);
      if (underlyingRenamed == null) {
        Reporter reporter = synthesizer.appView.options().reporter;
        reporter.warning(
            KotlinMetadataDiagnostic.messageInvalidUnderlyingType(clazz, typeAlias.getName()));
        iterator.remove();
        continue;
      }
      typeAlias.setUnderlyingType(underlyingRenamed.asKmType());
    }
  }

  private void rewriteProperties(
      KotlinMetadataSynthesizer synthesizer, List<KmProperty> properties) {
    Map<String, KmPropertyGroup.Builder> propertyGroupBuilderMap = new HashMap<>();
    // Backing fields for a companion object are declared in its host class.
    Iterable<DexEncodedField> fields = clazz.fields();
    Predicate<DexEncodedField> backingFieldTester = DexEncodedField::isKotlinBackingField;
    List<KmTypeParameter> classTypeParameters = getTypeParameters();
    if (isClass()) {
      KotlinClass ktClass = asClass();
      if (IS_COMPANION_OBJECT.invoke(ktClass.kmClass.getFlags()) && ktClass.hostClass != null) {
        fields = ktClass.hostClass.fields();
        backingFieldTester = DexEncodedField::isKotlinBackingFieldForCompanionObject;
      }
    }
    for (DexEncodedField field : fields) {
      if (backingFieldTester.test(field)) {
        KotlinFieldInfo kotlinFieldInfo = field.getKotlinMemberInfo().asFieldInfo();
        assert kotlinFieldInfo != null;
        String name = kotlinFieldInfo.propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name,
                k -> KmPropertyGroup.builder(kotlinFieldInfo.flags, name, classTypeParameters));
        builder.foundBackingField(field);
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }
      if (method.isKotlinProperty() || method.isKotlinExtensionProperty()) {
        assert method.getKotlinMemberInfo().isPropertyInfo();
        KotlinPropertyInfo kotlinPropertyInfo = method.getKotlinMemberInfo().asPropertyInfo();
        String name = kotlinPropertyInfo.propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name,
                // Hitting here (creating a property builder) after visiting all fields means that
                // this property doesn't have a backing field. Don't use members' flags.
                k -> KmPropertyGroup.builder(kotlinPropertyInfo.flags, name, classTypeParameters));
        switch (kotlinPropertyInfo.memberKind) {
          case EXTENSION_PROPERTY_GETTER:
            builder.isExtensionGetter();
            // fallthrough;
          case PROPERTY_GETTER:
            builder.foundGetter(method, kotlinPropertyInfo);
            break;
          case EXTENSION_PROPERTY_SETTER:
            builder.isExtensionSetter();
            // fallthrough;
          case PROPERTY_SETTER:
            builder.foundSetter(method, kotlinPropertyInfo);
            break;
          case EXTENSION_PROPERTY_ANNOTATIONS:
            builder.isExtensionAnnotations();
            // fallthrough;
          case PROPERTY_ANNOTATIONS:
            builder.foundAnnotations(method);
            break;
          default:
            throw new Unreachable("Not a Kotlin property: " + method.getKotlinMemberInfo());
        }
      }
      // TODO(b/151194869): What should we do for methods that fall into this category---no mark?
    }
    properties.clear();
    for (KmPropertyGroup.Builder builder : propertyGroupBuilderMap.values()) {
      KmPropertyGroup group = builder.build();
      if (group == null) {
        continue;
      }
      KmProperty property = group.toRenamedKmProperty(synthesizer);
      if (property != null) {
        properties.add(property);
      }
    }
  }

  public abstract String toString(String indent);

  @Override
  public String toString() {
    return toString("");
  }
}
