// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

@RunWith(Parameterized.class)
public class GenericSignatureIdentityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenericSignatureIdentityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testAllClassSignature() throws Exception {
    testParseSignaturesInJar(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS);
  }

  public static void testParseSignaturesInJar(Path jar) throws Exception {
    GenericSignatureReader genericSignatureReader = new GenericSignatureReader();
    ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(jar));
    ZipEntry next = inputStream.getNextEntry();
    while (next != null) {
      if (next.getName().endsWith(".class")) {
        ClassReader classReader = new ClassReader(inputStream);
        classReader.accept(genericSignatureReader, 0);
      }
      next = inputStream.getNextEntry();
    }
  }

  private static class GenericSignatureReader extends ClassVisitor {

    public final Set<String> signatures = new HashSet<>();
    private final DexItemFactory factory = new DexItemFactory();

    private GenericSignatureReader() {
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
      if (signature == null) {
        return;
      }
      TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
      ClassSignature classSignature =
          GenericSignature.parseClassSignature(
              name, signature, Origin.unknown(), factory, new Reporter(testDiagnosticMessages));
      assertEquals(signature, classSignature.toString());
      testDiagnosticMessages.assertNoMessages();
    }
  }
}
