// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.methodhandles;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassTransformer;
import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

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

  private static final String cDesc = TestBase.binaryName(MethodHandleTest.C.class);
  private static final String eDesc = TestBase.binaryName(MethodHandleTest.E.class);
  private static final String fDesc = TestBase.binaryName(MethodHandleTest.F.class);
  private static final String iDesc = TestBase.binaryName(MethodHandleTest.I.class);
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

  public static byte[] getTransformedClass() throws Exception {
    ImmutableMap<String, Type> types =
        ImmutableMap.<String, Type>builder()
            .put("viType", viType)
            .put("jiType", jiType)
            .put("vicType", vicType)
            .put("jicType", jicType)
            .put("veType", veType)
            .put("fType", fType)
            .build();

    ImmutableMap<String, Handle> methods =
        ImmutableMap.<String, Handle>builder()
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
            .put(
                "constructorMethod", new Handle(H_NEWINVOKESPECIAL, cDesc, "<init>", viDesc, false))
            .build();

    return ClassFileTransformer.create(MethodHandleTest.class)
        .addClassTransformer(
            new ClassTransformer() {
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
                      MethodVisitor mv = super.visitMethod(access, name, desc, null, null);
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
                      MethodVisitor mv = super.visitMethod(access, name, desc, null, null);
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
            })
        .transform();
  }
}
