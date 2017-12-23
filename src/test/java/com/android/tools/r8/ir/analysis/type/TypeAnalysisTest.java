// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Smali;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RunWith(Parameterized.class)
public class TypeAnalysisTest extends SmaliTestBase {
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();
  private static final TypeLatticeElement PRIMITIVE = PrimitiveTypeLatticeElement.getInstance();

  private final String dirName;
  private final String smaliFileName;
  private final Consumer<AppInfoWithSubtyping> inspection;

  public TypeAnalysisTest(String test, Consumer<AppInfoWithSubtyping> inspection) {
    dirName = test.substring(0, test.lastIndexOf('/'));
    smaliFileName = test.substring(test.lastIndexOf('/') + 1) + ".smali";
    this.inspection = inspection;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() throws Exception {
    List<String> tests = Arrays.asList(
        "arithmetic/Arithmetic",
        "fibonacci/Fibonacci",
        "fill-array-data/FillArrayData",
        "filled-new-array/FilledNewArray",
        "infinite-loop/InfiniteLoop1",
        "try-catch/TryCatch",
        "type-confusion-regression/TestObject",
        "type-confusion-regression5/TestObject"
    );

    Map<String, Consumer<AppInfoWithSubtyping>> inspections = new HashMap<>();
    inspections.put("arithmetic/Arithmetic", TypeAnalysisTest::arithmetic);
    inspections.put("fibonacci/Fibonacci", TypeAnalysisTest::fibonacci);
    inspections.put("fill-array-data/FillArrayData", TypeAnalysisTest::fillArrayData);
    inspections.put("filled-new-array/FilledNewArray", TypeAnalysisTest::filledNewArray);
    inspections.put("infinite-loop/InfiniteLoop1", TypeAnalysisTest::infiniteLoop);
    inspections.put("try-catch/TryCatch", TypeAnalysisTest::tryCatch);
    inspections.put("type-confusion-regression/TestObject", TypeAnalysisTest::typeConfusion);
    inspections.put("type-confusion-regression5/TestObject", TypeAnalysisTest::typeConfusion5);

    List<Object[]> testCases = new ArrayList<>();
    for (String test : tests) {
      Consumer<AppInfoWithSubtyping> inspection = inspections.get(test);
      testCases.add(new Object[]{test, inspection});
    }
    return testCases;
  }

  @Test
  public void typeAnalysisTest() throws Exception {
    Path smaliPath = Paths.get(ToolHelper.SMALI_DIR, dirName, smaliFileName);
    StringBuilder smaliStringBuilder = new StringBuilder();
    Files.lines(smaliPath, StandardCharsets.UTF_8)
        .forEach(s -> smaliStringBuilder.append(s).append(System.lineSeparator()));
    byte[] content = Smali.compile(smaliStringBuilder.toString());
    AndroidApp app = AndroidApp.builder().addDexProgramData(content, Origin.unknown()).build();
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, new Timing("TypeAnalysisTest.appReader"))
            .read().toDirect();
    inspection.accept(new AppInfoWithSubtyping(dexApplication));
  }

  // Simple one path with a lot of arithmetic operations.
  private static void arithmetic(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod subtract =
        inspector.clazz("Test")
            .method(
                new MethodSignature("subtractConstants8bitRegisters", "int", ImmutableList.of()))
            .getMethod();
    try {
      IRCode irCode = subtract.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, subtract, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        assertEquals(l, PRIMITIVE);
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // A couple branches, along with some recursive calls.
  private static void fibonacci(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod fib =
        inspector.clazz("Test")
            .method(new MethodSignature("fibonacci", "int", ImmutableList.of("int")))
            .getMethod();
    try {
      IRCode irCode = fib.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, fib, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        assertEquals(l, PRIMITIVE);
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // fill-array-data
  private static void fillArrayData(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod test1 =
        inspector.clazz("Test")
            .method(new MethodSignature("test1", "int[]", ImmutableList.of()))
            .getMethod();
    try {
      IRCode irCode = test1.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, test1, irCode);
      analysis.run();
      Value array = null;
      InstructionIterator iterator = irCode.instructionIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instruction instanceof NewArrayEmpty) {
          array = instruction.outValue();
          break;
        }
      }
      assertNotNull(array);
      final Value finalArray = array;
      analysis.forEach((v, l) -> {
        if (v == finalArray) {
          assertTrue(l instanceof PrimitiveArrayTypeLatticeElement);
          PrimitiveArrayTypeLatticeElement lattice = (PrimitiveArrayTypeLatticeElement) l;
          assertEquals(1, lattice.nesting);
          assertFalse(l.isNullable());
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // filled-new-array
  private static void filledNewArray(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod test4 =
        inspector.clazz("Test")
            .method(new MethodSignature("test4", "int[]", ImmutableList.of()))
            .getMethod();
    try {
      IRCode irCode = test4.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, test4, irCode);
      analysis.run();
      Value array = null;
      InstructionIterator iterator = irCode.instructionIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instruction instanceof InvokeNewArray) {
          array = instruction.outValue();
          break;
        }
      }
      assertNotNull(array);
      final Value finalArray = array;
      analysis.forEach((v, l) -> {
        if (v == finalArray) {
          assertTrue(l instanceof PrimitiveArrayTypeLatticeElement);
          PrimitiveArrayTypeLatticeElement lattice = (PrimitiveArrayTypeLatticeElement) l;
          assertEquals(1, lattice.nesting);
          assertFalse(l.isNullable());
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // Make sure the analysis does not hang.
  private static void infiniteLoop(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod loop2 =
        inspector.clazz("Test")
            .method(new MethodSignature("loop2", "void", ImmutableList.of()))
            .getMethod();
    try {
      IRCode irCode = loop2.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, loop2, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        if (l instanceof ClassTypeLatticeElement) {
          ClassTypeLatticeElement lattice = (ClassTypeLatticeElement) l;
          assertEquals("Ljava/io/PrintStream;", lattice.classType.toDescriptorString());
          // TODO(b/70795205): Can be refined by using control-flow info.
          assertTrue(l.isNullable());
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // move-exception
  private static void tryCatch(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod test2 =
        inspector.clazz("Test")
            .method(new MethodSignature("test2_throw", "int", ImmutableList.of()))
            .getMethod();
    try {
      IRCode irCode = test2.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, test2, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        if (l instanceof ClassTypeLatticeElement) {
          ClassTypeLatticeElement lattice = (ClassTypeLatticeElement) l;
          assertEquals("Ljava/lang/Throwable;", lattice.classType.toDescriptorString());
          assertFalse(l.isNullable());
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // One very complicated example.
  private static void typeConfusion(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod method =
        inspector.clazz("TestObject")
            .method(
                new MethodSignature("a", "void",
                    ImmutableList.of("Test", "Test", "Test", "Test")))
            .getMethod();

    DexType test = appInfo.dexItemFactory.createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = Maps.newHashMap();
    expectedLattices.put(ArrayLength.class, PRIMITIVE);
    expectedLattices.put(ConstNumber.class, PRIMITIVE);
    expectedLattices.put(
        ConstString.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false));
    expectedLattices.put(CheckCast.class, new ClassTypeLatticeElement(test, true));
    expectedLattices.put(NewInstance.class, new ClassTypeLatticeElement(test, false));

    try {
      IRCode irCode = method.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, method, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        if (v.definition == null) {
          return;
        }
        TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
        if (expected != null) {
          assertEquals(expected, l);
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }

  // One more complicated example.
  private static void typeConfusion5(AppInfoWithSubtyping appInfo) {
    DexInspector inspector = new DexInspector(appInfo.app);
    DexEncodedMethod method =
        inspector.clazz("TestObject")
            .method(
                new MethodSignature("onClick", "void", ImmutableList.of("Test")))
            .getMethod();

    DexType test = appInfo.dexItemFactory.createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = Maps.newHashMap();
    expectedLattices.put(ConstNumber.class, PRIMITIVE);
    expectedLattices.put(
        ConstString.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false));
    expectedLattices.put(InstanceOf.class, PRIMITIVE);
    expectedLattices.put(StaticGet.class, new ClassTypeLatticeElement(test, true));

    try {
      IRCode irCode = method.buildIR(TEST_OPTIONS);
      TypeAnalysis analysis = new TypeAnalysis(appInfo, method, irCode);
      analysis.run();
      analysis.forEach((v, l) -> {
        if (v.definition == null) {
          return;
        }
        TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
        if (expected != null) {
          assertEquals(expected, l);
        }
      });
    } catch (ApiLevelException e) {
      fail(e.getMessage());
    }
  }
}
