// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.annotations.KeepConstants;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Condition;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Item;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern.Builder;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeReader implements Opcodes {

  public static int ASM_VERSION = ASM9;

  public static Set<KeepEdge> readKeepEdges(byte[] classFileBytes) {
    ClassReader reader = new ClassReader(classFileBytes);
    Set<KeepEdge> edges = new HashSet<>();
    reader.accept(new KeepEdgeClassVisitor(edges::add), ClassReader.SKIP_CODE);
    return edges;
  }

  private static class KeepEdgeClassVisitor extends ClassVisitor {
    private final Parent<KeepEdge> parent;
    private String className;

    KeepEdgeClassVisitor(Parent<KeepEdge> parent) {
      super(ASM_VERSION);
      this.parent = parent;
    }

    private static String binaryNameToTypeName(String binaryName) {
      return binaryName.replace('/', '.');
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      this.className = binaryNameToTypeName(name);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      if (visible) {
        return null;
      }
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent);
      }
      if (descriptor.equals(KeepConstants.UsesReflection.DESCRIPTOR)) {
        KeepItemPattern classItem =
            KeepItemPattern.builder()
                .setClassPattern(KeepQualifiedClassNamePattern.exact(className))
                .build();
        return new UsesReflectionVisitor(parent, classItem);
      }
      return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new KeepEdgeMethodVisitor(parent, className, name, descriptor);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      return new KeepEdgeFieldVisitor(parent, className, name, descriptor);
    }
  }

  private static class KeepEdgeMethodVisitor extends MethodVisitor {
    private final Parent<KeepEdge> parent;
    private final String className;
    private final String methodName;
    private final String methodDescriptor;

    KeepEdgeMethodVisitor(
        Parent<KeepEdge> parent, String className, String methodName, String methodDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    private KeepItemPattern createItemContext() {
      String returnTypeDescriptor = Type.getReturnType(methodDescriptor).getDescriptor();
      Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
      KeepMethodParametersPattern.Builder builder = KeepMethodParametersPattern.builder();
      for (Type type : argumentTypes) {
        builder.addParameterTypePattern(KeepTypePattern.fromDescriptor(type.getDescriptor()));
      }
      KeepMethodReturnTypePattern returnTypePattern =
          "V".equals(returnTypeDescriptor)
              ? KeepMethodReturnTypePattern.voidType()
              : KeepMethodReturnTypePattern.fromType(
                  KeepTypePattern.fromDescriptor(returnTypeDescriptor));
      return KeepItemPattern.builder()
          .setClassPattern(KeepQualifiedClassNamePattern.exact(className))
          .setMemberPattern(
              KeepMethodPattern.builder()
                  .setNamePattern(KeepMethodNamePattern.exact(methodName))
                  .setReturnTypePattern(returnTypePattern)
                  .setParametersPattern(builder.build())
                  .build())
          .build();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent);
      }
      if (descriptor.equals(KeepConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(parent, createItemContext());
      }
      return null;
    }
  }

  private static class KeepEdgeFieldVisitor extends FieldVisitor {
    private final Parent<KeepEdge> parent;
    private final String className;
    private final String fieldName;
    private final String fieldDescriptor;

    KeepEdgeFieldVisitor(
        Parent<KeepEdge> parent, String className, String fieldName, String fieldDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.fieldName = fieldName;
      this.fieldDescriptor = fieldDescriptor;
    }

    private KeepItemPattern createItemContext() {
      // TODO(b/248408342): Default type is "any", support setting actual field type.
      return KeepItemPattern.builder()
          .setClassPattern(KeepQualifiedClassNamePattern.exact(className))
          .setMemberPattern(
              KeepFieldPattern.builder()
                  .setNamePattern(KeepFieldNamePattern.exact(fieldName))
                  .build())
          .build();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent);
      }
      if (descriptor.equals(KeepConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(parent, createItemContext());
      }
      return null;
    }
  }

  // Interface for providing AST result(s) for a sub-tree back up to its parent.
  private interface Parent<T> {
    void accept(T result);
  }

  private abstract static class AnnotationVisitorBase extends AnnotationVisitor {

    AnnotationVisitorBase() {
      super(ASM_VERSION);
    }

    @Override
    public void visit(String name, Object value) {
      throw new KeepEdgeException("Unexpected value in @KeepEdge: " + name + " = " + value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      throw new KeepEdgeException("Unexpected annotation in @KeepEdge: " + name);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      throw new KeepEdgeException("Unexpected enum in @KeepEdge: " + name);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      throw new KeepEdgeException("Unexpected array in @KeepEdge: " + name);
    }
  }

  private static class KeepEdgeVisitor extends AnnotationVisitorBase {
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();

    KeepEdgeVisitor(Parent<KeepEdge> parent) {
      this.parent = parent;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(builder::setPreconditions);
      }
      if (name.equals(Edge.consequences)) {
        return new KeepConsequencesVisitor(builder::setConsequences);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  private static class UsesReflectionVisitor extends AnnotationVisitorBase {
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();

    UsesReflectionVisitor(Parent<KeepEdge> parent, KeepItemPattern context) {
      this.parent = parent;
      preconditions.addCondition(KeepCondition.builder().setItem(context).build());
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(KeepConstants.UsesReflection.value)) {
        return new KeepConsequencesVisitor(builder::setConsequences);
      }
      if (name.equals(KeepConstants.UsesReflection.additionalPreconditions)) {
        return new KeepPreconditionsVisitor(
            additionalPreconditions -> {
              additionalPreconditions.forEach(preconditions::addCondition);
            });
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.setPreconditions(preconditions.build()).build());
    }
  }

  private static class KeepPreconditionsVisitor extends AnnotationVisitorBase {
    private final Parent<KeepPreconditions> parent;
    private final KeepPreconditions.Builder builder = KeepPreconditions.builder();

    public KeepPreconditionsVisitor(Parent<KeepPreconditions> parent) {
      this.parent = parent;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Condition.DESCRIPTOR)) {
        return new KeepConditionVisitor(builder::addCondition);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  private static class KeepConsequencesVisitor extends AnnotationVisitorBase {
    private final Parent<KeepConsequences> parent;
    private final KeepConsequences.Builder builder = KeepConsequences.builder();

    public KeepConsequencesVisitor(Parent<KeepConsequences> parent) {
      this.parent = parent;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Target.DESCRIPTOR)) {
        return new KeepTargetVisitor(builder::addTarget);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  private abstract static class KeepItemVisitorBase extends AnnotationVisitorBase {
    private final Parent<KeepItemPattern> parent;

    private KeepQualifiedClassNamePattern classNamePattern = null;
    private KeepMethodPattern.Builder lazyMethodBuilder = null;
    private KeepFieldPattern.Builder lazyFieldBuilder = null;

    public KeepItemVisitorBase(Parent<KeepItemPattern> parent) {
      this.parent = parent;
    }

    private KeepMethodPattern.Builder methodBuilder() {
      if (lazyFieldBuilder != null) {
        throw new KeepEdgeException("Cannot define both a field and a method pattern");
      }
      if (lazyMethodBuilder == null) {
        lazyMethodBuilder = KeepMethodPattern.builder();
      }
      return lazyMethodBuilder;
    }

    private KeepFieldPattern.Builder fieldBuilder() {
      if (lazyMethodBuilder != null) {
        throw new KeepEdgeException("Cannot define both a field and a method pattern");
      }
      if (lazyFieldBuilder == null) {
        lazyFieldBuilder = KeepFieldPattern.builder();
      }
      return lazyFieldBuilder;
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Item.classConstant) && value instanceof Type) {
        classNamePattern = KeepQualifiedClassNamePattern.exact(((Type) value).getClassName());
        return;
      }
      if (name.equals(Item.methodName) && value instanceof String) {
        String methodName = (String) value;
        if (!Item.methodNameDefaultValue.equals(methodName)) {
          methodBuilder().setNamePattern(KeepMethodNamePattern.exact(methodName));
        }
        return;
      }
      if (name.equals(Item.methodReturnType) && value instanceof String) {
        String returnType = (String) value;
        if (!Item.methodReturnTypeDefaultValue.equals(returnType)) {
          methodBuilder()
              .setReturnTypePattern(KeepEdgeReaderUtils.methodReturnTypeFromString(returnType));
        }
        return;
      }
      if (name.equals(Item.fieldName) && value instanceof String) {
        String fieldName = (String) value;
        if (!Item.fieldNameDefaultValue.equals(fieldName)) {
          fieldBuilder().setNamePattern(KeepFieldNamePattern.exact(fieldName));
        }
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(Item.methodParameters)) {
        return new StringArrayVisitor(
            params -> {
              if (Arrays.asList(Item.methodParametersDefaultValue).equals(params)) {
                return;
              }
              KeepMethodParametersPattern.Builder builder = KeepMethodParametersPattern.builder();
              for (String param : params) {
                builder.addParameterTypePattern(KeepEdgeReaderUtils.typePatternFromString(param));
              }
              methodBuilder().setParametersPattern(builder.build());
            });
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      assert lazyMethodBuilder == null || lazyFieldBuilder == null;
      Builder itemBuilder = KeepItemPattern.builder();
      if (classNamePattern != null) {
        itemBuilder.setClassPattern(classNamePattern);
      }
      if (lazyMethodBuilder != null) {
        itemBuilder.setMemberPattern(lazyMethodBuilder.build());
      }
      if (lazyFieldBuilder != null) {
        itemBuilder.setMemberPattern(lazyFieldBuilder.build());
      }
      parent.accept(itemBuilder.build());
    }
  }

  private static class StringArrayVisitor extends AnnotationVisitorBase {

    private final Consumer<List<String>> fn;
    private final List<String> strings = new ArrayList<>();

    public StringArrayVisitor(Consumer<List<String>> fn) {
      this.fn = fn;
    }

    @Override
    public void visit(String name, Object value) {
      if (value instanceof String) {
        strings.add((String) value);
      } else {
        super.visit(name, value);
      }
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      fn.accept(strings);
    }
  }

  private static class KeepTargetVisitor extends KeepItemVisitorBase {

    public KeepTargetVisitor(Parent<KeepTarget> parent) {
      super(item -> parent.accept(KeepTarget.builder().setItem(item).build()));
    }
  }

  private static class KeepConditionVisitor extends KeepItemVisitorBase {

    public KeepConditionVisitor(Parent<KeepCondition> parent) {
      super(item -> parent.accept(KeepCondition.builder().setItem(item).build()));
    }
  }
}
