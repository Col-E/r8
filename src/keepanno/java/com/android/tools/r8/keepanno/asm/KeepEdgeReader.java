// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.AccessVisibility;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Binding;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Condition;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Edge;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.FieldAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ForApi;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Item;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Kind;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MemberAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MethodAccess;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Option;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Target;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsedByReflection;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.UsesReflection;
import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemReference;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldTypePattern;
import com.android.tools.r8.keepanno.ast.KeepInstanceOfPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepMemberAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class KeepEdgeReader implements Opcodes {

  public static int ASM_VERSION = ASM9;

  public static List<KeepDeclaration> readKeepEdges(byte[] classFileBytes) {
    ClassReader reader = new ClassReader(classFileBytes);
    List<KeepDeclaration> declarations = new ArrayList<>();
    reader.accept(new KeepEdgeClassVisitor(declarations::add), ClassReader.SKIP_CODE);
    return declarations;
  }

  private static KeepClassItemReference classReferenceFromName(String className) {
    return KeepClassItemReference.fromClassNamePattern(
        KeepQualifiedClassNamePattern.exact(className));
  }

  private static class KeepEdgeClassVisitor extends ClassVisitor {
    private final Parent<KeepDeclaration> parent;
    private String className;

    KeepEdgeClassVisitor(Parent<KeepDeclaration> parent) {
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
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent::accept, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        KeepClassItemPattern classItem =
            KeepClassItemPattern.builder()
                .setClassNamePattern(KeepQualifiedClassNamePattern.exact(className))
                .build();
        return new UsesReflectionVisitor(parent::accept, this::setContext, classItem);
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiClassVisitor(parent::accept, this::setContext, className);
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionClassVisitor(
            descriptor, parent::accept, this::setContext, className);
      }
      if (descriptor.equals(AnnotationConstants.CheckRemoved.DESCRIPTOR)) {
        return new CheckRemovedClassVisitor(
            descriptor, parent::accept, this::setContext, className, KeepCheckKind.REMOVED);
      }
      if (descriptor.equals(AnnotationConstants.CheckOptimizedOut.DESCRIPTOR)) {
        return new CheckRemovedClassVisitor(
            descriptor, parent::accept, this::setContext, className, KeepCheckKind.OPTIMIZED_OUT);
      }
      return null;
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromClassDescriptor(KeepEdgeReaderUtils.javaTypeToDescriptor(className));
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new KeepEdgeMethodVisitor(parent::accept, className, name, descriptor);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      return new KeepEdgeFieldVisitor(parent::accept, className, name, descriptor);
    }
  }

  private static class KeepEdgeMethodVisitor extends MethodVisitor {
    private final Parent<KeepDeclaration> parent;
    private final String className;
    private final String methodName;
    private final String methodDescriptor;

    KeepEdgeMethodVisitor(
        Parent<KeepDeclaration> parent,
        String className,
        String methodName,
        String methodDescriptor) {
      super(ASM_VERSION);
      this.parent = parent;
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    private KeepMemberItemPattern createMethodItemContext() {
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
      return KeepMemberItemPattern.builder()
          .setClassReference(classReferenceFromName(className))
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
        return new KeepEdgeVisitor(parent::accept, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(
            parent::accept, this::setContext, createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiMemberVisitor(parent::accept, this::setContext, createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionMemberVisitor(
            descriptor, parent::accept, this::setContext, createMethodItemContext());
      }
      if (descriptor.equals(AnnotationConstants.CheckRemoved.DESCRIPTOR)) {
        return new CheckRemovedMemberVisitor(
            descriptor,
            parent::accept,
            this::setContext,
            createMethodItemContext(),
            KeepCheckKind.REMOVED);
      }
      if (descriptor.equals(AnnotationConstants.CheckOptimizedOut.DESCRIPTOR)) {
        return new CheckRemovedMemberVisitor(
            descriptor,
            parent::accept,
            this::setContext,
            createMethodItemContext(),
            KeepCheckKind.OPTIMIZED_OUT);
      }
      return null;
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromMethodDescriptor(
          KeepEdgeReaderUtils.javaTypeToDescriptor(className), methodName, methodDescriptor);
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

    private KeepMemberItemPattern createMemberItemContext() {
      KeepFieldTypePattern typePattern =
          KeepFieldTypePattern.fromType(KeepTypePattern.fromDescriptor(fieldDescriptor));
      return KeepMemberItemPattern.builder()
          .setClassReference(classReferenceFromName(className))
          .setMemberPattern(
              KeepFieldPattern.builder()
                  .setNamePattern(KeepFieldNamePattern.exact(fieldName))
                  .setTypePattern(typePattern)
                  .build())
          .build();
    }

    private void setContext(KeepEdgeMetaInfo.Builder builder) {
      builder.setContextFromFieldDescriptor(
          KeepEdgeReaderUtils.javaTypeToDescriptor(className), fieldName, fieldDescriptor);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Skip any visible annotations as @KeepEdge is not runtime visible.
      if (visible) {
        return null;
      }
      if (descriptor.equals(Edge.DESCRIPTOR)) {
        return new KeepEdgeVisitor(parent, this::setContext);
      }
      if (descriptor.equals(AnnotationConstants.UsesReflection.DESCRIPTOR)) {
        return new UsesReflectionVisitor(parent, this::setContext, createMemberItemContext());
      }
      if (descriptor.equals(AnnotationConstants.ForApi.DESCRIPTOR)) {
        return new ForApiMemberVisitor(parent, this::setContext, createMemberItemContext());
      }
      if (descriptor.equals(AnnotationConstants.UsedByReflection.DESCRIPTOR)
          || descriptor.equals(AnnotationConstants.UsedByNative.DESCRIPTOR)) {
        return new UsedByReflectionMemberVisitor(
            descriptor, parent, this::setContext, createMemberItemContext());
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

    public abstract String getAnnotationName();

    private String errorMessagePrefix() {
      return " @" + getAnnotationName() + ": ";
    }

    @Override
    public void visit(String name, Object value) {
      throw new KeepEdgeException(
          "Unexpected value in" + errorMessagePrefix() + name + " = " + value);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      throw new KeepEdgeException("Unexpected annotation in" + errorMessagePrefix() + name);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      throw new KeepEdgeException("Unexpected enum in" + errorMessagePrefix() + name);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      throw new KeepEdgeException("Unexpected array in" + errorMessagePrefix() + name);
    }
  }

  private static class UserBindingsHelper {
    private final KeepBindings.Builder builder = KeepBindings.builder();
    private final Map<String, KeepBindingSymbol> userNames = new HashMap<>();

    public KeepBindingSymbol resolveUserBinding(String name) {
      return userNames.computeIfAbsent(name, builder::create);
    }

    public void defineUserBinding(String name, KeepItemPattern item) {
      builder.addBinding(resolveUserBinding(name), item);
    }

    public KeepBindingSymbol defineFreshBinding(String name, KeepItemPattern item) {
      KeepBindingSymbol symbol = builder.generateFreshSymbol(name);
      builder.addBinding(symbol, item);
      return symbol;
    }

    public KeepBindings build() {
      return builder.build();
    }
  }

  private static class KeepEdgeVisitor extends AnnotationVisitorBase {
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    KeepEdgeVisitor(Parent<KeepEdge> parent, Consumer<KeepEdgeMetaInfo.Builder> addContext) {
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public String getAnnotationName() {
      return "KeepEdge";
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(Edge.bindings)) {
        return new KeepBindingsVisitor(getAnnotationName(), bindingsHelper);
      }
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            getAnnotationName(), builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(Edge.consequences)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(), builder::setConsequences, bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder.setMetaInfo(metaInfoBuilder.build()).setBindings(bindingsHelper.build()).build());
    }
  }

  /**
   * Parsing of @KeepForApi on a class context.
   *
   * <p>When used on a class context the annotation allows the member related content of a normal
   * item. This parser extends the base item visitor and throws an error if any class specific
   * properties are encountered.
   */
  private static class ForApiClassVisitor extends KeepItemVisitorBase {
    private final String className;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    ForApiClassVisitor(
        Parent<KeepEdge> parent, Consumer<KeepEdgeMetaInfo.Builder> addContext, String className) {
      this.className = className;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      // The class context/holder is the annotated class.
      visit(Item.className, className);
      // The default kind is to target the class and its members.
      visitEnum(null, Kind.DESCRIPTOR, Kind.CLASS_AND_MEMBERS);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      return ForApi.CLASS.getSimpleName();
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(ForApi.additionalTargets)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (!getKind().equals(Kind.ONLY_CLASS) && isDefaultMemberDeclaration()) {
        // If no member declarations have been made, set public & protected as the default.
        AnnotationVisitor v = visitArray(Item.memberAccess);
        v.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PUBLIC);
        v.visitEnum(null, MemberAccess.DESCRIPTOR, MemberAccess.PROTECTED);
      }
      super.visitEnd();
      Collection<KeepItemReference> items = getItemsWithoutBinding();
      for (KeepItemReference item : items) {
        if (item.isBindingReference()) {
          throw new KeepEdgeException("@KeepForApi cannot reference bindings");
        }
        KeepClassItemPattern classItemPattern = item.asClassItemPattern();
        if (classItemPattern == null) {
          assert item.isMemberItemReference();
          classItemPattern = item.asMemberItemPattern().getClassReference().asClassItemPattern();
        }
        String descriptor = AnnotationConstants.getDescriptorFromClassTypeName(className);
        String itemDescriptor = classItemPattern.getClassNamePattern().getExactDescriptor();
        if (!descriptor.equals(itemDescriptor)) {
          throw new KeepEdgeException("@KeepForApi must reference its class context " + className);
        }
        if (classItemPattern.isMemberItemPattern() && items.size() == 1) {
            throw new KeepEdgeException("@KeepForApi kind must include its class");
        }
        if (!classItemPattern.getInstanceOfPattern().isAny()) {
          throw new KeepEdgeException("@KeepForApi cannot define an 'extends' pattern.");
        }
        consequences.addTarget(KeepTarget.builder().setItemReference(item).build());
      }
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  /**
   * Parsing of @KeepForApi on a member context.
   *
   * <p>When used on a member context the annotation does not allow member related patterns.
   */
  private static class ForApiMemberVisitor extends AnnotationVisitorBase {
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();

    ForApiMemberVisitor(
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepMemberItemPattern context) {
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      // Create a binding for the context such that the class and member are shared.
      KeepClassItemPattern classContext = context.getClassReference().asClassItemPattern();
      KeepBindingSymbol bindingSymbol = bindingsHelper.defineFreshBinding("CONTEXT", classContext);
      KeepClassItemReference classReference =
          KeepBindingReference.forClass(bindingSymbol).toClassItemReference();
      consequences.addTarget(
          KeepTarget.builder()
              .setItemPattern(
                  KeepMemberItemPattern.builder()
                      .copyFrom(context)
                      .setClassReference(classReference)
                      .build())
              .build());
      consequences.addTarget(KeepTarget.builder().setItemReference(classReference).build());
    }

    @Override
    public String getAnnotationName() {
      return ForApi.CLASS.getSimpleName();
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(ForApi.additionalTargets)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  /**
   * Parsing of @UsedByReflection or @UsedByNative on a class context.
   *
   * <p>When used on a class context the annotation allows the member related content of a normal
   * item. This parser extends the base item visitor and throws an error if any class specific
   * properties are encountered.
   */
  private static class UsedByReflectionClassVisitor extends KeepItemVisitorBase {
    private final String annotationDescriptor;
    private final String className;
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    UsedByReflectionClassVisitor(
        String annotationDescriptor,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className) {
      this.annotationDescriptor = annotationDescriptor;
      this.className = className;
      this.parent = parent;
      addContext.accept(metaInfoBuilder);
      // The class context/holder is the annotated class.
      visit(Item.className, className);
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      int sep = annotationDescriptor.lastIndexOf('/');
      return annotationDescriptor.substring(sep + 1, annotationDescriptor.length() - 1);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            getAnnotationName(), builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (getKind() == null && !isDefaultMemberDeclaration()) {
        // If no explict kind is set and member declarations have been made, keep the class too.
        visitEnum(null, Kind.DESCRIPTOR, Kind.CLASS_AND_MEMBERS);
      }
      super.visitEnd();
      Collection<KeepItemReference> items = getItemsWithoutBinding();
      for (KeepItemReference item : items) {
        if (item.isBindingReference()) {
          // TODO(b/248408342): The edge can have preconditions so it should support bindings!
          throw new KeepEdgeException("@" + getAnnotationName() + " cannot reference bindings");
        }
        KeepItemPattern itemPattern = item.asItemPattern();
        KeepClassItemPattern holderPattern =
            itemPattern.isClassItemPattern()
                ? itemPattern.asClassItemPattern()
                : itemPattern.asMemberItemPattern().getClassReference().asClassItemPattern();
        String descriptor = AnnotationConstants.getDescriptorFromClassTypeName(className);
        String itemDescriptor = holderPattern.getClassNamePattern().getExactDescriptor();
        if (!descriptor.equals(itemDescriptor)) {
          throw new KeepEdgeException(
              "@" + getAnnotationName() + " must reference its class context " + className);
        }
        if (itemPattern.isMemberItemPattern() && items.size() == 1) {
          throw new KeepEdgeException("@" + getAnnotationName() + " kind must include its class");
        }
        if (!holderPattern.getInstanceOfPattern().isAny()) {
          throw new KeepEdgeException(
              "@" + getAnnotationName() + " cannot define an 'extends' pattern.");
        }
        consequences.addTarget(KeepTarget.builder().setItemPattern(itemPattern).build());
      }
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  /**
   * Parsing of @UsedByReflection or @UsedByNative on a member context.
   *
   * <p>When used on a member context the annotation does not allow member related patterns.
   */
  private static class UsedByReflectionMemberVisitor extends AnnotationVisitorBase {
    private final String annotationDescriptor;
    private final Parent<KeepEdge> parent;
    private final KeepItemPattern context;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();
    private final KeepConsequences.Builder consequences = KeepConsequences.builder();
    private String kind = Kind.ONLY_MEMBERS;

    UsedByReflectionMemberVisitor(
        String annotationDescriptor,
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepItemPattern context) {
      this.annotationDescriptor = annotationDescriptor;
      this.parent = parent;
      this.context = context;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public String getAnnotationName() {
      int sep = annotationDescriptor.lastIndexOf('/');
      return annotationDescriptor.substring(sep + 1, annotationDescriptor.length() - 1);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.Kind.DESCRIPTOR)) {
        super.visitEnum(name, descriptor, value);
      }
      switch (value) {
        case Kind.ONLY_CLASS:
        case Kind.ONLY_MEMBERS:
        case Kind.CLASS_AND_MEMBERS:
          kind = value;
          break;
        default:
          super.visitEnum(name, descriptor, value);
      }
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(Edge.preconditions)) {
        return new KeepPreconditionsVisitor(
            getAnnotationName(), builder::setPreconditions, bindingsHelper);
      }
      if (name.equals(UsedByReflection.additionalTargets)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(),
            additionalConsequences -> {
              additionalConsequences.forEachTarget(consequences::addTarget);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (Kind.ONLY_CLASS.equals(kind)) {
        throw new KeepEdgeException("@" + getAnnotationName() + " kind must include its member");
      }
      assert context.isMemberItemPattern();
      KeepMemberItemPattern memberContext = context.asMemberItemPattern();
      if (Kind.CLASS_AND_MEMBERS.equals(kind)) {
        consequences.addTarget(
            KeepTarget.builder().setItemReference(memberContext.getClassReference()).build());
      }
      consequences.addTarget(KeepTarget.builder().setItemPattern(context).build());
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setConsequences(consequences.build())
              .build());
    }
  }

  private static class UsesReflectionVisitor extends AnnotationVisitorBase {
    private final Parent<KeepEdge> parent;
    private final KeepEdge.Builder builder = KeepEdge.builder();
    private final KeepPreconditions.Builder preconditions = KeepPreconditions.builder();
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final UserBindingsHelper bindingsHelper = new UserBindingsHelper();

    UsesReflectionVisitor(
        Parent<KeepEdge> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepItemPattern context) {
      this.parent = parent;
      preconditions.addCondition(KeepCondition.builder().setItemPattern(context).build());
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public String getAnnotationName() {
      return UsesReflection.CLASS.getSimpleName();
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      if (name.equals(AnnotationConstants.UsesReflection.value)) {
        return new KeepConsequencesVisitor(
            getAnnotationName(), builder::setConsequences, bindingsHelper);
      }
      if (name.equals(AnnotationConstants.UsesReflection.additionalPreconditions)) {
        return new KeepPreconditionsVisitor(
            getAnnotationName(),
            additionalPreconditions -> {
              additionalPreconditions.forEach(preconditions::addCondition);
            },
            bindingsHelper);
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      parent.accept(
          builder
              .setMetaInfo(metaInfoBuilder.build())
              .setBindings(bindingsHelper.build())
              .setPreconditions(preconditions.build())
              .build());
    }
  }

  private static class KeepBindingsVisitor extends AnnotationVisitorBase {
    private final String annotationName;
    private final UserBindingsHelper helper;

    public KeepBindingsVisitor(String annotationName, UserBindingsHelper helper) {
      this.annotationName = annotationName;
      this.helper = helper;
    }

    @Override
    public String getAnnotationName() {
      return annotationName;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(AnnotationConstants.Binding.DESCRIPTOR)) {
        return new KeepBindingVisitor(helper);
      }
      return super.visitAnnotation(name, descriptor);
    }
  }

  private static class KeepPreconditionsVisitor extends AnnotationVisitorBase {
    private final String annotationName;
    private final Parent<KeepPreconditions> parent;
    private final KeepPreconditions.Builder builder = KeepPreconditions.builder();
    private final UserBindingsHelper bindingsHelper;

    public KeepPreconditionsVisitor(
        String annotationName,
        Parent<KeepPreconditions> parent,
        UserBindingsHelper bindingsHelper) {
      this.annotationName = annotationName;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      return annotationName;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Condition.DESCRIPTOR)) {
        return new KeepConditionVisitor(builder::addCondition, bindingsHelper);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  private static class KeepConsequencesVisitor extends AnnotationVisitorBase {
    private final String annotationName;
    private final Parent<KeepConsequences> parent;
    private final KeepConsequences.Builder builder = KeepConsequences.builder();
    private final UserBindingsHelper bindingsHelper;

    public KeepConsequencesVisitor(
        String annotationName, Parent<KeepConsequences> parent, UserBindingsHelper bindingsHelper) {
      this.annotationName = annotationName;
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      return annotationName;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
      if (descriptor.equals(Target.DESCRIPTOR)) {
        return KeepTargetVisitor.create(builder::addTarget, bindingsHelper);
      }
      return super.visitAnnotation(name, descriptor);
    }

    @Override
    public void visitEnd() {
      parent.accept(builder.build());
    }
  }

  /** Parsing of @CheckRemoved and @CheckOptimizedOut on a class context. */
  private static class CheckRemovedClassVisitor extends AnnotationVisitorBase {

    private final String annotationDescriptor;
    private final Parent<KeepCheck> parent;
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final String className;
    private final KeepCheckKind kind;

    public CheckRemovedClassVisitor(
        String annotationDescriptor,
        Parent<KeepCheck> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        String className,
        KeepCheckKind kind) {
      this.annotationDescriptor = annotationDescriptor;
      this.parent = parent;
      this.className = className;
      this.kind = kind;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public String getAnnotationName() {
      int sep = annotationDescriptor.lastIndexOf('/');
      return annotationDescriptor.substring(sep + 1, annotationDescriptor.length() - 1);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      CheckRemovedClassVisitor superVisitor = this;
      KeepItemVisitorBase itemVisitor =
          new KeepItemVisitorBase() {
            @Override
            public UserBindingsHelper getBindingsHelper() {
              throw new KeepEdgeException("Bindings not supported in @" + getAnnotationName());
            }

            @Override
            public String getAnnotationName() {
              return superVisitor.getAnnotationName();
            }
          };
      itemVisitor.visit(Item.className, className);
      itemVisitor.visitEnd();
      parent.accept(
          KeepCheck.builder()
              .setMetaInfo(metaInfoBuilder.build())
              .setKind(kind)
              .setItemPattern(itemVisitor.getItemReference().asItemPattern())
              .build());
    }
  }

  /** Parsing of @CheckRemoved and @CheckOptimizedOut on a class context. */
  private static class CheckRemovedMemberVisitor extends AnnotationVisitorBase {

    private final String annotationDescriptor;
    private final Parent<KeepDeclaration> parent;
    private final KeepItemPattern context;
    private final KeepEdgeMetaInfo.Builder metaInfoBuilder = KeepEdgeMetaInfo.builder();
    private final KeepCheckKind kind;

    CheckRemovedMemberVisitor(
        String annotationDescriptor,
        Parent<KeepDeclaration> parent,
        Consumer<KeepEdgeMetaInfo.Builder> addContext,
        KeepItemPattern context,
        KeepCheckKind kind) {
      this.annotationDescriptor = annotationDescriptor;
      this.parent = parent;
      this.context = context;
      this.kind = kind;
      addContext.accept(metaInfoBuilder);
    }

    @Override
    public String getAnnotationName() {
      int sep = annotationDescriptor.lastIndexOf('/');
      return annotationDescriptor.substring(sep + 1, annotationDescriptor.length() - 1);
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Edge.description) && value instanceof String) {
        metaInfoBuilder.setDescription((String) value);
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      parent.accept(
          KeepCheck.builder()
              .setMetaInfo(metaInfoBuilder.build())
              .setKind(kind)
              .setItemPattern(context)
              .build());
    }
  }

  abstract static class Declaration<T> {
    abstract String kind();

    abstract boolean isDefault();

    abstract T getValue();

    boolean tryParse(String name, Object value) {
      return false;
    }

    AnnotationVisitor tryParseArray(String name, Consumer<T> onValue) {
      return null;
    }
  }

  private abstract static class SingleDeclaration<T> extends Declaration<T> {
    private String declarationName = null;
    private T declarationValue = null;
    private AnnotationVisitor declarationVisitor = null;

    abstract T getDefaultValue();

    abstract T parse(String name, Object value);

    AnnotationVisitor parseArray(String name, Consumer<T> setValue) {
      return null;
    }

    @Override
    boolean isDefault() {
      return !hasDeclaration();
    }

    private boolean hasDeclaration() {
      return declarationValue != null || declarationVisitor != null;
    }

    private void error(String name) {
      throw new KeepEdgeException(
          "Multiple declarations defining "
              + kind()
              + ": '"
              + declarationName
              + "' and '"
              + name
              + "'");
    }

    @Override
    public final T getValue() {
      return declarationValue == null ? getDefaultValue() : declarationValue;
    }

    @Override
    final boolean tryParse(String name, Object value) {
      T result = parse(name, value);
      if (result != null) {
        if (hasDeclaration()) {
          error(name);
        }
        declarationName = name;
        declarationValue = result;
        return true;
      }
      return false;
    }

    @Override
    AnnotationVisitor tryParseArray(String name, Consumer<T> setValue) {
      AnnotationVisitor visitor = parseArray(name, setValue.andThen(v -> declarationValue = v));
      if (visitor != null) {
        if (hasDeclaration()) {
          error(name);
        }
        declarationName = name;
        declarationVisitor = visitor;
        return visitor;
      }
      return null;
    }
  }

  private static class ClassNameDeclaration
      extends SingleDeclaration<KeepQualifiedClassNamePattern> {

    @Override
    String kind() {
      return "class-name";
    }

    @Override
    KeepQualifiedClassNamePattern getDefaultValue() {
      return KeepQualifiedClassNamePattern.any();
    }

    @Override
    KeepQualifiedClassNamePattern parse(String name, Object value) {
      if (name.equals(Item.classConstant) && value instanceof Type) {
        return KeepQualifiedClassNamePattern.exact(((Type) value).getClassName());
      }
      if (name.equals(Item.className) && value instanceof String) {
        return KeepQualifiedClassNamePattern.exact(((String) value));
      }
      return null;
    }
  }

  private static class InstanceOfDeclaration extends SingleDeclaration<KeepInstanceOfPattern> {

    @Override
    String kind() {
      return "instance-of";
    }

    @Override
    KeepInstanceOfPattern getDefaultValue() {
      return KeepInstanceOfPattern.any();
    }

    @Override
    KeepInstanceOfPattern parse(String name, Object value) {
      if (name.equals(Item.instanceOfClassConstant) && value instanceof Type) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((Type) value).getClassName()))
            .build();
      }
      if (name.equals(Item.instanceOfClassName) && value instanceof String) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((String) value)))
            .build();
      }
      if (name.equals(Item.instanceOfClassConstantExclusive) && value instanceof Type) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((Type) value).getClassName()))
            .setInclusive(false)
            .build();
      }
      if (name.equals(Item.instanceOfClassNameExclusive) && value instanceof String) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((String) value)))
            .setInclusive(false)
            .build();
      }
      if (name.equals(Item.extendsClassConstant) && value instanceof Type) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((Type) value).getClassName()))
            .setInclusive(false)
            .build();
      }
      if (name.equals(Item.extendsClassName) && value instanceof String) {
        return KeepInstanceOfPattern.builder()
            .classPattern(KeepQualifiedClassNamePattern.exact(((String) value)))
            .setInclusive(false)
            .build();
      }
      return null;
    }
  }

  private static class ClassDeclaration extends Declaration<KeepClassItemReference> {

    private final Supplier<UserBindingsHelper> getBindingsHelper;

    private KeepClassItemReference boundClassItemReference = null;
    private final ClassNameDeclaration classNameDeclaration = new ClassNameDeclaration();
    private final InstanceOfDeclaration instanceOfDeclaration = new InstanceOfDeclaration();

    public ClassDeclaration(Supplier<UserBindingsHelper> getBindingsHelper) {
      this.getBindingsHelper = getBindingsHelper;
    }

    private boolean isBindingReferenceDefined() {
      return boundClassItemReference != null;
    }

    private boolean classPatternsAreDefined() {
      return !classNameDeclaration.isDefault() || !instanceOfDeclaration.isDefault();
    }

    private void checkAllowedDefinitions() {
      if (isBindingReferenceDefined() && classPatternsAreDefined()) {
        throw new KeepEdgeException(
            "Cannot reference a class binding and class patterns for a single class item");
      }
    }

    @Override
    String kind() {
      return "class";
    }

    @Override
    boolean isDefault() {
      return !isBindingReferenceDefined() && !classPatternsAreDefined();
    }

    @Override
    KeepClassItemReference getValue() {
      if (isBindingReferenceDefined()) {
        return boundClassItemReference;
      }
      if (classPatternsAreDefined()) {
        return KeepClassItemPattern.builder()
            .setClassNamePattern(classNameDeclaration.getValue())
            .setInstanceOfPattern(instanceOfDeclaration.getValue())
            .build()
            .toClassItemReference();
      }
      assert isDefault();
      return KeepClassItemPattern.any().toClassItemReference();
    }

    public void setBindingReference(KeepClassItemReference bindingReference) {
      if (isBindingReferenceDefined()) {
        throw new KeepEdgeException(
            "Cannot reference multiple class bindings for a single class item");
      }
      this.boundClassItemReference = bindingReference;
    }

    @Override
    boolean tryParse(String name, Object value) {
      if (name.equals(Item.classFromBinding) && value instanceof String) {
        KeepBindingSymbol symbol = getBindingsHelper.get().resolveUserBinding((String) value);
        setBindingReference(KeepBindingReference.forClass(symbol).toClassItemReference());
        return true;
      }
      if (classNameDeclaration.tryParse(name, value)) {
        checkAllowedDefinitions();
        return true;
      }
      if (instanceOfDeclaration.tryParse(name, value)) {
        checkAllowedDefinitions();
        return true;
      }
      return false;
    }
  }

  private static class MethodDeclaration extends Declaration<KeepMethodPattern> {
    private final Supplier<String> annotationName;
    private KeepMethodAccessPattern.Builder accessBuilder = null;
    private KeepMethodPattern.Builder builder = null;

    private MethodDeclaration(Supplier<String> annotationName) {
      this.annotationName = annotationName;
    }

    private KeepMethodPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepMethodPattern.builder();
      }
      return builder;
    }

    @Override
    String kind() {
      return "method";
    }

    @Override
    boolean isDefault() {
      return accessBuilder == null && builder == null;
    }

    @Override
    KeepMethodPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    boolean tryParse(String name, Object value) {
      if (name.equals(Item.methodName) && value instanceof String) {
        getBuilder().setNamePattern(KeepMethodNamePattern.exact((String) value));
        return true;
      }
      if (name.equals(Item.methodReturnType) && value instanceof String) {
        getBuilder()
            .setReturnTypePattern(KeepEdgeReaderUtils.methodReturnTypeFromString((String) value));
        return true;
      }
      return false;
    }

    @Override
    AnnotationVisitor tryParseArray(String name, Consumer<KeepMethodPattern> ignored) {
      if (name.equals(Item.methodAccess)) {
        accessBuilder = KeepMethodAccessPattern.builder();
        return new MethodAccessVisitor(annotationName, accessBuilder);
      }
      if (name.equals(Item.methodParameters)) {
        return new StringArrayVisitor(
            annotationName,
            params -> {
              KeepMethodParametersPattern.Builder builder = KeepMethodParametersPattern.builder();
              for (String param : params) {
                builder.addParameterTypePattern(KeepEdgeReaderUtils.typePatternFromString(param));
              }
              KeepMethodParametersPattern result = builder.build();
              getBuilder().setParametersPattern(result);
            });
      }
      return null;
    }
  }

  private static class FieldDeclaration extends Declaration<KeepFieldPattern> {
    private final Supplier<String> annotationName;
    private KeepFieldAccessPattern.Builder accessBuilder = null;
    private KeepFieldPattern.Builder builder = null;

    public FieldDeclaration(Supplier<String> annotationName) {
      this.annotationName = annotationName;
    }

    private KeepFieldPattern.Builder getBuilder() {
      if (builder == null) {
        builder = KeepFieldPattern.builder();
      }
      return builder;
    }

    @Override
    String kind() {
      return "field";
    }

    @Override
    boolean isDefault() {
      return accessBuilder == null && builder == null;
    }

    @Override
    KeepFieldPattern getValue() {
      if (accessBuilder != null) {
        getBuilder().setAccessPattern(accessBuilder.build());
      }
      return builder != null ? builder.build() : null;
    }

    @Override
    boolean tryParse(String name, Object value) {
      if (name.equals(Item.fieldName) && value instanceof String) {
        getBuilder().setNamePattern(KeepFieldNamePattern.exact((String) value));
        return true;
      }
      if (name.equals(Item.fieldType) && value instanceof String) {
        getBuilder()
            .setTypePattern(
                KeepFieldTypePattern.fromType(
                    KeepEdgeReaderUtils.typePatternFromString((String) value)));
        return true;
      }
      return false;
    }

    @Override
    AnnotationVisitor tryParseArray(String name, Consumer<KeepFieldPattern> onValue) {
      if (name.equals(Item.fieldAccess)) {
        accessBuilder = KeepFieldAccessPattern.builder();
        return new FieldAccessVisitor(annotationName, accessBuilder);
      }
      return super.tryParseArray(name, onValue);
    }
  }

  private static class MemberDeclaration extends Declaration<KeepMemberPattern> {
    private final Supplier<String> annotationName;
    private KeepMemberAccessPattern.Builder accessBuilder = null;
    private final MethodDeclaration methodDeclaration;
    private final FieldDeclaration fieldDeclaration;

    MemberDeclaration(Supplier<String> annotationName) {
      this.annotationName = annotationName;
      methodDeclaration = new MethodDeclaration(annotationName);
      fieldDeclaration = new FieldDeclaration(annotationName);
    }

    @Override
    String kind() {
      return "member";
    }

    @Override
    public boolean isDefault() {
      return accessBuilder == null && methodDeclaration.isDefault() && fieldDeclaration.isDefault();
    }

    @Override
    public KeepMemberPattern getValue() {
      KeepMethodPattern method = methodDeclaration.getValue();
      KeepFieldPattern field = fieldDeclaration.getValue();
      if (accessBuilder != null) {
        if (method != null || field != null) {
          throw new KeepEdgeException(
              "Cannot define common member access as well as field or method pattern");
        }
        return KeepMemberPattern.memberBuilder().setAccessPattern(accessBuilder.build()).build();
      }
      if (method != null && field != null) {
        throw new KeepEdgeException("Cannot define both a field and a method pattern");
      }
      if (method != null) {
        return method;
      }
      if (field != null) {
        return field;
      }
      return KeepMemberPattern.none();
    }

    @Override
    boolean tryParse(String name, Object value) {
      return methodDeclaration.tryParse(name, value) || fieldDeclaration.tryParse(name, value);
    }

    @Override
    AnnotationVisitor tryParseArray(String name, Consumer<KeepMemberPattern> ignored) {
      if (name.equals(Item.memberAccess)) {
        accessBuilder = KeepMemberAccessPattern.memberBuilder();
        return new MemberAccessVisitor(annotationName, accessBuilder);
      }
      AnnotationVisitor visitor = methodDeclaration.tryParseArray(name, v -> {});
      if (visitor != null) {
        return visitor;
      }
      return fieldDeclaration.tryParseArray(name, v -> {});
    }
  }

  private abstract static class KeepItemVisitorBase extends AnnotationVisitorBase {
    private String memberBindingReference = null;
    private String kind = null;
    private final ClassDeclaration classDeclaration = new ClassDeclaration(this::getBindingsHelper);
    private final MemberDeclaration memberDeclaration;

    public abstract UserBindingsHelper getBindingsHelper();

    // Constructed item available once visitEnd has been called.
    private KeepItemReference itemReference = null;

    KeepItemVisitorBase() {
      memberDeclaration = new MemberDeclaration(this::getAnnotationName);
    }

    public Collection<KeepItemReference> getItemsWithoutBinding() {
      if (itemReference == null) {
        throw new KeepEdgeException("Item reference not finalized. Missing call to visitEnd()");
      }
      if (Kind.CLASS_AND_MEMBERS.equals(kind)) {
        // If kind is set then visitEnd ensures that this cannot be a binding reference.
        assert !itemReference.isBindingReference();
        KeepItemPattern itemPattern = itemReference.asItemPattern();
        KeepClassItemReference classReference;
        KeepMemberItemPattern memberPattern;
        if (itemPattern.isClassItemPattern()) {
          classReference = itemPattern.asClassItemPattern().toClassItemReference();
          memberPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(classReference)
                  .setMemberPattern(KeepMemberPattern.allMembers())
                  .build();
        } else {
          memberPattern = itemPattern.asMemberItemPattern();
          classReference = memberPattern.getClassReference();
        }
        return ImmutableList.of(classReference, memberPattern.toItemReference());
      } else {
        return Collections.singletonList(itemReference);
      }
    }

    public Collection<KeepItemReference> getItemsWithBinding() {
      if (itemReference == null) {
        throw new KeepEdgeException("Item reference not finalized. Missing call to visitEnd()");
      }
      if (itemReference.isBindingReference()) {
        return Collections.singletonList(itemReference);
      }
      // Kind is only null if item is a "binding reference".
      assert kind != null;
      if (Kind.CLASS_AND_MEMBERS.equals(kind)) {
        KeepItemPattern itemPattern = itemReference.asItemPattern();
        // Ensure we have a member item linked to the correct class.
        KeepMemberItemPattern memberItemPattern;
        if (itemPattern.isClassItemPattern()) {
          memberItemPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(itemPattern.asClassItemPattern().toClassItemReference())
                  .build();
        } else {
          memberItemPattern = itemPattern.asMemberItemPattern();
        }
        // If the class is not a binding, introduce the binding and rewrite the member.
        KeepClassItemReference classItemReference = memberItemPattern.getClassReference();
        if (classItemReference.isClassItemPattern()) {
          KeepClassItemPattern classItemPattern = classItemReference.asClassItemPattern();
          KeepBindingSymbol symbol =
              getBindingsHelper().defineFreshBinding("CLASS", classItemPattern);
          classItemReference = KeepBindingReference.forClass(symbol).toClassItemReference();
          memberItemPattern =
              KeepMemberItemPattern.builder()
                  .copyFrom(memberItemPattern)
                  .setClassReference(classItemReference)
                  .build();
        }
        assert classItemReference.isBindingReference();
        assert memberItemPattern.getClassReference().equals(classItemReference);
        return ImmutableList.of(classItemReference, memberItemPattern.toItemReference());
      } else {
        return Collections.singletonList(itemReference);
      }
    }

    public KeepItemReference getItemReference() {
      if (itemReference == null) {
        throw new KeepEdgeException("Item reference not finalized. Missing call to visitEnd()");
      }
      return itemReference;
    }

    public String getKind() {
      return kind;
    }

    public boolean isDefaultMemberDeclaration() {
      return memberDeclaration.isDefault();
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.Kind.DESCRIPTOR)) {
        super.visitEnum(name, descriptor, value);
      }
      switch (value) {
        case Kind.ONLY_CLASS:
        case Kind.ONLY_MEMBERS:
        case Kind.CLASS_AND_MEMBERS:
          kind = value;
          break;
        default:
          super.visitEnum(name, descriptor, value);
      }
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Item.memberFromBinding) && value instanceof String) {
        memberBindingReference = (String) value;
        return;
      }
      if (classDeclaration.tryParse(name, value)
          || memberDeclaration.tryParse(name, value)) {
        return;
      }
      super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor visitor = memberDeclaration.tryParseArray(name, v -> {});
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      if (memberBindingReference != null) {
        if (!classDeclaration.isDefault()
            || !memberDeclaration.getValue().isNone()
            || kind != null) {
          throw new KeepEdgeException(
              "Cannot define an item explicitly and via a member-binding reference");
        }
        KeepBindingSymbol symbol = getBindingsHelper().resolveUserBinding(memberBindingReference);
        itemReference = KeepBindingReference.forMember(symbol).toItemReference();
      } else {
        KeepMemberPattern memberPattern = memberDeclaration.getValue();
        // If the kind is not set (default) then the content of the members determines the kind.
        if (kind == null) {
          kind = memberPattern.isNone() ? Kind.ONLY_CLASS : Kind.ONLY_MEMBERS;
        }

        KeepClassItemReference classReference = classDeclaration.getValue();
        if (kind.equals(Kind.ONLY_CLASS)) {
          itemReference = classReference;
        } else {
          KeepItemPattern itemPattern =
              KeepMemberItemPattern.builder()
                  .setClassReference(classReference)
                  .setMemberPattern(
                      memberPattern.isNone() ? KeepMemberPattern.allMembers() : memberPattern)
                  .build();
          itemReference = itemPattern.toItemReference();
        }
      }
    }
  }

  private static class KeepBindingVisitor extends KeepItemVisitorBase {

    private final UserBindingsHelper helper;
    private String bindingName;

    public KeepBindingVisitor(UserBindingsHelper helper) {
      this.helper = helper;
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return helper;
    }

    @Override
    public String getAnnotationName() {
      return Binding.CLASS.getSimpleName();
    }

    @Override
    public void visit(String name, Object value) {
      if (name.equals(Binding.bindingName) && value instanceof String) {
        bindingName = (String) value;
        return;
      }
      super.visit(name, value);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      KeepItemReference item = getItemReference();
      // The language currently disallows aliasing bindings, thus a binding cannot directly be
      // defined by a reference to another binding.
      if (item.isBindingReference()) {
        throw new KeepEdgeException(
            "Invalid binding reference to '"
                + item.asBindingReference()
                + "' in binding definition of '"
                + bindingName
                + "'");
      }
      helper.defineUserBinding(bindingName, item.asItemPattern());
    }
  }

  private static class StringArrayVisitor extends AnnotationVisitorBase {
    private final Supplier<String> annotationName;
    private final Consumer<List<String>> fn;
    private final List<String> strings = new ArrayList<>();

    public StringArrayVisitor(Supplier<String> annotationName, Consumer<List<String>> fn) {
      this.annotationName = annotationName;
      this.fn = fn;
    }

    @Override
    public String getAnnotationName() {
      return annotationName.get();
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

  private static class OptionsDeclaration extends SingleDeclaration<KeepOptions> {

    private final String annotationName;

    public OptionsDeclaration(String annotationName) {
      this.annotationName = annotationName;
    }

    @Override
    String kind() {
      return "options";
    }

    @Override
    KeepOptions getDefaultValue() {
      return KeepOptions.keepAll();
    }

    @Override
    KeepOptions parse(String name, Object value) {
      return null;
    }

    @Override
    AnnotationVisitor parseArray(String name, Consumer<KeepOptions> setValue) {
      if (name.equals(AnnotationConstants.Target.disallow)) {
        return new KeepOptionsVisitor(
            annotationName,
            options -> setValue.accept(KeepOptions.disallowBuilder().addAll(options).build()));
      }
      if (name.equals(AnnotationConstants.Target.allow)) {
        return new KeepOptionsVisitor(
            annotationName,
            options -> setValue.accept(KeepOptions.allowBuilder().addAll(options).build()));
      }
      return null;
    }
  }

  private static class KeepTargetVisitor extends KeepItemVisitorBase {

    private final Parent<KeepTarget> parent;
    private final KeepTarget.Builder builder = KeepTarget.builder();
    private final OptionsDeclaration optionsDeclaration =
        new OptionsDeclaration(getAnnotationName());
    private final UserBindingsHelper bindingsHelper;

    static KeepTargetVisitor create(Parent<KeepTarget> parent, UserBindingsHelper bindingsHelper) {
      return new KeepTargetVisitor(parent, bindingsHelper);
    }

    private KeepTargetVisitor(Parent<KeepTarget> parent, UserBindingsHelper bindingsHelper) {
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      return Target.CLASS.getSimpleName();
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor visitor = optionsDeclaration.tryParseArray(name, builder::setOptions);
      if (visitor != null) {
        return visitor;
      }
      return super.visitArray(name);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      for (KeepItemReference item : getItemsWithBinding()) {
        parent.accept(builder.setItemReference(item).build());
      }
    }
  }

  private static class KeepConditionVisitor extends KeepItemVisitorBase {

    private final Parent<KeepCondition> parent;
    private final UserBindingsHelper bindingsHelper;

    public KeepConditionVisitor(Parent<KeepCondition> parent, UserBindingsHelper bindingsHelper) {
      this.parent = parent;
      this.bindingsHelper = bindingsHelper;
    }

    @Override
    public UserBindingsHelper getBindingsHelper() {
      return bindingsHelper;
    }

    @Override
    public String getAnnotationName() {
      return Condition.CLASS.getSimpleName();
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      parent.accept(KeepCondition.builder().setItemReference(getItemReference()).build());
    }
  }

  private static class KeepOptionsVisitor extends AnnotationVisitorBase {

    private final String annotationName;
    private final Parent<Collection<KeepOption>> parent;
    private final Set<KeepOption> options = new HashSet<>();

    public KeepOptionsVisitor(String annotationName, Parent<Collection<KeepOption>> parent) {
      this.annotationName = annotationName;
      this.parent = parent;
    }

    @Override
    public String getAnnotationName() {
      return annotationName;
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.Option.DESCRIPTOR)) {
        super.visitEnum(ignore, descriptor, value);
      }
      KeepOption option;
      switch (value) {
        case Option.SHRINKING:
          option = KeepOption.SHRINKING;
          break;
        case Option.OPTIMIZATION:
          option = KeepOption.OPTIMIZING;
          break;
        case Option.OBFUSCATION:
          option = KeepOption.OBFUSCATING;
          break;
        case Option.ACCESS_MODIFICATION:
          option = KeepOption.ACCESS_MODIFICATION;
          break;
        case Option.ANNOTATION_REMOVAL:
          option = KeepOption.ANNOTATION_REMOVAL;
          break;
        default:
          super.visitEnum(ignore, descriptor, value);
          return;
      }
      options.add(option);
    }

    @Override
    public void visitEnd() {
      parent.accept(options);
      super.visitEnd();
    }
  }

  private static class MemberAccessVisitor extends AnnotationVisitorBase {
    private final Supplier<String> annotationName;
    KeepMemberAccessPattern.BuilderBase<?, ?> builder;

    public MemberAccessVisitor(
        Supplier<String> annotationName, KeepMemberAccessPattern.BuilderBase<?, ?> builder) {
      this.annotationName = annotationName;
      this.builder = builder;
    }

    @Override
    public String getAnnotationName() {
      return annotationName.get();
    }

    static boolean withNormalizedAccessFlag(String flag, BiPredicate<String, Boolean> fn) {
      boolean allow = !flag.startsWith(MemberAccess.NEGATION_PREFIX);
      return allow
          ? fn.test(flag, true)
          : fn.test(flag.substring(MemberAccess.NEGATION_PREFIX.length()), false);
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.MemberAccess.DESCRIPTOR)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                AccessVisibility visibility = getAccessVisibilityFromString(flag);
                if (visibility != null) {
                  builder.setAccessVisibility(visibility, allow);
                  return true;
                }
                switch (flag) {
                  case MemberAccess.STATIC:
                    builder.setStatic(allow);
                    return true;
                  case MemberAccess.FINAL:
                    builder.setFinal(allow);
                    return true;
                  case MemberAccess.SYNTHETIC:
                    builder.setSynthetic(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        super.visitEnum(ignore, descriptor, value);
      }
    }

    private AccessVisibility getAccessVisibilityFromString(String value) {
      switch (value) {
        case MemberAccess.PUBLIC:
          return AccessVisibility.PUBLIC;
        case MemberAccess.PROTECTED:
          return AccessVisibility.PROTECTED;
        case MemberAccess.PACKAGE_PRIVATE:
          return AccessVisibility.PACKAGE_PRIVATE;
        case MemberAccess.PRIVATE:
          return AccessVisibility.PRIVATE;
        default:
          return null;
      }
    }
  }

  private static class MethodAccessVisitor extends MemberAccessVisitor {

    @SuppressWarnings("HidingField")
    KeepMethodAccessPattern.Builder builder;

    public MethodAccessVisitor(
        Supplier<String> annotationName, KeepMethodAccessPattern.Builder builder) {
      super(annotationName, builder);
      this.builder = builder;
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.MethodAccess.DESCRIPTOR)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                switch (flag) {
                  case MethodAccess.SYNCHRONIZED:
                    builder.setSynchronized(allow);
                    return true;
                  case MethodAccess.BRIDGE:
                    builder.setBridge(allow);
                    return true;
                  case MethodAccess.NATIVE:
                    builder.setNative(allow);
                    return true;
                  case MethodAccess.ABSTRACT:
                    builder.setAbstract(allow);
                    return true;
                  case MethodAccess.STRICT_FP:
                    builder.setStrictFp(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        // Continue visitation with the "member" descriptor to allow matching the common values.
        super.visitEnum(ignore, MemberAccess.DESCRIPTOR, value);
      }
    }
  }

  private static class FieldAccessVisitor extends MemberAccessVisitor {

    @SuppressWarnings("HidingField")
    KeepFieldAccessPattern.Builder builder;

    public FieldAccessVisitor(
        Supplier<String> annotationName, KeepFieldAccessPattern.Builder builder) {
      super(annotationName, builder);
      this.builder = builder;
    }

    @Override
    public void visitEnum(String ignore, String descriptor, String value) {
      if (!descriptor.equals(AnnotationConstants.FieldAccess.DESCRIPTOR)) {
        super.visitEnum(ignore, descriptor, value);
      }
      boolean handled =
          withNormalizedAccessFlag(
              value,
              (flag, allow) -> {
                switch (flag) {
                  case FieldAccess.VOLATILE:
                    builder.setVolatile(allow);
                    return true;
                  case FieldAccess.TRANSIENT:
                    builder.setTransient(allow);
                    return true;
                  default:
                    return false;
                }
              });
      if (!handled) {
        // Continue visitation with the "member" descriptor to allow matching the common values.
        super.visitEnum(ignore, MemberAccess.DESCRIPTOR, value);
      }
    }
  }
}
