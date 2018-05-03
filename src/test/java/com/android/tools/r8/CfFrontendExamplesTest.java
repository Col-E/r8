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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;

@RunWith(Parameterized.class)
public class CfFrontendExamplesTest extends TestBase {

  static final Collection<Object[]> TESTS = Arrays.asList(
    makeTest("arithmetic.Arithmetic"),
    makeTest("arrayaccess.ArrayAccess"),
    makeTest("barray.BArray"),
    makeTest("bridge.BridgeMethod"),
    makeTest("cse.CommonSubexpressionElimination"),
    makeTest("constants.Constants"),
    makeTest("controlflow.ControlFlow"),
    makeTest("conversions.Conversions"),
    makeTest("floating_point_annotations.FloatingPointValuedAnnotationTest"),
    makeTest("filledarray.FilledArray"),
    makeTest("hello.Hello"),
    makeTest("ifstatements.IfStatements"),
    makeTest("instancevariable.InstanceVariable"),
    makeTest("instanceofstring.InstanceofString"),
    makeTest("invoke.Invoke"),
    makeTest("jumbostring.JumboString"),
    makeTest("loadconst.LoadConst"),
    makeTest("loop.UdpServer"),
    makeTest("newarray.NewArray"),
    makeTest("regalloc.RegAlloc"),
    makeTest("returns.Returns"),
    makeTest("staticfield.StaticField"),
    makeTest("stringbuilding.StringBuilding"),
    makeTest("switches.Switches"),
    makeTest("sync.Sync"),
    makeTest("throwing.Throwing"),
    makeTest("trivial.Trivial"),
    makeTest("trycatch.TryCatch"),
    makeTest("nestedtrycatches.NestedTryCatches"),
    makeTest("trycatchmany.TryCatchMany"),
    makeTest("invokeempty.InvokeEmpty"),
    makeTest("regress.Regress"),
    makeTest("regress2.Regress2"),
    makeTest("regress_37726195.Regress"),
    makeTest("regress_37658666.Regress", CfFrontendExamplesTest::compareRegress37658666),
    makeTest("regress_37875803.Regress"),
    makeTest("regress_37955340.Regress"),
    makeTest("regress_62300145.Regress"),
    makeTest("regress_64881691.Regress"),
    makeTest("regress_65104300.Regress"),
    makeTest("regress_70703087.Test"),
    makeTest("regress_70736958.Test"),
    makeTest("regress_70737019.Test"),
    makeTest("regress_72361252.Test"),
    makeTest("memberrebinding2.Memberrebinding"),
    makeTest("memberrebinding3.Memberrebinding"),
    makeTest("minification.Minification"),
    makeTest("enclosingmethod.Main"),
    makeTest("enclosingmethod_proguarded.Main"),
    makeTest("interfaceinlining.Main"),
    makeTest("switchmaps.Switches")
  );

  private static Object[] makeTest(String className) {
    return makeTest(className, null);
  }

  private static Object[] makeTest(String className, BiConsumer<byte[], byte[]> comparator) {
    return new Object[] {className, comparator};
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return TESTS;
  }

  private static void compareRegress37658666(byte[] expectedBytes, byte[] actualBytes) {
    // javac emits LDC(-0.0f) instead of the shorter FCONST_0 FNEG emitted by CfConstNumber.
    String ldc = "mv.visitLdcInsn(new Float(\"-0.0\"));";
    String constNeg = "mv.visitInsn(FCONST_0);\nmv.visitInsn(FNEG);";
    assertEquals(
        asmToString(expectedBytes).replace(ldc, constNeg),
        asmToString(actualBytes));
  }

  private final Path inputJar;
  private final BiConsumer<byte[], byte[]> comparator;

  public CfFrontendExamplesTest(String clazz, BiConsumer<byte[], byte[]> comparator) {
    this.comparator = comparator;
    String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
    String suffix = "_debuginfo_all";
    inputJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, pkg + suffix + JAR_EXTENSION);
  }

  @Test
  public void test() throws Exception {
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
