// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
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

  private static boolean isValidJvmMethodSignature(String desc) {
    return desc != null
        && !desc.isEmpty()
        && desc.charAt(0) == '('
        && desc.lastIndexOf('(') == 0
        && desc.indexOf(')') != -1
        && desc.indexOf(')') == desc.lastIndexOf(')')
        && desc.lastIndexOf(')') < desc.length();
  }

  /**
   * Extract return type from {@link JvmMethodSignature}.
   *
   * Example of JVM signature is: `JvmMethodSignature("getX", "()Ljava/lang/Object;").`
   * In this case, the return type is "Ljava/lang/Object;".
   */
  static String returnTypeFromJvmMethodSignature(JvmMethodSignature signature) {
    if (signature == null) {
      return null;
    }
    String desc = signature.getDesc();
    if (!isValidJvmMethodSignature(desc)) {
      return null;
    }
    int index = desc.lastIndexOf(')');
    assert desc.charAt(0) == '(' && 0 < index && index < desc.length() : signature.asString();
    return desc.substring(index + 1);
  }

  /**
   * Extract parameters from {@link JvmMethodSignature}.
   *
   * Example of JVM signature is: `JvmMethodSignature("setX", "(Ljava/lang/Object;)V").`
   * In this case, the parameter is the list with "Ljava/lang/Object;" as the first element.
   */
  static List<String> parameterTypesFromJvmMethodSignature(JvmMethodSignature signature) {
    if (signature == null) {
      return null;
    }
    String desc = signature.getDesc();
    if (!isValidJvmMethodSignature(desc)) {
      return null;
    }
    int index = desc.lastIndexOf(')');
    assert desc.charAt(0) == '(' && 0 < index && index < desc.length() : signature.asString();
    String params = desc.substring(1, index);
    if (params.isEmpty()) {
      return ImmutableList.of();
    } else {
      return Arrays.asList(params.split(","));
    }
  }

  static class KmConstructorProcessor {
    private JvmMethodSignature signature = null;

    KmConstructorProcessor(KmConstructor kmConstructor) {
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
    }

    JvmMethodSignature signature() {
      return signature;
    }
  }

  static class KmFunctionProcessor {
    // Custom name via @JvmName("..."). Otherwise, null.
    private JvmMethodSignature signature = null;

    KmFunctionProcessor(KmFunction kmFunction) {
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

    KmPropertyProcessor(KmProperty kmProperty) {
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
