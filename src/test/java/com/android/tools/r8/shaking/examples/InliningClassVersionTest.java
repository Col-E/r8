// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.io.ByteStreams;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class InliningClassVersionTest extends TestBase {

  private final int OLD_VERSION = Opcodes.V1_6;
  private final String BASE_DESCRIPTOR = DescriptorUtils.javaTypeToDescriptor(Base.class.getName());

  private static class Base {

    public static void main(String[] args) {
      System.out.println(Inlinee.foo());
    }
  }

  private static class Inlinee {
    public static String foo() {
      return "Hello from Inlinee!";
    }
  }

  private static class DowngradeVisitor extends ClassVisitor {

    private final int version;

    DowngradeVisitor(ClassVisitor cv, int version) {
      super(Opcodes.ASM6, cv);
      this.version = version;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      assert version > this.version
          : "Going from " + version + " to " + this.version + " is not a downgrade";
      super.visit(this.version, access, name, signature, superName, interfaces);
    }
  }

  private static byte[] downgradeClass(byte[] classBytes, int version) {
    ClassWriter writer = new ClassWriter(0);
    new ClassReader(classBytes).accept(new DowngradeVisitor(writer, version), 0);
    return writer.toByteArray();
  }

  @Test
  public void test() throws Exception {
    Path inputJar = writeInput();
    assertEquals(OLD_VERSION, getBaseClassVersion(inputJar));
    ProcessResult runInput = run(inputJar);
    assertEquals(0, runInput.exitCode);
    Path outputJar = runR8(inputJar);
    ProcessResult runOutput = run(outputJar);
    assertEquals(runInput.toString(), runOutput.toString());
    assertNotEquals(
        "Inliner did not upgrade classfile version", OLD_VERSION, getBaseClassVersion(outputJar));
  }

  private int getBaseClassVersion(Path jar) throws Exception {
    return getClassVersion(jar, BASE_DESCRIPTOR);
  }

  private int getClassVersion(Path jar, String descriptor) throws Exception {

    class ClassVersionReader extends ClassVisitor {
      private int version = -1;

      private ClassVersionReader() {
        super(Opcodes.ASM6);
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
        assert version != -1;
        this.version = version;
      }
    }

    byte[] bytes =
        ByteStreams.toByteArray(
            new ArchiveClassFileProvider(jar).getProgramResource(descriptor).getByteStream());
    ClassVersionReader reader = new ClassVersionReader();
    new ClassReader(bytes).accept(reader, 0);
    assert reader.version != -1;
    return reader.version;
  }

  private Path writeInput() throws Exception {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer consumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    consumer.accept(
        ByteDataView.of(downgradeClass(ToolHelper.getClassAsBytes(Base.class), OLD_VERSION)),
        BASE_DESCRIPTOR,
        null);
    consumer.accept(
        ByteDataView.of(ToolHelper.getClassAsBytes(Inlinee.class)),
        DescriptorUtils.javaTypeToDescriptor(Inlinee.class.getName()),
        null);
    consumer.finished(null);
    return inputJar;
  }

  private ProcessResult run(Path jar) throws Exception {
    return ToolHelper.runJava(jar, Base.class.getName());
  }

  private Path runR8(Path inputJar) throws Exception {
    List<String> keepRule =
        Collections.singletonList(
            "-keep class " + Base.class.getName() + " { public static void main(...); }");
    Path outputJar = temp.getRoot().toPath().resolve("output.jar");
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(inputJar)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .addProguardConfiguration(keepRule, Origin.unknown())
            .setOutput(outputJar, OutputMode.ClassFile)
            .build());
    return outputJar;
  }
}
