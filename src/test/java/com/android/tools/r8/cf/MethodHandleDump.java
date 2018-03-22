// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import org.objectweb.asm.*;

// The method MethodHandleDump.transform() translates methods in MethodHandleTest that look like
//     MethodType.methodType(TYPES)
// and
//     MethodHandles.lookup().findKIND(FOO.class, "METHOD", TYPE)
// into LDC instructions.
// This is necessary since there is no Java syntax that compiles to
// LDC of a constant method handle or constant method type.
//
// The method dumpD() dumps a class equivalent to MethodHandleTest.D
// that uses an LDC instruction instead of MethodHandles.lookup().findSpecial().
// The LDC instruction loads an InvokeSpecial constant method handle to a C method,
// so this LDC instruction must be in a subclass of C, and not directly on MethodHandleTest.
public class MethodHandleDump implements Opcodes {

  private static final String cDesc = "com/android/tools/r8/cf/MethodHandleTest$C";
  private static final String eDesc = "com/android/tools/r8/cf/MethodHandleTest$E";
  private static final String fDesc = "com/android/tools/r8/cf/MethodHandleTest$F";
  private static final String iDesc = "com/android/tools/r8/cf/MethodHandleTest$I";
  private static final Type viType = Type.getMethodType(Type.VOID_TYPE, Type.INT_TYPE);
  private static final Type jiType = Type.getMethodType(Type.LONG_TYPE, Type.INT_TYPE);
  private static final Type vicType =
      Type.getMethodType(Type.VOID_TYPE, Type.INT_TYPE, Type.CHAR_TYPE);
  private static final Type jicType =
      Type.getMethodType(Type.LONG_TYPE, Type.INT_TYPE, Type.CHAR_TYPE);
  private static final Type veType = Type.getMethodType(Type.VOID_TYPE, Type.getObjectType(eDesc));
  private static final Type fType = Type.getMethodType(Type.getObjectType(fDesc));
  private static final String viDesc = viType.getDescriptor();
  private static final String jiDesc = jiType.getDescriptor();
  private static final String vicDesc = vicType.getDescriptor();
  private static final String jicDesc = jicType.getDescriptor();
  private static final String intDesc = Type.INT_TYPE.getDescriptor();

  public static byte[] transform(byte[] input) throws Exception {
    ImmutableMap.Builder<String, Type> typesBuilder = ImmutableMap.builder();
    ImmutableMap<String, Type> types =
        typesBuilder
            .put("viType", viType)
            .put("jiType", jiType)
            .put("vicType", vicType)
            .put("jicType", jicType)
            .put("veType", veType)
            .put("fType", fType)
            .build();

    Builder<String, Handle> methodsBuilder = ImmutableMap.builder();
    methodsBuilder
        .put("scviMethod", new Handle(H_INVOKESTATIC, cDesc, "svi", viDesc, false))
        .put("scjiMethod", new Handle(H_INVOKESTATIC, cDesc, "sji", jiDesc, false))
        .put("scvicMethod", new Handle(H_INVOKESTATIC, cDesc, "svic", vicDesc, false))
        .put("scjicMethod", new Handle(H_INVOKESTATIC, cDesc, "sjic", jicDesc, false))
        .put("vcviMethod", new Handle(H_INVOKEVIRTUAL, cDesc, "vvi", viDesc, false))
        .put("vcjiMethod", new Handle(H_INVOKEVIRTUAL, cDesc, "vji", jiDesc, false))
        .put("vcvicMethod", new Handle(H_INVOKEVIRTUAL, cDesc, "vvic", vicDesc, false))
        .put("vcjicMethod", new Handle(H_INVOKEVIRTUAL, cDesc, "vjic", jicDesc, false))
        .put("siviMethod", new Handle(H_INVOKESTATIC, iDesc, "svi", viDesc, true))
        .put("sijiMethod", new Handle(H_INVOKESTATIC, iDesc, "sji", jiDesc, true))
        .put("sivicMethod", new Handle(H_INVOKESTATIC, iDesc, "svic", vicDesc, true))
        .put("sijicMethod", new Handle(H_INVOKESTATIC, iDesc, "sjic", jicDesc, true))
        .put("diviMethod", new Handle(H_INVOKEINTERFACE, iDesc, "dvi", viDesc, true))
        .put("dijiMethod", new Handle(H_INVOKEINTERFACE, iDesc, "dji", jiDesc, true))
        .put("divicMethod", new Handle(H_INVOKEINTERFACE, iDesc, "dvic", vicDesc, true))
        .put("dijicMethod", new Handle(H_INVOKEINTERFACE, iDesc, "djic", jicDesc, true))
        .put("vciSetField", new Handle(H_PUTFIELD, cDesc, "vi", intDesc, false))
        .put("sciSetField", new Handle(H_PUTSTATIC, cDesc, "si", intDesc, false))
        .put("vciGetField", new Handle(H_GETFIELD, cDesc, "vi", intDesc, false))
        .put("sciGetField", new Handle(H_GETSTATIC, cDesc, "si", intDesc, false))
        .put("iiSetField", new Handle(H_PUTSTATIC, iDesc, "ii", intDesc, true))
        .put("iiGetField", new Handle(H_GETSTATIC, iDesc, "ii", intDesc, true))
        .put("constructorMethod", new Handle(H_NEWINVOKESPECIAL, cDesc, "<init>", viDesc, false));
    ImmutableMap<String, Handle> methods = methodsBuilder.build();
    ClassReader cr = new ClassReader(input);
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cr.accept(
        new ClassVisitor(ASM6, cw) {

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            switch (desc) {
              case "()Ljava/lang/invoke/MethodType;":
                {
                  Type type = types.get(name);
                  assert type != null : name;
                  assert access == ACC_PUBLIC + ACC_STATIC;
                  assert signature == null;
                  assert exceptions == null;
                  MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
                  mv.visitCode();
                  mv.visitLdcInsn(type);
                  mv.visitInsn(ARETURN);
                  mv.visitMaxs(-1, -1);
                  mv.visitEnd();
                  return null;
                }
              case "()Ljava/lang/invoke/MethodHandle;":
                {
                  Handle method = methods.get(name);
                  assert access == ACC_PUBLIC + ACC_STATIC;
                  assert method != null : name;
                  assert signature == null;
                  assert exceptions == null;
                  MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
                  mv.visitCode();
                  mv.visitLdcInsn(method);
                  mv.visitInsn(ARETURN);
                  mv.visitMaxs(-1, -1);
                  mv.visitEnd();
                  return null;
                }
              default:
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
          }
        },
        0);
    return cw.toByteArray();
  }

  public static byte[] dumpD() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/cf/MethodHandleTest$D",
        null,
        "com/android/tools/r8/cf/MethodHandleTest$C",
        null);

    cw.visitInnerClass(
        "com/android/tools/r8/cf/MethodHandleTest$D",
        "com/android/tools/r8/cf/MethodHandleTest",
        "D",
        ACC_PUBLIC + ACC_STATIC);

    cw.visitInnerClass(
        "com/android/tools/r8/cf/MethodHandleTest$C",
        "com/android/tools/r8/cf/MethodHandleTest",
        "C",
        ACC_PUBLIC + ACC_STATIC);

    cw.visitInnerClass(
        "java/lang/invoke/MethodHandles$Lookup",
        "java/lang/invoke/MethodHandles",
        "Lookup",
        ACC_PUBLIC + ACC_FINAL + ACC_STATIC);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(
          INVOKESPECIAL, "com/android/tools/r8/cf/MethodHandleTest$C", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv =
          cw.visitMethod(
              ACC_PUBLIC + ACC_STATIC,
              "vcviSpecialMethod",
              "()Ljava/lang/invoke/MethodHandle;",
              null,
              null);
      mv.visitCode();
      mv.visitLdcInsn(new Handle(H_INVOKESPECIAL, cDesc, "vvi", viDesc, false));
      mv.visitInsn(ARETURN);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "vvi", "(I)V", null, null);
      mv.visitCode();
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
