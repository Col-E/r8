// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.StringUtils.LINE_SEPARATOR;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.StringUtils;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import kotlinx.metadata.InconsistentKotlinMetadataException;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmLambda;
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
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinMetadataWriter {

  static final String INDENT = "  ";

  public static void writeKotlinMetadataAnnotation(
      String prefix, DexAnnotation annotation, PrintStream ps, Kotlin kotlin) {
    assert annotation.annotation.type == kotlin.factory.kotlinMetadataType;
    try {
      KotlinClassMetadata kMetadata =
          KotlinClassMetadataReader.toKotlinClassMetadata(kotlin, annotation.annotation);
      ps.println(kotlinMetadataToString(prefix, kMetadata));
    } catch (Throwable ignored) {
    }
  }

  public static String kotlinMetadataToString(String prefix, KotlinClassMetadata kMetadata) {
    if (kMetadata instanceof KotlinClassMetadata.Class) {
      return kotlinClassMetadataToString((KotlinClassMetadata.Class) kMetadata, prefix);
    } else if (kMetadata instanceof KotlinClassMetadata.FileFacade) {
      // e.g., B.kt becomes class `BKt`
      return kotlinFileFacadeMetadataToString((KotlinClassMetadata.FileFacade) kMetadata, prefix);
    } else if (kMetadata instanceof KotlinClassMetadata.MultiFileClassFacade) {
      // multi-file class with the same @JvmName.
      return kotlinMultiFileClassFacadeMetadataString(
          (KotlinClassMetadata.MultiFileClassFacade) kMetadata, prefix);
    } else if (kMetadata instanceof KotlinClassMetadata.MultiFileClassPart) {
      // A single file, which is part of multi-file class.
      return kotlinMultiFileClassPartToString(
          (KotlinClassMetadata.MultiFileClassPart) kMetadata, prefix);
    } else if (kMetadata instanceof KotlinClassMetadata.SyntheticClass) {
      return kotlinSyntheticClassToString((KotlinClassMetadata.SyntheticClass) kMetadata, prefix);
    } else {
      throw new Unreachable("An error would be thrown before in createKotlinInfo");
    }
  }

  private static String kotlinClassMetadataToString(
      KotlinClassMetadata.Class kMetadata, String indent) {
    StringBuilder sb = new StringBuilder(indent);
    KotlinMetadataWriter.appendKmSection(
        indent,
        "Metadata.Class",
        sb,
        newIndent -> {
          KotlinMetadataWriter.appendKmClass(newIndent, sb, kMetadata.toKmClass());
        });
    return sb.toString();
  }

  private static String kotlinFileFacadeMetadataToString(
      KotlinClassMetadata.FileFacade kMetadata, String indent) {
    StringBuilder sb = new StringBuilder(indent);
    KotlinMetadataWriter.appendKmSection(
        indent,
        "Metadata.FileFacade",
        sb,
        newIndent -> {
          KotlinMetadataWriter.appendKmPackage(newIndent, sb, kMetadata.toKmPackage());
        });
    return sb.toString();
  }

  private static String kotlinMultiFileClassFacadeMetadataString(
      KotlinClassMetadata.MultiFileClassFacade kMetadata, String indent) {
    return indent
        + "MetaData.MultiFileClassFacade("
        + StringUtils.join(kMetadata.getPartClassNames(), ", ")
        + ")";
  }

  private static String kotlinMultiFileClassPartToString(
      KotlinClassMetadata.MultiFileClassPart kMetadata, String indent) {
    StringBuilder sb = new StringBuilder(indent);
    KotlinMetadataWriter.appendKmSection(
        indent,
        "Metadata.MultiFileClassPart",
        sb,
        newIndent -> {
          KotlinMetadataWriter.appendKeyValue(
              newIndent, "facadeClassName", sb, kMetadata.getFacadeClassName());
          KotlinMetadataWriter.appendKmPackage(newIndent, sb, kMetadata.toKmPackage());
        });
    return sb.toString();
  }

  private static String kotlinSyntheticClassToString(
      KotlinClassMetadata.SyntheticClass kMetadata, String indent) {
    StringBuilder sb = new StringBuilder();
    KotlinMetadataWriter.appendKmSection(
        indent,
        "Metadata.SyntheticClass",
        sb,
        newIndent -> {
          try {
            KmLambda kmLambda = kMetadata.toKmLambda();
            if (kmLambda != null) {
              KotlinMetadataWriter.appendKeyValue(
                  newIndent,
                  "function",
                  sb,
                  nextIndent -> {
                    KotlinMetadataWriter.appendKmFunction(nextIndent, sb, kmLambda.function);
                  });
            } else {
              KotlinMetadataWriter.appendKeyValue(newIndent, "function", sb, "null");
            }
          } catch (InconsistentKotlinMetadataException ex) {
            appendKeyValue(newIndent, "function", sb, ex.getMessage());
          }
        });
    return sb.toString();
  }

  private static <T> void appendKmHelper(
      String key, StringBuilder sb, Action appendContent, String start, String end) {
    sb.append(key);
    sb.append(start);
    appendContent.execute();
    sb.append(end);
  }

  public static <T> void appendKmSection(
      String indent, String typeDescription, StringBuilder sb, Consumer<String> appendContent) {
    appendKmHelper(
        typeDescription,
        sb,
        () -> appendContent.accept(indent + INDENT),
        "{" + LINE_SEPARATOR,
        indent + "}");
  }

  private static <T> void appendKmList(
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

  private static void appendKeyValue(
      String indent, String key, StringBuilder sb, Consumer<String> appendValue) {
    sb.append(indent);
    appendKmHelper(key, sb, () -> appendValue.accept(indent), ": ", "," + LINE_SEPARATOR);
  }

  public static void appendKeyValue(String indent, String key, StringBuilder sb, String value) {
    sb.append(indent);
    appendKmHelper(key, sb, () -> sb.append(value), ": ", "," + LINE_SEPARATOR);
  }

  private static void appendKmDeclarationContainer(
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
                  .sorted(
                      Comparator.comparing(
                          kmFunction -> JvmExtensionsKt.getSignature(kmFunction).asString()))
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
                  .sorted(
                      Comparator.comparing(
                          kmProperty -> {
                            JvmMethodSignature signature =
                                JvmExtensionsKt.getGetterSignature(kmProperty);
                            if (signature != null) {
                              return signature.asString();
                            }
                            signature = JvmExtensionsKt.getSetterSignature(kmProperty);
                            if (signature != null) {
                              return signature.asString();
                            }
                            JvmFieldSignature fieldSignature =
                                JvmExtensionsKt.getFieldSignature(kmProperty);
                            if (fieldSignature != null) {
                              return fieldSignature.asString();
                            }
                            return kmProperty.getName();
                          }))
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

  public static void appendKmPackage(String indent, StringBuilder sb, KmPackage kmPackage) {
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

  public static void appendKmClass(String indent, StringBuilder sb, KmClass kmClass) {
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
              kmClass.getConstructors().stream()
                  .sorted(
                      Comparator.comparing(
                          kmConstructor -> JvmExtensionsKt.getSignature(kmConstructor).asString()))
                  .collect(Collectors.toList()),
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

  public static void appendKmFunction(String indent, StringBuilder sb, KmFunction function) {
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
}
