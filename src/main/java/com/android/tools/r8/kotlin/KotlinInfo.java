// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.StringUtils.LINE_SEPARATOR;
import static kotlinx.metadata.Flag.Class.IS_COMPANION_OBJECT;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinFieldInfo;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinPropertyInfo;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.KmPropertyGroup;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmPackage;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
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
  abstract void rewrite(
      AppView<AppInfoWithLiveness> appView, SubtypingInfo subtypingInfo, NamingLens lens);

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

  static final String INDENT = "  ";

  private static <T> void appendKmHelper(
      String key, StringBuilder sb, Action appendContent, String start, String end) {
    sb.append(key);
    sb.append(start);
    appendContent.execute();
    sb.append(end);
  }

  static <T> void appendKmSection(
      String indent, String typeDescription, StringBuilder sb, Consumer<String> appendContent) {
    appendKmHelper(
        typeDescription,
        sb,
        () -> appendContent.accept(indent + INDENT),
        "{" + LINE_SEPARATOR,
        indent + "}");
  }

  static <T> void appendKmList(
      String indent,
      String typeDescription,
      StringBuilder sb,
      List<T> items,
      BiConsumer<String, T> appendItem) {
    if (items.isEmpty()) {
      sb.append(typeDescription).append("[]");
      return;
    }
    appendKmHelper(
        typeDescription,
        sb,
        () -> {
          for (T kmItem : items) {
            sb.append(indent).append(INDENT);
            appendItem.accept(indent + INDENT, kmItem);
            sb.append(LINE_SEPARATOR);
          }
        },
        "[" + LINE_SEPARATOR,
        indent + "]");
  }

  static void appendKeyValue(
      String indent, String key, StringBuilder sb, Consumer<String> appendValue) {
    sb.append(indent);
    appendKmHelper(key, sb, () -> appendValue.accept(indent), ": ", "," + LINE_SEPARATOR);
  }

  static void appendKeyValue(String indent, String key, StringBuilder sb, String value) {
    sb.append(indent);
    appendKmHelper(key, sb, () -> sb.append(value), ": ", "," + LINE_SEPARATOR);
  }

  static void appendKmDeclarationContainer(
      String indent, StringBuilder sb, KmDeclarationContainer container) {
    appendKeyValue(
        indent,
        "functions",
        sb,
        newIndent -> {
          appendKmList(
              newIndent,
              "KmFunction",
              sb,
              container.getFunctions().stream()
                  .sorted(Comparator.comparing(KmFunction::getName))
                  .collect(Collectors.toList()),
              (nextIndent, kmFunction) -> {
                appendKmFunction(nextIndent, sb, kmFunction);
              });
        });
    appendKeyValue(
        indent,
        "properties",
        sb,
        newIndent -> {
          appendKmList(
              newIndent,
              "KmProperty",
              sb,
              container.getProperties().stream()
                  .sorted(Comparator.comparing(KmProperty::getName))
                  .collect(Collectors.toList()),
              (nextIndent, kmProperty) -> {
                appendKmProperty(nextIndent, sb, kmProperty);
              });
        });
    appendKeyValue(
        indent,
        "typeAliases",
        sb,
        newIndent -> {
          appendKmList(
              newIndent,
              "KmTypeAlias",
              sb,
              container.getTypeAliases().stream()
                  .sorted(Comparator.comparing(KmTypeAlias::getName))
                  .collect(Collectors.toList()),
              (nextIndent, kmTypeAlias) -> {
                appendTypeAlias(nextIndent, sb, kmTypeAlias);
              });
        });
  }

  static void appendKmPackage(String indent, StringBuilder sb, KmPackage kmPackage) {
    appendKmDeclarationContainer(indent, sb, kmPackage);
    appendKeyValue(indent, "moduleName", sb, JvmExtensionsKt.getModuleName(kmPackage));
    appendKeyValue(
        indent,
        "localDelegatedProperties",
        sb,
        nextIndent -> {
          appendKmList(
              nextIndent,
              "KmProperty",
              sb,
              JvmExtensionsKt.getLocalDelegatedProperties(kmPackage),
              (nextNextIndent, kmProperty) -> {
                appendKmProperty(nextNextIndent, sb, kmProperty);
              });
        });
  }

  static void appendKmClass(String indent, StringBuilder sb, KmClass kmClass) {
    appendKeyValue(indent, "flags", sb, kmClass.getFlags() + "");
    appendKeyValue(indent, "name", sb, kmClass.getName());
    appendKeyValue(
        indent,
        "typeParameters",
        sb,
        newIndent -> {
          appendTypeParameters(newIndent, sb, kmClass.getTypeParameters());
        });
    appendKeyValue(
        indent,
        "superTypes",
        sb,
        newIndent -> {
          appendKmList(
              newIndent,
              "KmType",
              sb,
              kmClass.getSupertypes(),
              (nextIndent, kmType) -> {
                appendKmType(nextIndent, sb, kmType);
              });
        });
    String companionObject = kmClass.getCompanionObject();
    appendKeyValue(
        indent, "enumEntries", sb, "[" + StringUtils.join(kmClass.getEnumEntries(), ",") + "]");
    appendKeyValue(
        indent, "companionObject", sb, companionObject == null ? "null" : companionObject);
    appendKeyValue(
        indent,
        "sealedSubclasses",
        sb,
        "[" + StringUtils.join(kmClass.getSealedSubclasses(), ",") + "]");
    appendKeyValue(
        indent, "nestedClasses", sb, "[" + StringUtils.join(kmClass.getNestedClasses(), ",") + "]");
    appendKeyValue(
        indent,
        "anonymousObjectOriginName",
        sb,
        JvmExtensionsKt.getAnonymousObjectOriginName(kmClass));
    appendKeyValue(indent, "moduleName", sb, JvmExtensionsKt.getModuleName(kmClass));
    appendKeyValue(
        indent,
        "localDelegatedProperties",
        sb,
        nextIndent -> {
          appendKmList(
              nextIndent,
              "KmProperty",
              sb,
              JvmExtensionsKt.getLocalDelegatedProperties(kmClass),
              (nextNextIndent, kmProperty) -> {
                appendKmProperty(nextNextIndent, sb, kmProperty);
              });
        });
    appendKeyValue(
        indent,
        "constructors",
        sb,
        newIndent -> {
          appendKmList(
              newIndent,
              "KmConstructor",
              sb,
              kmClass.getConstructors(),
              (nextIndent, constructor) -> {
                appendKmConstructor(nextIndent, sb, constructor);
              });
        });
    appendKmDeclarationContainer(indent, sb, kmClass);
  }

  private static void appendKmConstructor(
      String indent, StringBuilder sb, KmConstructor constructor) {
    appendKmSection(
        indent,
        "KmConstructor",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "flags", sb, constructor.getFlags() + "");
          appendKeyValue(
              newIndent,
              "valueParameters",
              sb,
              nextIndent ->
                  appendValueParameters(nextIndent, sb, constructor.getValueParameters()));
          JvmMethodSignature signature = JvmExtensionsKt.getSignature(constructor);
          appendKeyValue(
              newIndent, "signature", sb, signature != null ? signature.asString() : "null");
        });
  }

  private static void appendKmFunction(String indent, StringBuilder sb, KmFunction function) {
    appendKmSection(
        indent,
        "KmFunction",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "flags", sb, function.getFlags() + "");
          appendKeyValue(newIndent, "name", sb, function.getName());
          appendKeyValue(
              newIndent,
              "receiverParameterType",
              sb,
              nextIndent -> appendKmType(nextIndent, sb, function.getReceiverParameterType()));
          appendKeyValue(
              newIndent,
              "returnType",
              sb,
              nextIndent -> appendKmType(nextIndent, sb, function.getReturnType()));
          appendKeyValue(
              newIndent,
              "typeParameters",
              sb,
              nextIndent -> appendTypeParameters(nextIndent, sb, function.getTypeParameters()));
          appendKeyValue(
              newIndent,
              "valueParameters",
              sb,
              nextIndent -> appendValueParameters(nextIndent, sb, function.getValueParameters()));
          JvmMethodSignature signature = JvmExtensionsKt.getSignature(function);
          appendKeyValue(
              newIndent, "signature", sb, signature != null ? signature.asString() : "null");
          appendKeyValue(
              newIndent,
              "lambdaClassOriginName",
              sb,
              JvmExtensionsKt.getLambdaClassOriginName(function));
        });
  }

  private static void appendKmProperty(String indent, StringBuilder sb, KmProperty kmProperty) {
    appendKmSection(
        indent,
        "KmProperty",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "flags", sb, kmProperty.getFlags() + "");
          appendKeyValue(newIndent, "name", sb, kmProperty.getName());
          appendKeyValue(
              newIndent,
              "receiverParameterType",
              sb,
              nextIndent -> appendKmType(nextIndent, sb, kmProperty.getReceiverParameterType()));
          appendKeyValue(
              newIndent,
              "returnType",
              sb,
              nextIndent -> appendKmType(nextIndent, sb, kmProperty.getReturnType()));
          appendKeyValue(
              newIndent,
              "typeParameters",
              sb,
              nextIndent -> appendTypeParameters(nextIndent, sb, kmProperty.getTypeParameters()));
          appendKeyValue(newIndent, "getterFlags", sb, kmProperty.getGetterFlags() + "");
          appendKeyValue(newIndent, "setterFlags", sb, kmProperty.getSetterFlags() + "");
          appendKeyValue(
              newIndent,
              "setterParameter",
              sb,
              nextIndent -> appendValueParameter(nextIndent, sb, kmProperty.getSetterParameter()));
          appendKeyValue(newIndent, "jvmFlags", sb, JvmExtensionsKt.getJvmFlags(kmProperty) + "");
          JvmFieldSignature fieldSignature = JvmExtensionsKt.getFieldSignature(kmProperty);
          appendKeyValue(
              newIndent,
              "fieldSignature",
              sb,
              fieldSignature != null ? fieldSignature.asString() : "null");
          JvmMethodSignature getterSignature = JvmExtensionsKt.getGetterSignature(kmProperty);
          appendKeyValue(
              newIndent,
              "getterSignature",
              sb,
              getterSignature != null ? getterSignature.asString() : "null");
          JvmMethodSignature setterSignature = JvmExtensionsKt.getSetterSignature(kmProperty);
          appendKeyValue(
              newIndent,
              "setterSignature",
              sb,
              setterSignature != null ? setterSignature.asString() : "null");
          JvmMethodSignature syntheticMethod =
              JvmExtensionsKt.getSyntheticMethodForAnnotations(kmProperty);
          appendKeyValue(
              newIndent,
              "syntheticMethodForAnnotations",
              sb,
              syntheticMethod != null ? syntheticMethod.asString() : "null");
        });
  }

  private static void appendKmType(String indent, StringBuilder sb, KmType kmType) {
    if (kmType == null) {
      sb.append("null");
      return;
    }
    appendKmSection(
        indent,
        "KmType",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "flags", sb, kmType.getFlags() + "");
          appendKeyValue(newIndent, "classifier", sb, kmType.classifier.toString());
          appendKeyValue(
              newIndent,
              "arguments",
              sb,
              nextIndent -> {
                appendKmList(
                    nextIndent,
                    "KmTypeProjection",
                    sb,
                    kmType.getArguments(),
                    (nextNextIndent, kmTypeProjection) -> {
                      appendKmTypeProjection(nextNextIndent, sb, kmTypeProjection);
                    });
              });
          appendKeyValue(
              newIndent,
              "abbreviatedType",
              sb,
              nextIndent -> appendKmType(newIndent, sb, kmType.getAbbreviatedType()));
          appendKeyValue(
              newIndent,
              "outerType",
              sb,
              nextIndent -> appendKmType(newIndent, sb, kmType.getOuterType()));
          appendKeyValue(newIndent, "raw", sb, JvmExtensionsKt.isRaw(kmType) + "");
          appendKeyValue(
              newIndent,
              "annotations",
              sb,
              nextIndent -> {
                appendKmList(
                    nextIndent,
                    "KmAnnotion",
                    sb,
                    JvmExtensionsKt.getAnnotations(kmType),
                    (nextNextIndent, kmAnnotation) -> {
                      appendKmAnnotation(nextNextIndent, sb, kmAnnotation);
                    });
              });
        });
  }

  private static void appendKmTypeProjection(
      String indent, StringBuilder sb, KmTypeProjection projection) {
    appendKmSection(
        indent,
        "KmTypeProjection",
        sb,
        newIndent -> {
          appendKeyValue(
              newIndent,
              "type",
              sb,
              nextIndent -> {
                appendKmType(nextIndent, sb, projection.getType());
              });
          if (projection.getVariance() != null) {
            appendKeyValue(newIndent, "variance", sb, projection.getVariance().name());
          }
        });
  }

  private static void appendValueParameters(
      String indent, StringBuilder sb, List<KmValueParameter> valueParameters) {
    appendKmList(
        indent,
        "KmValueParameter",
        sb,
        valueParameters,
        (newIndent, parameter) -> {
          appendValueParameter(newIndent, sb, parameter);
        });
  }

  private static void appendValueParameter(
      String indent, StringBuilder sb, KmValueParameter valueParameter) {
    if (valueParameter == null) {
      sb.append("null");
      return;
    }
    appendKmSection(
        indent,
        "KmValueParameter",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "flags", sb, valueParameter.getFlags() + "");
          appendKeyValue(newIndent, "name", sb, valueParameter.getName());
          appendKeyValue(
              newIndent,
              "type",
              sb,
              nextIndent -> {
                appendKmType(nextIndent, sb, valueParameter.getType());
              });
          appendKeyValue(
              newIndent,
              "varargElementType",
              sb,
              nextIndent -> {
                appendKmType(nextIndent, sb, valueParameter.getVarargElementType());
              });
        });
  }

  private static void appendTypeParameters(
      String indent, StringBuilder sb, List<KmTypeParameter> typeParameters) {
    appendKmList(
        indent,
        "KmTypeParameter",
        sb,
        typeParameters,
        (newIndent, parameter) -> {
          appendTypeParameter(newIndent, sb, parameter);
        });
  }

  private static void appendTypeParameter(
      String indent, StringBuilder sb, KmTypeParameter typeParameter) {
    appendKmSection(
        indent,
        "KmTypeParameter",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "id", sb, typeParameter.getId() + "");
          appendKeyValue(newIndent, "flags", sb, typeParameter.getFlags() + "");
          appendKeyValue(newIndent, "name", sb, typeParameter.getName());
          appendKeyValue(newIndent, "variance", sb, typeParameter.getVariance().name());
          appendKeyValue(
              newIndent,
              "upperBounds",
              sb,
              nextIndent -> {
                appendKmList(
                    nextIndent,
                    "KmType",
                    sb,
                    typeParameter.getUpperBounds(),
                    (nextNextIndent, kmType) -> {
                      appendKmType(nextNextIndent, sb, kmType);
                    });
              });
          appendKeyValue(
              newIndent,
              "extensions",
              sb,
              nextIndent -> {
                appendKmList(
                    nextIndent,
                    "KmAnnotion",
                    sb,
                    JvmExtensionsKt.getAnnotations(typeParameter),
                    (nextNextIndent, kmAnnotation) -> {
                      appendKmAnnotation(nextNextIndent, sb, kmAnnotation);
                    });
              });
        });
  }

  private static void appendTypeAlias(String indent, StringBuilder sb, KmTypeAlias kmTypeAlias) {
    appendKmSection(
        indent,
        "KmTypeAlias",
        sb,
        newIndent -> {
          appendKeyValue(
              newIndent,
              "annotations",
              sb,
              nextIndent -> {
                appendKmList(
                    nextIndent,
                    "KmAnnotation",
                    sb,
                    kmTypeAlias.getAnnotations(),
                    (nextNextIndent, kmAnnotation) -> {
                      appendKmAnnotation(nextNextIndent, sb, kmAnnotation);
                    });
              });
          appendKeyValue(
              newIndent,
              "expandedType",
              sb,
              nextIndent -> {
                appendKmType(nextIndent, sb, kmTypeAlias.expandedType);
              });
          appendKeyValue(newIndent, "flags", sb, kmTypeAlias.getFlags() + "");
          appendKeyValue(newIndent, "name", sb, kmTypeAlias.getName());
          appendKeyValue(
              newIndent,
              "typeParameters",
              sb,
              nextIndent -> {
                appendTypeParameters(nextIndent, sb, kmTypeAlias.getTypeParameters());
              });
          appendKeyValue(
              newIndent,
              "underlyingType",
              sb,
              nextIndent -> {
                appendKmType(nextIndent, sb, kmTypeAlias.underlyingType);
              });
        });
  }

  private static void appendKmAnnotation(
      String indent, StringBuilder sb, KmAnnotation kmAnnotation) {
    appendKmSection(
        indent,
        "KmAnnotation",
        sb,
        newIndent -> {
          appendKeyValue(newIndent, "className", sb, kmAnnotation.getClassName());
          appendKeyValue(
              newIndent,
              "arguments",
              sb,
              nextIndent -> {
                Map<String, KmAnnotationArgument<?>> arguments = kmAnnotation.getArguments();
                for (String key : arguments.keySet()) {
                  appendKeyValue(nextIndent, key, sb, arguments.get(key).toString());
                }
              });
        });
  }

  @Override
  public String toString() {
    return toString("");
  }
}
