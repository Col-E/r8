// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class AsmRewriter {

  public static int ASM_VERSION = Opcodes.ASM9;

  public static byte[] transformInvoke(byte[] bytes, MethodTransformer transformer) {
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(reader, 0);
    ClassVisitor subvisitor = new InvokeTransformer(writer, transformer);
    reader.accept(subvisitor, 0);
    return writer.toByteArray();
  }

  public static class InvokeTransformer extends ClassVisitor {

    private final MethodTransformer transformer;

    InvokeTransformer(ClassWriter writer, MethodTransformer transformer) {
      super(ASM_VERSION, writer);
      this.transformer = transformer;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor sub = super.visitMethod(access, name, descriptor, signature, exceptions);
      transformer.setMv(sub);
      return transformer;
    }
  }

  public static class MethodTransformer extends MethodVisitor {

    protected MethodTransformer(int api) {
      super(api);
    }

    public void setMv(MethodVisitor visitor) {
      this.mv = visitor;
    }
  }
}
