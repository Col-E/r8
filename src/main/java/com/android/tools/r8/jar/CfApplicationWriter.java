// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar;

import static org.objectweb.asm.Opcodes.ASM6;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class CfApplicationWriter {
  private static final boolean RUN_VERIFIER = false;
  private static final boolean PRINT_CF = false;

  private final DexApplication application;
  private final NamingLens namingLens;
  private final InternalOptions options;

  public final ProguardMapSupplier proguardMapSupplier;
  public final String deadCode;
  public final String proguardSeedsData;

  public CfApplicationWriter(
      DexApplication application,
      InternalOptions options,
      String deadCode,
      NamingLens namingLens,
      String proguardSeedsData,
      ProguardMapSupplier proguardMapSupplier) {
    this.application = application;
    this.namingLens = namingLens;
    this.options = options;
    this.proguardMapSupplier = proguardMapSupplier;
    this.deadCode = deadCode;
    this.proguardSeedsData = proguardSeedsData;
  }

  public void write(ClassFileConsumer consumer, ExecutorService executor) throws IOException {
    application.timing.begin("CfApplicationWriter.write");
    try {
      writeApplication(consumer, executor);
    } finally {
      application.timing.end();
    }
  }

  private void writeApplication(ClassFileConsumer consumer, ExecutorService executor)
      throws IOException {
    for (DexProgramClass clazz : application.classes()) {
      if (clazz.getSynthesizedFrom().isEmpty()) {
        writeClass(clazz, consumer);
      } else {
        throw new Unimplemented("No support for synthetics in the Java bytecode backend.");
      }
    }
    ApplicationWriter.supplyAdditionalConsumers(
        application, namingLens, options, deadCode, proguardMapSupplier, proguardSeedsData);
  }

  private void writeClass(DexProgramClass clazz, ClassFileConsumer consumer) throws IOException {
    ClassWriter writer = new ClassWriter(0);
    writer.visitSource(clazz.sourceFile != null ? clazz.sourceFile.toString() : null, null);
    int version = clazz.getClassFileVersion();
    int access = clazz.accessFlags.getAsCfAccessFlags();
    String desc = clazz.type.toDescriptorString();
    String name = clazz.type.getInternalName();
    String signature = null; // TODO(zerny): Support generic signatures.
    String superName =
        clazz.type == options.itemFactory.objectType ? null : clazz.superType.getInternalName();
    String[] interfaces = new String[clazz.interfaces.values.length];
    for (int i = 0; i < clazz.interfaces.values.length; i++) {
      interfaces[i] = clazz.interfaces.values[i].getInternalName();
    }
    writer.visit(version, access, name, signature, superName, interfaces);

    if (clazz.getEnclosingMethod() != null) {
      clazz.getEnclosingMethod().write(writer);
    }

    for (InnerClassAttribute entry : clazz.getInnerClasses()) {
      entry.write(writer);
    }

    for (DexEncodedField field : clazz.staticFields()) {
      writeField(field, writer);
    }
    for (DexEncodedField field : clazz.instanceFields()) {
      writeField(field, writer);
    }
    for (DexEncodedMethod method : clazz.directMethods()) {
      writeMethod(method, writer);
    }
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      writeMethod(method, writer);
    }
    writer.visitEnd();

    byte[] result = writer.toByteArray();
    if (PRINT_CF) {
      System.out.print(printCf(result));
      System.out.flush();
    }
    if (RUN_VERIFIER) {
      // Generally, this will fail with ClassNotFoundException,
      // so don't assert that verifyCf() returns true.
      verifyCf(result);
    }
    ExceptionUtils.withConsumeResourceHandler(
        options.reporter, handler -> consumer.accept(result, desc, handler));
  }

  private Object getStaticValue(DexEncodedField field) {
    if (!field.accessFlags.isStatic() || field.staticValue == null) {
      return null;
    }
    return field.staticValue.asAsmEncodedObject();
  }

  private void writeField(DexEncodedField field, ClassWriter writer) {
    int access = field.accessFlags.getAsCfAccessFlags();
    String name = field.field.name.toString();
    String desc = field.field.type.toDescriptorString();
    String signature = null; // TODO(zerny): Support generic signatures.
    Object value = getStaticValue(field);
    writer.visitField(access, name, desc, signature, value);
    // TODO(zerny): Add annotations to the field.
  }

  private void writeMethod(DexEncodedMethod method, ClassWriter writer) {
    int access = method.accessFlags.getAsCfAccessFlags();
    String name = method.method.name.toString();
    String desc = method.descriptor();
    String signature = null; // TODO(zerny): Support generic signatures.
    String[] exceptions = null;
    MethodVisitor visitor = writer.visitMethod(access, name, desc, signature, exceptions);
    if (!method.accessFlags.isAbstract() && !method.accessFlags.isNative()) {
      writeCode(method.getCode(), visitor);
    }
  }

  private void writeCode(Code code, MethodVisitor visitor) {
    if (code.isJarCode()) {
      code.asJarCode().writeTo(visitor);
    } else {
      assert code.isCfCode();
      code.asCfCode().write(visitor);
    }
  }

  public static String printCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    ClassNode node = new ClassNode(ASM6);
    reader.accept(node, ASM6);
    StringWriter writer = new StringWriter();
    for (MethodNode method : node.methods) {
      writer.append(method.name).append(method.desc).append('\n');
      TraceMethodVisitor visitor = new TraceMethodVisitor(new Textifier());
      method.accept(visitor);
      visitor.p.print(new PrintWriter(writer));
      writer.append('\n');
    }
    return writer.toString();
  }

  private static void verifyCf(byte[] result) {
    ClassReader reader = new ClassReader(result);
    PrintWriter pw = new PrintWriter(System.out);
    CheckClassAdapter.verify(reader, false, pw);
  }
}
