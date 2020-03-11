// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;

import com.android.tools.r8.errors.InvalidDescriptorException;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmConstructorExtensionVisitor;
import kotlinx.metadata.KmConstructorVisitor;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmFunctionExtensionVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;

class KotlinMetadataJvmExtensionUtils {

  // Mappings from Kotlin types to JVM types (of String)
  private static final Map<String, String> knownTypeConversion =
      // See {@link org.jetbrains.kotlin.metadata.jvm.deserialization.ClassMapperLite}
      ImmutableMap.<String, String>builder()
          // Boxed primitives and arrays
          .put(addKotlinPrefix("Boolean;"), "Z")
          .put(addKotlinPrefix("BooleanArray;"), "[Z")
          .put(addKotlinPrefix("Byte;"), "B")
          .put(addKotlinPrefix("ByteArray;"), "[B")
          .put(addKotlinPrefix("Char;"), "C")
          .put(addKotlinPrefix("CharArray;"), "[C")
          .put(addKotlinPrefix("Short;"), "S")
          .put(addKotlinPrefix("ShortArray;"), "[S")
          .put(addKotlinPrefix("Int;"), "I")
          .put(addKotlinPrefix("IntArray;"), "[I")
          .put(addKotlinPrefix("Long;"), "J")
          .put(addKotlinPrefix("LongArray;"), "[J")
          .put(addKotlinPrefix("Float;"), "F")
          .put(addKotlinPrefix("FloatArray;"), "[F")
          .put(addKotlinPrefix("Double;"), "D")
          .put(addKotlinPrefix("DoubleArray;"), "[D")
          // Other intrinsics
          .put(addKotlinPrefix("Unit;"), "V")
          .put(addKotlinPrefix("Any;"), "Ljava/lang/Object;")
          .put(addKotlinPrefix("Nothing;"), "Ljava/lang/Void;")
          .putAll(ImmutableList.of(
              "String", "CharSequence", "Throwable", "Cloneable", "Number", "Comparable", "Enum")
                  .stream().collect(Collectors.toMap(
                      t -> addKotlinPrefix(t + ";"),
                      t -> "Ljava/lang/" + t + ";")))
          // Collections
          .putAll(ImmutableList.of("Iterator", "Collection", "List", "Set", "Map", "ListIterator")
              .stream().collect(Collectors.toMap(
                  t -> addKotlinPrefix("collections/" + t + ";"),
                  t -> "Ljava/util/" + t + ";")))
          .putAll(ImmutableList.of("Iterator", "Collection", "List", "Set", "Map", "ListIterator")
              .stream().collect(Collectors.toMap(
                  t -> addKotlinPrefix("collections/Mutable" + t + ";"),
                  t -> "Ljava/util/" + t + ";")))
          .put(addKotlinPrefix("collections/Iterable;"), "Ljava/lang/Iterable;")
          .put(addKotlinPrefix("collections/MutableIterable;"), "Ljava/lang/Iterable;")
          .put(addKotlinPrefix("collections/Map.Entry;"), "Ljava/util/Map$Entry;")
          .put(addKotlinPrefix("collections/MutableMap.MutableEntry;"), "Ljava/util/Map$Entry;")
          // .../FunctionN -> .../jvm/functions/FunctionN
          .putAll(
              IntStream.rangeClosed(0, 22).boxed().collect(Collectors.toMap(
                  i -> addKotlinPrefix("Function" + i + ";"),
                  i -> addKotlinPrefix("jvm/functions/Function" + i + ";"))))
          .build();

  // TODO(b/151195430): remove backward type conversions.
  private static String remapKotlinType(String type) {
    if (knownTypeConversion.containsKey(type)) {
      return knownTypeConversion.get(type);
    }
    return type;
  }

  // TODO(b/151195430): remove backward type conversions.
  // Kotlin @Metadata deserialization has plain "kotlin", which will be relocated in r8lib.
  // See b/70169921#comment57 for more details.
  // E.g., desc: (Labc/xyz/C;Lkotlin/Function1;)kotlin/Unit
  // remapped desc would be: (Labc/xyz/C;Lkotlin/jvm/functions/Function1;)V
  private static String remapKotlinTypeInDesc(String desc, Reporter reporter) {
    if (desc == null) {
      return null;
    }
    if (desc.isEmpty()) {
      return desc;
    }
    String[] parameterTypes;
    try {
      parameterTypes = DescriptorUtils.getArgumentTypeDescriptors(desc);
      for (int i = 0; i < parameterTypes.length; i++) {
        parameterTypes[i] = remapKotlinType(parameterTypes[i]);
      }
    } catch (InvalidDescriptorException e) {
      // JvmMethodSignature from @Metadata is not 100% reliable (due to its own optimization using
      // map, relocation in r8lib, etc.)
      reporter.info(
          new StringDiagnostic(
              "Invalid descriptor (deserialized from Kotlin @Metadata): " + desc));
      return desc;
    }
    int index = desc.indexOf(')');
    assert 0 < index && index < desc.length() : desc;
    String returnType = remapKotlinType(desc.substring(index + 1));
    return "(" + StringUtils.join(Arrays.asList(parameterTypes), "") + ")" + returnType;
  }

  static JvmFieldSignature toJvmFieldSignature(DexField field) {
    return new JvmFieldSignature(field.name.toString(), field.type.toDescriptorString());
  }

  static JvmMethodSignature toJvmMethodSignature(DexMethod method) {
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    for (DexType argType : method.proto.parameters.values) {
      descBuilder.append(argType.toDescriptorString());
    }
    descBuilder.append(")");
    descBuilder.append(method.proto.returnType.toDescriptorString());
    return new JvmMethodSignature(method.name.toString(), descBuilder.toString());
  }

  static class KmConstructorProcessor {
    private JvmMethodSignature signature = null;

    KmConstructorProcessor(KmConstructor kmConstructor, Reporter reporter) {
      kmConstructor.accept(new KmConstructorVisitor() {
        @Override
        public KmConstructorExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmConstructorExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmConstructorExtensionVisitor() {
            @Override
            public void visit(JvmMethodSignature desc) {
              assert signature == null : signature.asString();
              signature = desc;
            }
          };
        }
      });
      if (signature != null) {
        String remappedDesc = remapKotlinTypeInDesc(signature.getDesc(), reporter);
        if (remappedDesc != null && !remappedDesc.equals(signature.getDesc())) {
          signature = new JvmMethodSignature(signature.getName(), remappedDesc);
        }
      }
    }

    JvmMethodSignature signature() {
      return signature;
    }
  }

  static class KmFunctionProcessor {
    // Custom name via @JvmName("..."). Otherwise, null.
    private JvmMethodSignature signature = null;

    KmFunctionProcessor(KmFunction kmFunction, Reporter reporter) {
      kmFunction.accept(new KmFunctionVisitor() {
        @Override
        public KmFunctionExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmFunctionExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmFunctionExtensionVisitor() {
            @Override
            public void visit(JvmMethodSignature desc) {
              assert signature == null : signature.asString();
              signature = desc;
            }
          };
        }
      });
      if (signature != null) {
        String remappedDesc = remapKotlinTypeInDesc(signature.getDesc(), reporter);
        if (remappedDesc != null && !remappedDesc.equals(signature.getDesc())) {
          signature = new JvmMethodSignature(signature.getName(), remappedDesc);
        }
      }
    }

    JvmMethodSignature signature() {
      return signature;
    }
  }

  static class KmPropertyProcessor {
    private JvmFieldSignature fieldSignature = null;
    // Custom getter via @get:JvmName("..."). Otherwise, null.
    private JvmMethodSignature getterSignature = null;
    // Custom getter via @set:JvmName("..."). Otherwise, null.
    private JvmMethodSignature setterSignature = null;

    KmPropertyProcessor(KmProperty kmProperty, Reporter reporter) {
      kmProperty.accept(new KmPropertyVisitor() {
        @Override
        public KmPropertyExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmPropertyExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmPropertyExtensionVisitor() {
            @Override
            public void visit(
                int flags,
                JvmFieldSignature fieldDesc,
                JvmMethodSignature getterDesc,
                JvmMethodSignature setterDesc) {
              assert fieldSignature == null : fieldSignature.asString();
              fieldSignature = fieldDesc;
              assert getterSignature == null : getterSignature.asString();
              getterSignature = getterDesc;
              assert setterSignature == null : setterSignature.asString();
              setterSignature = setterDesc;
            }
          };
        }
      });
      if (fieldSignature != null) {
        String remappedDesc = remapKotlinType(fieldSignature.getDesc());
        if (remappedDesc != null && !remappedDesc.equals(fieldSignature.getDesc())) {
          fieldSignature = new JvmFieldSignature(fieldSignature.getName(), remappedDesc);
        }
      }
      if (getterSignature != null) {
        String remappedDesc = remapKotlinTypeInDesc(getterSignature.getDesc(), reporter);
        if (remappedDesc != null && !remappedDesc.equals(getterSignature.getDesc())) {
          getterSignature = new JvmMethodSignature(getterSignature.getName(), remappedDesc);
        }
      }
      if (setterSignature != null) {
        String remappedDesc = remapKotlinTypeInDesc(setterSignature.getDesc(), reporter);
        if (remappedDesc != null && !remappedDesc.equals(setterSignature.getDesc())) {
          setterSignature = new JvmMethodSignature(setterSignature.getName(), remappedDesc);
        }
      }
    }

    JvmFieldSignature fieldSignature() {
      return fieldSignature;
    }

    JvmMethodSignature getterSignature() {
      return getterSignature;
    }

    JvmMethodSignature setterSignature() {
      return setterSignature;
    }
  }
}
