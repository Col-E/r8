// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmFunction;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.KmPropertyGroup;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

// Provides access to package/class-level Kotlin information.
public abstract class KotlinInfo<MetadataKind extends KotlinClassMetadata> {
  final DexClass clazz;

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

  // {@link KmClass} and {@link KmPackage} are inherited from {@link KmDeclarationContainer} that
  // abstract functions and properties. Rewriting of those portions can be unified here.
  void rewriteDeclarationContainer(
      KmDeclarationContainer kmDeclarationContainer,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    assert clazz != null;

    Map<String, KmPropertyGroup.Builder> propertyGroupBuilderMap = new HashMap<>();
    List<KmFunction> functions = kmDeclarationContainer.getFunctions();
    functions.clear();
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }

      if (method.isKotlinFunction() || method.isKotlinExtensionFunction()) {
        KmFunction function = toRenamedKmFunction(method, appView, lens);
        if (function != null) {
          functions.add(function);
        }
        continue;
      }
      if (method.isKotlinProperty() || method.isKotlinExtensionProperty()) {
        String name = method.getKotlinMemberInfo().propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name, k -> KmPropertyGroup.builder(method.getKotlinMemberInfo().flag, name));
        switch (method.getKotlinMemberInfo().memberKind) {
          case EXTENSION_PROPERTY_GETTER:
            builder.isExtensionGetter();
            // fallthrough;
          case PROPERTY_GETTER:
            builder.foundGetter(method);
            break;
          case EXTENSION_PROPERTY_SETTER:
            builder.isExtensionSetter();
            // fallthrough;
          case PROPERTY_SETTER:
            builder.foundSetter(method);
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
        continue;
      }

      // TODO(b/70169921): What should we do for methods that fall into this category---no mark?
    }

    for (DexEncodedField field : clazz.fields()) {
      if (field.isKotlinBackingField()) {
        String name = field.getKotlinMemberInfo().propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name, k -> KmPropertyGroup.builder(field.getKotlinMemberInfo().flag, name));
        builder.foundBackingField(field);
      }
    }

    List<KmProperty> properties = kmDeclarationContainer.getProperties();
    properties.clear();
    for (KmPropertyGroup.Builder builder : propertyGroupBuilderMap.values()) {
      KmPropertyGroup group = builder.build();
      if (group == null) {
        continue;
      }
      KmProperty property = group.toRenamedKmProperty(appView, lens);
      if (property != null) {
        properties.add(property);
      }
    }
  }
}
