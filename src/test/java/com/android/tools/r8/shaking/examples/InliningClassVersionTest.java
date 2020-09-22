// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.io.ByteStreams;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InliningClassVersionTest extends TestBase {

  private final TestParameters parameters;
  private static final String EXPECTED = "Hello from Inlinee!";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public InliningClassVersionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final int OLD_VERSION = Opcodes.V1_6;
  private final String BASE_DESCRIPTOR = DescriptorUtils.javaTypeToDescriptor(Base.class.getName());

  private static class Base {

    public static void main(String[] args) {
      System.out.println(Inlinee.foo());
    }
  }

  private static class Inlinee {

    @NeverPropagateValue
    public static String foo() {
      return "Hello from Inlinee!";
    }
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Inlinee.class)
        .addProgramClassFileData(downgradeBaseClass())
        .run(parameters.getRuntime(), Base.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    Path outputJar = temp.newFile("output.jar").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Inlinee.class)
        .addProgramClassFileData(downgradeBaseClass())
        .addKeepMainRule(Base.class)
        .enableMemberValuePropagationAnnotations()
        .compile()
        .writeToZip(outputJar)
        .run(parameters.getRuntime(), Base.class)
        .assertSuccessWithOutputLines(EXPECTED);
    assertNotEquals(
        "Inliner did not upgrade classfile version", OLD_VERSION, getBaseClassVersion(outputJar));
  }

  private byte[] downgradeBaseClass() throws Exception {
    byte[] transform = transformer(Base.class).setVersion(OLD_VERSION).transform();
    assertEquals(OLD_VERSION, getClassVersion(transform));
    return transform;
  }

  private int getBaseClassVersion(Path jar) throws Exception {
    return getClassVersion(
        ByteStreams.toByteArray(
            new ArchiveClassFileProvider(jar).getProgramResource(BASE_DESCRIPTOR).getByteStream()));
  }

  private int getClassVersion(byte[] classFileBytes) {

    class ClassVersionReader extends ClassVisitor {
      private int version = -1;

      private ClassVersionReader() {
        super(InternalOptions.ASM_VERSION);
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
    ClassVersionReader reader = new ClassVersionReader();
    new ClassReader(classFileBytes).accept(reader, 0);
    assert reader.version != -1;
    return reader.version;
  }
}
