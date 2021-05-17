// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

@RunWith(Parameterized.class)
public class Java11D8CompilationTest extends TestBase {

  public Java11D8CompilationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static void assertNoNests(CodeInspector inspector) {
    assertTrue(
        inspector.allClasses().stream().noneMatch(subj -> subj.getDexProgramClass().isInANest()));
  }

  @Test
  public void testR8CompiledWithD8() throws Exception {
    testForD8()
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_11_JAR)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .compile()
        .inspect(Java11D8CompilationTest::assertNoNests);
  }

  @Test
  public void testR8CompiledWithD8ToCf() throws Exception {
    Path r8Desugared =
        testForD8(Backend.CF)
            .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_11_JAR)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .inspect(Java11D8CompilationTest::assertNoNests)
            .writeToZip();

    // Check that the desugared classes has the expected class file versions and that no nest
    // related attributes remains.
    ZipUtils.iter(
        r8Desugared,
        (entry, input) -> {
          if (SyntheticItemsTestUtils.isExternalStaticInterfaceCall(
              Reference.classFromBinaryName(
                  entry
                      .getName()
                      .substring(0, entry.getName().length() - CLASS_EXTENSION.length())))) {
            assertEquals(CfVersion.V1_8, extractClassFileVersionAndAssertNoNestAttributes(input));
          } else {
            assertTrue(
                extractClassFileVersionAndAssertNoNestAttributes(input)
                    .isLessThanOrEqualTo(CfVersion.V1_7));
          }
        });
  }

  protected static CfVersion extractClassFileVersionAndAssertNoNestAttributes(InputStream classFile)
      throws IOException {
    class ClassFileVersionExtractor extends ClassVisitor {
      private int version;

      private ClassFileVersionExtractor() {
        super(ASM_VERSION);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        this.version = version;
      }

      @Override
      public void visitAttribute(Attribute attribute) {}

      CfVersion getClassFileVersion() {
        return CfVersion.fromRaw(version);
      }

      @Override
      public void visitNestHost(String nestHost) {
        // ASM will always report the NestHost attribute if present (independently of class
        // file version).
        fail();
      }

      @Override
      public void visitNestMember(String nestMember) {
        // ASM will always report the NestHost attribute if present (independently of class
        // file version).
        fail();
      }
    }

    ClassReader reader = new ClassReader(classFile);
    ClassFileVersionExtractor extractor = new ClassFileVersionExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassFileVersion();
  }
}
