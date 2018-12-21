// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.Flag.Type.IS_NULLABLE;

import com.android.tools.r8.graph.DexClass;
import com.google.common.collect.HashBasedTable;
import java.util.BitSet;
import kotlinx.metadata.KmConstructorExtensionVisitor;
import kotlinx.metadata.KmConstructorVisitor;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmFunctionExtensionVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.KmValueParameterVisitor;
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;

class NonNullParameterHintCollector {

  static class FunctionVisitor extends KmFunctionVisitor {

    private final HashBasedTable<String, String, BitSet> paramHints;

    private BitSet paramHint = new BitSet();
    private int paramIndex = 0;
    private String name = "";
    private String descriptor = "";

    FunctionVisitor(HashBasedTable<String, String, BitSet> paramHints) {
      this.paramHints = paramHints;
    }

    @Override
    public KmTypeVisitor visitReceiverParameterType(int typeFlags) {
      if (!IS_NULLABLE.invoke(typeFlags)) {
        paramHint.set(paramIndex);
      }
      paramIndex++;
      return null;
    }

    @Override
    public KmValueParameterVisitor visitValueParameter(int paramFlags, String paramName) {
      return new KmValueParameterVisitor() {
        @Override
        public KmTypeVisitor visitType(int typeFlags) {
          if (!IS_NULLABLE.invoke(typeFlags)) {
            paramHint.set(paramIndex);
          }
          paramIndex++;
          return null;
        }
      };
    }

    @Override
    public KmFunctionExtensionVisitor visitExtensions(KmExtensionType type) {
      if (type != JvmFunctionExtensionVisitor.TYPE) {
        return null;
      }
      return new JvmFunctionExtensionVisitor() {
        @Override
        public void visit(JvmMethodSignature desc) {
          if (desc != null) {
            name = desc.getName();
            descriptor = desc.getDesc();
          }
        }
      };
    }

    @Override
    public void visitEnd() {
      if (name.isEmpty() || descriptor.isEmpty()) {
        return;
      }
      paramHints.put(name, descriptor, paramHint);
    }
  }

  static class ConstructorVisitor extends KmConstructorVisitor {
    private final HashBasedTable<String, String, BitSet> paramHints;

    private BitSet paramHint = new BitSet();
    private int paramIndex = 0;
    private final String name = "<init>";
    private String descriptor = "";

    ConstructorVisitor(HashBasedTable<String, String, BitSet> paramHints, DexClass clazz) {
      this.paramHints = paramHints;
      // Enum constructor has two synthetic arguments to java.lang.Enum's sole constructor:
      // https://docs.oracle.com/javase/8/docs/api/java/lang/Enum.html#Enum-java.lang.String-int-
      // whereas Kotlin @Metadata is still based on constructor signature, not descriptor.
      if (clazz != null && clazz.isEnum()) {
        // name - The name of this enum constant, which is the identifier used to declare it.
        paramIndex++;
        // ordinal - The ordinal of this enumeration constant (its position in the enum declaration,
        // where the initial constant is assigned an ordinal of zero).
        paramIndex++;
      }
    }

    @Override
    public KmValueParameterVisitor visitValueParameter(int paramFlags, String paramName) {
      return new KmValueParameterVisitor() {
        @Override
        public KmTypeVisitor visitType(int typeFlags) {
          if (!IS_NULLABLE.invoke(typeFlags)) {
            paramHint.set(paramIndex);
          }
          paramIndex++;
          return null;
        }
      };
    }

    @Override
    public KmConstructorExtensionVisitor visitExtensions(KmExtensionType type) {
      if (type != JvmConstructorExtensionVisitor.TYPE) {
        return null;
      }
      return new JvmConstructorExtensionVisitor() {
        @Override
        public void visit(JvmMethodSignature desc) {
          assert name.equals(desc.getName());
          descriptor = desc.getDesc();
        }
      };
    }

    @Override
    public void visitEnd() {
      if (descriptor.isEmpty()) {
        return;
      }
      paramHints.put(name, descriptor, paramHint);
    }
  }

  static class PropertyVisitor extends KmPropertyVisitor {
    private final HashBasedTable<String, String, BitSet> paramHints;

    private BitSet paramHint = new BitSet();
    private int paramIndex = 0;
    private String name = "";
    private String descriptor = "";

    PropertyVisitor(HashBasedTable<String, String, BitSet> paramHints) {
      this.paramHints = paramHints;
    }

    @Override
    public KmTypeVisitor visitReturnType(int typeFlags) {
      if (!IS_NULLABLE.invoke(typeFlags)) {
        paramHint.set(paramIndex);
      }
      paramIndex++;
      return null;
    }

    @Override
    public KmPropertyExtensionVisitor visitExtensions(KmExtensionType type) {
      if (type != JvmPropertyExtensionVisitor.TYPE) {
        return null;
      }
      return new JvmPropertyExtensionVisitor() {
        @Override
        public void visit(
            JvmFieldSignature fieldDesc,
            JvmMethodSignature getterDesc,
            JvmMethodSignature setterDesc) {
          if (setterDesc != null) {
            name = setterDesc.getName();
            descriptor = setterDesc.getDesc();
          }
        }
      };
    }

    @Override
    public void visitEnd() {
      if (name.isEmpty() || descriptor.isEmpty()) {
        return;
      }
      paramHints.put(name, descriptor, paramHint);
    }
  }
}