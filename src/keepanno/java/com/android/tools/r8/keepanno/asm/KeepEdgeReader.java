// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.annotations.KeepConstants.Edge;
import com.android.tools.r8.keepanno.annotations.KeepConstants.Target;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern.Builder;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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

    KeepEdgeClassVisitor(Parent<KeepEdge> parent) {
      super(ASM_VERSION);
      this.parent = parent;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (!visible && descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent);
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

  private static class KeepPreconditionsVisitor extends AnnotationVisitorBase {
    private final Parent<KeepPreconditions> parent;

    public KeepPreconditionsVisitor(Parent<KeepPreconditions> parent) {
      this.parent = parent;
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

  private static class KeepTargetVisitor extends AnnotationVisitorBase {
    private final Parent<KeepTarget> parent;
    private KeepQualifiedClassNamePattern classNamePattern = null;
    private KeepMethodNamePattern methodName = null;

    public KeepTargetVisitor(Parent<KeepTarget> parent) {
      this.parent = parent;
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Target.classConstant) && value instanceof Type) {
        classNamePattern = KeepQualifiedClassNamePattern.exact(((Type) value).getClassName());
        return;
      }
      if (name.equals(Target.methodName) && value instanceof String) {
        methodName = KeepMethodNamePattern.exact((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      Builder itemBuilder = KeepItemPattern.builder();
      if (classNamePattern != null) {
        itemBuilder.setClassPattern(classNamePattern);
      }
      if (methodName != null) {
        itemBuilder.setMembersPattern(
            KeepMethodPattern.builder().setNamePattern(methodName).build());
      }
      KeepTarget target = KeepTarget.builder().setItem(itemBuilder.build()).build();
      parent.accept(target);
    }
  }
}
