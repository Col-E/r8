// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.toRenamedKmFunction;
import static kotlinx.metadata.Flag.Class.IS_COMPANION_OBJECT;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.KmPropertyGroup;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.KmTypeParameter;
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
  void rewriteDeclarationContainer(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    assert clazz != null;

    KmDeclarationContainer kmDeclarationContainer = getDeclarations();
    rewriteFunctions(appView, lens, kmDeclarationContainer.getFunctions());
    rewriteProperties(appView, lens, kmDeclarationContainer.getProperties());
  }

  private void rewriteFunctions(
      AppView<AppInfoWithLiveness> appView, NamingLens lens, List<KmFunction> functions) {
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
      }
      // TODO(b/151194869): What should we do for methods that fall into this category---no mark?
    }
  }

  private void rewriteProperties(
      AppView<AppInfoWithLiveness> appView, NamingLens lens, List<KmProperty> properties) {
    Map<String, KmPropertyGroup.Builder> propertyGroupBuilderMap = new HashMap<>();
    // Backing fields for a companion object are declared in its host class.
    Iterable<DexEncodedField> fields = clazz.fields();
    Predicate<DexEncodedField> backingFieldTester = DexEncodedField::isKotlinBackingField;
    if (isClass()) {
      KotlinClass ktClass = asClass();
      if (IS_COMPANION_OBJECT.invoke(ktClass.kmClass.getFlags()) && ktClass.hostClass != null) {
        fields = ktClass.hostClass.fields();
        backingFieldTester = DexEncodedField::isKotlinBackingFieldForCompanionObject;
      }
    }
    for (DexEncodedField field : fields) {
      if (backingFieldTester.test(field)) {
        String name = field.getKotlinMemberInfo().propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name,
                k -> KmPropertyGroup.builder(field.getKotlinMemberInfo().propertyFlags, name));
        builder.foundBackingField(field);
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }
      if (method.isKotlinProperty() || method.isKotlinExtensionProperty()) {
        String name = method.getKotlinMemberInfo().propertyName;
        assert name != null;
        KmPropertyGroup.Builder builder =
            propertyGroupBuilderMap.computeIfAbsent(
                name,
                // Hitting here (creating a property builder) after visiting all fields means that
                // this property doesn't have a backing field. Don't use members' flags.
                k -> KmPropertyGroup.builder(method.getKotlinMemberInfo().propertyFlags, name));
        switch (method.getKotlinMemberInfo().memberKind) {
          case EXTENSION_PROPERTY_GETTER:
            builder.isExtensionGetter();
            // fallthrough;
          case PROPERTY_GETTER:
            builder.foundGetter(method, method.getKotlinMemberInfo().flags);
            break;
          case EXTENSION_PROPERTY_SETTER:
            builder.isExtensionSetter();
            // fallthrough;
          case PROPERTY_SETTER:
            builder.foundSetter(method, method.getKotlinMemberInfo().flags);
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
      KmProperty property = group.toRenamedKmProperty(appView, lens);
      if (property != null) {
        properties.add(property);
      }
    }
  }

  public abstract String toString(String indent);

  String kmDeclarationContainerToString(String indent) {
    StringBuilder sb = new StringBuilder();
    KmDeclarationContainer declarations = getDeclarations();
    appendKmSection(indent, "functions", declarations.getFunctions(), this::kmFunctionToString, sb);
    appendKmSection(
        indent, "properties", declarations.getProperties(), this::kmPropertyToString, sb);
    appendKmSection(
        indent, "typeAliases", declarations.getTypeAliases(), this::kmTypeAliasToString, sb);
    return sb.toString();
  }

  final String INDENT = "  ";

  private <T> void appendKmSection(
      String indent,
      String header,
      List<T> items,
      BiFunction<String, T, String> stringify,
      StringBuilder sb) {
    if (items.size() > 0) {
      sb.append(indent);
      sb.append(header);
      sb.append(": [");
      sb.append(StringUtils.LINE_SEPARATOR);
    }
    for (T item : items) {
      sb.append(stringify.apply(indent + INDENT, item));
      sb.append(",");
      sb.append(StringUtils.LINE_SEPARATOR);
    }
    if (items.size() > 0) {
      sb.append(indent);
      sb.append("]");
      sb.append(StringUtils.LINE_SEPARATOR);
    }
  }

  private String kmFunctionToString(String indent, KmFunction function) {
    assert function != null;
    StringBuilder sb = new StringBuilder();
    sb.append(indent);
    sb.append("KmFunction {");
    sb.append(StringUtils.LINE_SEPARATOR);
    String newIndent = indent + INDENT;
    KmType receiverParameterType = function.getReceiverParameterType();
    appendKeyValue(
        newIndent,
        "receiverParameterType",
        receiverParameterType == null ? "null" : kmTypeToString(receiverParameterType),
        sb);
    appendKeyValue(newIndent, "returnType", kmTypeToString(function.returnType), sb);
    appendKeyValue(newIndent, "name", function.getName(), sb);
    // TODO(b/148581822): Print flags, generic signature etc.
    sb.append(indent);
    sb.append("}");
    return sb.toString();
  }

  private String kmPropertyToString(String indent, KmProperty property) {
    // TODO(b/148581822): Add information.
    return indent + "KmProperty { " + property + "}";
  }

  private String kmTypeAliasToString(String indent, KmTypeAlias alias) {
    assert alias != null;
    StringBuilder sb = new StringBuilder(indent);
    sb.append("KmTypeAlias {");
    sb.append(StringUtils.LINE_SEPARATOR);
    String newIndent = indent + INDENT;
    appendKeyValue(newIndent, "name", alias.getName(), sb);
    if (!alias.getTypeParameters().isEmpty()) {
      appendKeyValue(
          newIndent,
          "typeParameters",
          alias.getTypeParameters().stream()
              .map(KmTypeParameter::getName)
              .collect(Collectors.joining(",")),
          sb);
    }
    appendType(newIndent, "underlyingType", alias.underlyingType, sb);
    appendType(newIndent, "expandedType", alias.expandedType, sb);
    // TODO(b/151783973): Extend with annotations.
    sb.append(indent);
    sb.append("}");
    return sb.toString();
  }

  void appendType(String indent, String key, KmType kmType, StringBuilder sb) {
    sb.append(indent);
    sb.append(key);
    sb.append(" {");
    sb.append(StringUtils.LINE_SEPARATOR);
    String newIndent = indent + INDENT;
    appendKeyValue(newIndent, "classifier", kmType.classifier.toString(), sb);
    if (!kmType.getArguments().isEmpty()) {
      appendKeyValue(
          newIndent,
          "arguments",
          kmType.getArguments().stream()
              .map(arg -> arg.getType().classifier.toString())
              .collect(Collectors.joining(",")),
          sb);
    }
    sb.append(indent);
    sb.append("}");
    sb.append(StringUtils.LINE_SEPARATOR);
  }

  void appendKeyValue(String indent, String key, String value, StringBuilder sb) {
    sb.append(indent);
    sb.append(key);
    sb.append(": ");
    sb.append(value);
    sb.append(StringUtils.LINE_SEPARATOR);
  }

  private String kmTypeToString(KmType type) {
    return DescriptorUtils.getDescriptorFromKmType(type);
  }

  @Override
  public String toString() {
    return toString("");
  }
}
