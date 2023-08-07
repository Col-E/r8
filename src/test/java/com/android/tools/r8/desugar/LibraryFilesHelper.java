// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ObjectArrays;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class LibraryFilesHelper implements Opcodes {

  public static Path[] getJdk9LibraryFiles(TemporaryFolder temp) throws Exception {
    // TODO(b/270105162): We should be able to run this on windows.
    Assume.assumeFalse(ToolHelper.isWindows());
    // TODO(b/180553597): Add JDK-9 runtime jar instead. As a temporary solution we use the JDK-8
    //  runtime with additional stubs.
    // We use jdk-8 for compilation because in jdk-9 and higher we would need to deal with the
    // module patching logic.
    Path generatedJar = temp.newFolder().toPath().resolve("stubs.jar");
    ZipBuilder builder = ZipBuilder.builder(generatedJar);
    addStringConcatFactory(builder);
    addVarHandle(builder);
    builder.build();
    return new Path[] {generatedJar, ToolHelper.getJava8RuntimeJar()};
  }

  public static Path[] getJdk11LibraryFiles(TemporaryFolder temp) throws Exception {
    Assume.assumeFalse(ToolHelper.isWindows());
    // TODO(b/180553597): Add JDK-11 runtime jar instead. As a temporary solution we use the JDK-8
    //  runtime with additional stubs.
    return getJdk9LibraryFiles(temp);
  }

  public static Path[] getJdk15LibraryFiles(TemporaryFolder temp) throws Exception {
    // TODO(b/270105162): We should be able to run this on windows.
    Assume.assumeFalse(ToolHelper.isWindows());
    // TODO(b/169645628): Add JDK-15 runtime jar instead. As a temporary solution we use the jdk 8
    //   runtime with additional stubs.
    Path generatedJar = temp.newFolder().toPath().resolve("stubs.jar");
    ZipBuilder builder = ZipBuilder.builder(generatedJar);
    addRecord(builder);
    addRecordComponent(builder);
    addObjectsMethod(builder);
    addTypeDescriptor(builder);
    builder.build();
    return ObjectArrays.concat(getJdk9LibraryFiles(temp), new Path[] {generatedJar}, Path.class);
  }

  // Generates a class file for:
  // <pre>
  // public class ObjectMethods {}
  // </pre>
  private static void addObjectsMethod(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder, ACC_PUBLIC, "java/lang/runtime/ObjectMethods", ConsumerUtils.emptyConsumer());
  }

  // Generates a class file for:
  // <pre>
  // public interface TypeDescriptor {
  //   String descriptorString();
  // }
  // </pre>
  private static void addTypeDescriptor(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder,
        ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
        "java/lang/invoke/TypeDescriptor",
        methodAdder ->
            methodAdder.add(
                ACC_PUBLIC | ACC_ABSTRACT,
                "descriptorString",
                methodDescriptor(false, String.class)));
  }

  // Generates a class file for:
  // <pre>
  // public class StringConcatFactory {}
  // </pre>
  private static void addStringConcatFactory(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder, ACC_PUBLIC, "java/lang/invoke/StringConcatFactory", ConsumerUtils.emptyConsumer());
  }

  // Generates a class file for:
  // <pre>
  // public class VarHandle {}
  // </pre>
  private static void addVarHandle(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder, ACC_PUBLIC, "java/lang/invoke/VarHandle", ConsumerUtils.emptyConsumer());
  }

  // Generates a class file for:
  // <pre>
  // public abstract class Record {
  //   protected Record() {}
  //
  //   public abstract boolean equals(Object obj);
  //
  //   public abstract int hashCode();
  //
  //   public abstract String toString();
  // }
  // </pre>
  private static void addRecord(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder,
        ACC_PUBLIC | ACC_ABSTRACT,
        "java/lang/Record",
        methodAdder -> {
          methodAdder.add(ACC_PROTECTED, "<init>", "()V");
          methodAdder.add(
              ACC_PUBLIC | ACC_ABSTRACT,
              "equals",
              methodDescriptor(false, Object.class, boolean.class));
          methodAdder.add(
              ACC_PUBLIC | ACC_ABSTRACT, "hashCode", methodDescriptor(false, int.class));
          methodAdder.add(
              ACC_PUBLIC | ACC_ABSTRACT, "toString", methodDescriptor(false, String.class));
        });
  }

  // Generates a class file for:
  // <pre>
  // public class RecordComponent {}
  // </pre>
  private static void addRecordComponent(ZipBuilder builder) throws Exception {
    addClassToZipBuilder(
        builder, ACC_PUBLIC, "java/lang/reflect/RecordComponent", ConsumerUtils.emptyConsumer());
  }

  // Generates a class file for:
  // <pre>
  // public interface Supplier {
  //   Object get();
  // }
  // </pre>
  public static byte[] getSupplier() {
    return createClass(
        ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT,
        "java/util/function/Supplier",
        methodAdder ->
            methodAdder.add(
                ACC_PUBLIC | ACC_ABSTRACT, "get", methodDescriptor(false, Object.class)));
  }

  private static void addClassToZipBuilder(
      ZipBuilder builder, int access, String binaryName, Consumer<MethodAdder> consumer)
      throws Exception {
    builder.addBytes(binaryName + ".class", createClass(access, binaryName, consumer));
  }

  @FunctionalInterface
  public interface MethodAdder {
    void add(int access, String name, String descriptor);
  }

  public static String descriptor(Class<?> clazz) {
    return DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName());
  }

  public static String methodDescriptor(boolean isVoid, Class<?>... clazz) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (int i = 0; i < clazz.length - (isVoid ? 0 : 1); i++) {
      sb.append(descriptor(clazz[i]));
    }
    sb.append(")");
    if (!isVoid) {
      sb.append(descriptor(clazz[clazz.length - 1]));
    }
    return sb.toString();
  }

  public static byte[] createClass(int access, String binaryName, Consumer<MethodAdder> consumer) {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V1_8, access, binaryName, null, "java/lang/Object", null);

    consumer.accept(
        (access1, name, descriptor1) -> cw.visitMethod(access1, name, descriptor1, null, null));
    cw.visitEnd();
    return cw.toByteArray();
  }
}
