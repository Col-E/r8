// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.transformers;

import static org.objectweb.asm.Opcodes.ASM7;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer.MethodContext;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

public class ClassFileTransformer {

  /**
   * Basic algorithm for transforming the content of a class file.
   *
   * <p>The provided transformers are nested in the order given: the first in the list will receive
   * is call back first, if it forwards to 'super' then the seconds call back will be called, etc,
   * until finally the writer will be called. If the writer is not called the effect is as if the
   * callback was never called and its content will not be in the result.
   */
  public static byte[] transform(
      byte[] bytes,
      List<ClassTransformer> classTransformers,
      List<MethodTransformer> methodTransformers) {
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(reader, 0);
    ClassVisitor subvisitor = new InnerMostClassTransformer(writer, methodTransformers);
    for (int i = classTransformers.size() - 1; i >= 0; i--) {
      classTransformers.get(i).setSubVisitor(subvisitor);
      subvisitor = classTransformers.get(i);
    }
    reader.accept(subvisitor, 0);
    return writer.toByteArray();
  }

  // Inner-most bride from the class transformation to the method transformers.
  private static class InnerMostClassTransformer extends ClassVisitor {
    ClassReference classReference;
    final List<MethodTransformer> methodTransformers;

    InnerMostClassTransformer(ClassWriter writer, List<MethodTransformer> methodTransformers) {
      super(ASM7, writer);
      this.methodTransformers = methodTransformers;
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
      classReference = Reference.classFromBinaryName(name);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodContext context = createMethodContext(access, name, descriptor);
      MethodVisitor subvisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
      for (int i = methodTransformers.size() - 1; i >= 0; i--) {
        MethodTransformer transformer = methodTransformers.get(i);
        transformer.setSubVisitor(subvisitor);
        transformer.setContext(context);
        subvisitor = transformer;
      }
      return subvisitor;
    }

    private MethodContext createMethodContext(int access, String name, String descriptor) {
      // Maybe clean up this parsing of info as it is not very nice.
      MethodSignature methodSignature = MethodSignature.fromSignature(name, descriptor);
      MethodReference methodReference =
          Reference.method(
              classReference,
              name,
              Arrays.stream(methodSignature.parameters)
                  .map(DescriptorUtils::javaTypeToDescriptor)
                  .map(Reference::typeFromDescriptor)
                  .collect(Collectors.toList()),
              methodSignature.type.equals("void")
                  ? null
                  : Reference.typeFromDescriptor(
                      DescriptorUtils.javaTypeToDescriptor(methodSignature.type)));
      return new MethodContext(methodReference, access);
    }
  }

  // Transformer utilities.

  private final byte[] bytes;
  private final List<ClassTransformer> classTransformers = new ArrayList<>();
  private final List<MethodTransformer> methodTransformers = new ArrayList<>();

  private ClassFileTransformer(byte[] bytes) {
    this.bytes = bytes;
  }

  public static ClassFileTransformer create(byte[] bytes) {
    return new ClassFileTransformer(bytes);
  }

  public static ClassFileTransformer create(Class<?> clazz) throws IOException {
    return create(ToolHelper.getClassAsBytes(clazz));
  }

  public byte[] transform() {
    return ClassFileTransformer.transform(bytes, classTransformers, methodTransformers);
  }

  /** Base addition of a transformer on the class. */
  public ClassFileTransformer addClassTransformer(ClassTransformer transformer) {
    classTransformers.add(transformer);
    return this;
  }

  /** Base addtion of a transformer on methods. */
  public ClassFileTransformer addMethodTransformer(MethodTransformer transformer) {
    methodTransformers.add(transformer);
    return this;
  }

  /** Unconditionally replace the implements clause of a class. */
  public ClassFileTransformer setImplements(Class<?>... interfaces) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] ignoredInterfaces) {
            super.visit(
                version,
                access,
                name,
                signature,
                superName,
                Arrays.stream(interfaces)
                    .map(clazz -> DescriptorUtils.getBinaryNameFromJavaType(clazz.getTypeName()))
                    .toArray(String[]::new));
          }
        });
  }

  /** Abstraction of the MethodVisitor.visitMethodInsn method with its continuation. */
  @FunctionalInterface
  public interface MethodInsnTransform {
    void visitMethodInsn(
        int opcode,
        String owner,
        String name,
        String descriptor,
        boolean isInterface,
        MethodInsnTransformContinuation continuation);
  }

  /** Continuation for transforming a method. Will continue with the super visitor if called. */
  @FunctionalInterface
  public interface MethodInsnTransformContinuation {
    void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface);
  }

  public ClassFileTransformer transformMethodInsnInMethod(
      String methodName, MethodInsnTransform transform) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (getContext().method.getMethodName().equals(methodName)) {
              transform.visitMethodInsn(
                  opcode, owner, name, descriptor, isInterface, super::visitMethodInsn);
            } else {
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
          }
        });
  }
}
