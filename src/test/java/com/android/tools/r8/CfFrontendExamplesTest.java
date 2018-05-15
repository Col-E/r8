// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.google.common.io.ByteStreams.toByteArray;
import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class CfFrontendExamplesTest extends TestBase {

  @Test
  public void testArithmetic() throws Exception {
    runTest("arithmetic.Arithmetic");
  }

  @Test
  public void testArrayAccess() throws Exception {
    runTest("arrayaccess.ArrayAccess");
  }

  @Test
  public void testBArray() throws Exception {
    runTest("barray.BArray");
  }

  @Test
  public void testBridgeMethod() throws Exception {
    runTest("bridge.BridgeMethod");
  }

  @Test
  public void testCommonSubexpressionElimination() throws Exception {
    runTest("cse.CommonSubexpressionElimination");
  }

  @Test
  public void testConstants() throws Exception {
    runTest("constants.Constants");
  }

  @Test
  public void testControlFlow() throws Exception {
    runTest("controlflow.ControlFlow");
  }

  @Test
  public void testConversions() throws Exception {
    runTest("conversions.Conversions");
  }

  @Test
  public void testFloatingPointValuedAnnotation() throws Exception {
    runTest("floating_point_annotations.FloatingPointValuedAnnotationTest");
  }

  @Test
  public void testFilledArray() throws Exception {
    runTest("filledarray.FilledArray");
  }

  @Test
  public void testHello() throws Exception {
    runTest("hello.Hello");
  }

  @Test
  public void testIfStatements() throws Exception {
    runTest("ifstatements.IfStatements");
  }

  @Test
  public void testInstanceVariable() throws Exception {
    runTest("instancevariable.InstanceVariable");
  }

  @Test
  public void testInstanceofString() throws Exception {
    runTest("instanceofstring.InstanceofString");
  }

  @Test
  public void testInvoke() throws Exception {
    runTest("invoke.Invoke");
  }

  @Test
  public void testJumboString() throws Exception {
    runTest("jumbostring.JumboString");
  }

  @Test
  public void testLoadConst() throws Exception {
    runTest("loadconst.LoadConst");
  }

  @Test
  public void testUdpServer() throws Exception {
    runTest("loop.UdpServer");
  }

  @Test
  public void testNewArray() throws Exception {
    runTest("newarray.NewArray");
  }

  @Test
  public void testRegAlloc() throws Exception {
    runTest("regalloc.RegAlloc");
  }

  @Test
  public void testReturns() throws Exception {
    runTest("returns.Returns");
  }

  @Test
  public void testStaticField() throws Exception {
    runTest("staticfield.StaticField");
  }

  @Test
  public void testStringBuilding() throws Exception {
    runTest("stringbuilding.StringBuilding");
  }

  @Test
  public void testSwitches() throws Exception {
    runTest("switches.Switches");
  }

  @Test
  public void testSync() throws Exception {
    runTest("sync.Sync");
  }

  @Test
  public void testThrowing() throws Exception {
    runTest("throwing.Throwing");
  }

  @Test
  public void testTrivial() throws Exception {
    runTest("trivial.Trivial");
  }

  @Test
  public void testTryCatch() throws Exception {
    runTest("trycatch.TryCatch");
  }

  @Test
  public void testNestedTryCatches() throws Exception {
    runTest("nestedtrycatches.NestedTryCatches");
  }

  @Test
  public void testTryCatchMany() throws Exception {
    runTest("trycatchmany.TryCatchMany");
  }

  @Test
  public void testInvokeEmpty() throws Exception {
    runTest("invokeempty.InvokeEmpty");
  }

  @Test
  public void testRegress() throws Exception {
    runTest("regress.Regress");
  }

  @Test
  public void testRegress2() throws Exception {
    runTest("regress2.Regress2");
  }

  @Test
  public void testRegress37726195() throws Exception {
    runTest("regress_37726195.Regress");
  }

  @Test
  public void testRegress37658666() throws Exception {
    runTest(
        "regress_37658666.Regress",
        (expectedBytes, actualBytes) -> {
          // javac emits LDC(-0.0f) instead of the shorter FCONST_0 FNEG emitted by CfConstNumber.
          String ldc = "mv.visitLdcInsn(new Float(\"-0.0\"));";
          String constNeg = "mv.visitInsn(FCONST_0);\nmv.visitInsn(FNEG);";
          assertEquals(asmToString(expectedBytes).replace(ldc, constNeg), asmToString(actualBytes));
        });
  }

  @Test
  public void testRegress37875803() throws Exception {
    runTest("regress_37875803.Regress");
  }

  @Test
  public void testRegress37955340() throws Exception {
    runTest("regress_37955340.Regress");
  }

  @Test
  public void testRegress62300145() throws Exception {
    runTest("regress_62300145.Regress");
  }

  @Test
  public void testRegress64881691() throws Exception {
    runTest("regress_64881691.Regress");
  }

  @Test
  public void testRegress65104300() throws Exception {
    runTest("regress_65104300.Regress");
  }

  @Test
  public void testRegress70703087() throws Exception {
    runTest("regress_70703087.Test");
  }

  @Test
  public void testRegress70736958() throws Exception {
    runTest("regress_70736958.Test");
  }

  @Test
  public void testRegress70737019() throws Exception {
    runTest("regress_70737019.Test");
  }

  @Test
  public void testRegress72361252() throws Exception {
    runTest("regress_72361252.Test");
  }

  @Test
  public void testMemberrebinding2() throws Exception {
    runTest("memberrebinding2.Memberrebinding");
  }

  @Test
  public void testMemberrebinding3() throws Exception {
    runTest("memberrebinding3.Memberrebinding");
  }

  @Test
  public void testMinification() throws Exception {
    runTest("minification.Minification");
  }

  @Test
  public void testEnclosingmethod() throws Exception {
    runTest("enclosingmethod.Main");
  }

  @Test
  public void testEnclosingmethodProguarded() throws Exception {
    runTest("enclosingmethod_proguarded.Main");
  }

  @Test
  public void testInterfaceInlining() throws Exception {
    runTest("interfaceinlining.Main");
  }

  @Test
  public void testSwitchmaps() throws Exception {
    runTest("switchmaps.Switches");
  }

  private void runTest(String clazz) throws Exception {
    runTest(clazz, null);
  }

  private void runTest(String clazz, BiConsumer<byte[], byte[]> comparator) throws Exception {
    String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
    String suffix = "_debuginfo_all";
    Path inputJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, pkg + suffix + JAR_EXTENSION);
    Path outputJar = temp.getRoot().toPath().resolve("output.jar");
    R8Command command =
        R8Command.builder()
            .addProgramFiles(inputJar)
            .setMode(CompilationMode.DEBUG)
            .setOutput(outputJar, OutputMode.ClassFile)
            .build();
    ToolHelper.runR8(
        command,
        options -> {
          options.skipIR = true;
          options.enableCfFrontend = true;
        });
    ArchiveClassFileProvider expected = new ArchiveClassFileProvider(inputJar);
    ArchiveClassFileProvider actual = new ArchiveClassFileProvider(outputJar);
    assertEquals(getSortedDescriptorList(expected), getSortedDescriptorList(actual));
    for (String descriptor : expected.getClassDescriptors()) {
      byte[] expectedBytes = getClassAsBytes(expected, descriptor);
      byte[] actualBytes = getClassAsBytes(actual, descriptor);
      if (comparator != null) {
        comparator.accept(expectedBytes, actualBytes);
      } else if (!Arrays.equals(expectedBytes, actualBytes)) {
        assertEquals(
            "Class " + descriptor + " differs",
            asmToString(expectedBytes),
            asmToString(actualBytes));
      }
    }
  }

  private static List<String> getSortedDescriptorList(ArchiveClassFileProvider inputJar) {
    ArrayList<String> descriptorList = new ArrayList<>(inputJar.getClassDescriptors());
    Collections.sort(descriptorList);
    return descriptorList;
  }

  private static byte[] getClassAsBytes(ArchiveClassFileProvider inputJar, String descriptor)
      throws Exception {
    return toByteArray(inputJar.getProgramResource(descriptor).getByteStream());
  }

  private static String asmToString(byte[] clazz) {
    StringWriter stringWriter = new StringWriter();
    printAsm(new PrintWriter(stringWriter), clazz);
    return stringWriter.toString();
  }

  private static void printAsm(PrintWriter pw, byte[] clazz) {
    new ClassReader(clazz).accept(new TraceClassVisitor(null, new ASMifierSorted(), pw), 0);
  }

  /** Sort methods and fields in the output of ASMifier to make diffing possible. */
  private static class ASMifierSorted extends ASMifier {
    private static class Part implements Comparable<Part> {

      private final String key;
      private final int start;
      private final int end;

      Part(String key, int start, int end) {
        this.key = key;
        this.start = start;
        this.end = end;
      }

      @Override
      public int compareTo(Part part) {
        int i = key.compareTo(part.key);
        return i != 0 ? i : Integer.compare(start, part.start);
      }
    }

    private final List<Part> parts = new ArrayList<>();

    ASMifierSorted() {
      super(Opcodes.ASM6, "cw", 0);
    }

    @Override
    public ASMifier visitField(
        int access, String name, String desc, String signature, Object value) {
      init();
      int i = text.size();
      ASMifier res = super.visitField(access, name, desc, signature, value);
      parts.add(new Part((String) text.get(i), i, text.size()));
      return res;
    }

    @Override
    public ASMifier visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      init();
      int i = text.size();
      ASMifier res = super.visitMethod(access, name, desc, signature, exceptions);
      parts.add(new Part((String) text.get(i), i, text.size()));
      return res;
    }

    private void init() {
      if (parts.isEmpty()) {
        parts.add(new Part("", 0, text.size()));
      }
    }

    @Override
    public void print(PrintWriter pw) {
      init();
      int end = parts.get(parts.size() - 1).end;
      Collections.sort(parts);
      parts.add(new Part("", end, text.size()));
      ArrayList<Object> tmp = new ArrayList<>(text);
      text.clear();
      for (Part part : parts) {
        for (int i = part.start; i < part.end; i++) {
          text.add(tmp.get(i));
        }
      }
      super.print(pw);
    }
  }
}
