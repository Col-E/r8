// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Smali;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeAnalysisTest extends SmaliTestBase {
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();
  private static final TypeLatticeElement NULL =
      ReferenceTypeLatticeElement.getNullTypeLatticeElement();
  private static final TypeLatticeElement SINGLE = SingleTypeLatticeElement.getInstance();
  private static final TypeLatticeElement INT = IntTypeLatticeElement.getInstance();
  private static final TypeLatticeElement LONG = LongTypeLatticeElement.getInstance();

  private final String dirName;
  private final String smaliFileName;
  private final Consumer<AppInfo> inspection;

  public TypeAnalysisTest(String test, Consumer<AppInfo> inspection) {
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

    Map<String, Consumer<AppInfo>> inspections = new HashMap<>();
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
      Consumer<AppInfo> inspection = inspections.get(test);
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
    inspection.accept(new AppInfo(dexApplication));
  }

  private static void forEachOutValue(
      IRCode irCode, BiConsumer<Value, TypeLatticeElement> consumer) {
    irCode.instructionIterator().forEachRemaining(instruction -> {
      Value outValue = instruction.outValue();
      if (outValue != null) {
        TypeLatticeElement element = outValue.getTypeLattice();
        consumer.accept(outValue, element);
      }
    });
  }

  // Simple one path with a lot of arithmetic operations.
  private static void arithmetic(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod subtract =
        inspector.clazz("Test")
            .method(
                new MethodSignature("subtractConstants8bitRegisters", "int", ImmutableList.of()))
            .getMethod();
    IRCode irCode =
        subtract.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, subtract);
    analysis.widening(subtract, irCode);
    forEachOutValue(irCode, (v, l) -> {
      // v9 <- 9 (INT_OR_FLOAT), which is never used later, hence imprecise.
      assertEither(l, SINGLE, INT, NULL);
    });
  }

  // A couple branches, along with some recursive calls.
  private static void fibonacci(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod fib =
        inspector.clazz("Test")
            .method(new MethodSignature("fibonacci", "int", ImmutableList.of("int")))
            .getMethod();
    IRCode irCode =
        fib.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, fib);
    analysis.widening(fib, irCode);
    forEachOutValue(irCode, (v, l) -> {
      assertEither(l, INT, NULL);
    });
  }

  // fill-array-data
  private static void fillArrayData(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod test1 =
        inspector.clazz("Test")
            .method(new MethodSignature("test1", "int[]", ImmutableList.of()))
            .getMethod();
    IRCode irCode =
        test1.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, test1);
    analysis.widening(test1, irCode);
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
    forEachOutValue(irCode, (v, l) -> {
      if (v == finalArray) {
        assertTrue(l.isArrayType());
        ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
        assertTrue(lattice.getArrayType().isPrimitiveArrayType());
        assertEquals(1, lattice.getNesting());
        assertFalse(lattice.isNullable());
      }
    });
  }

  // filled-new-array
  private static void filledNewArray(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod test4 =
        inspector.clazz("Test")
            .method(new MethodSignature("test4", "int[]", ImmutableList.of()))
            .getMethod();
    IRCode irCode =
        test4.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, test4);
    analysis.widening(test4, irCode);
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
    forEachOutValue(irCode, (v, l) -> {
      if (v == finalArray) {
        assertTrue(l.isArrayType());
        ArrayTypeLatticeElement lattice = l.asArrayTypeLatticeElement();
        assertTrue(lattice.getArrayType().isPrimitiveArrayType());
        assertEquals(1, lattice.getNesting());
        assertFalse(lattice.isNullable());
      }
    });
  }

  // Make sure the analysis does not hang.
  private static void infiniteLoop(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod loop2 =
        inspector.clazz("Test")
            .method(new MethodSignature("loop2", "void", ImmutableList.of()))
            .getMethod();
    IRCode irCode =
        loop2.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, loop2);
    analysis.widening(loop2, irCode);
    forEachOutValue(irCode, (v, l) -> {
      if (l.isClassType()) {
        ClassTypeLatticeElement lattice = l.asClassTypeLatticeElement();
        assertEquals("Ljava/io/PrintStream;", lattice.getClassType().toDescriptorString());
        // TODO(b/70795205): Can be refined by using control-flow info.
        assertTrue(l.isNullable());
      }
    });
  }

  // move-exception
  private static void tryCatch(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod test2 =
        inspector.clazz("Test")
            .method(new MethodSignature("test2_throw", "int", ImmutableList.of()))
            .getMethod();
    IRCode irCode =
        test2.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, test2);
    analysis.widening(test2, irCode);
    forEachOutValue(irCode, (v, l) -> {
      if (l.isClassType()) {
        ClassTypeLatticeElement lattice = l.asClassTypeLatticeElement();
        assertEquals("Ljava/lang/Throwable;", lattice.getClassType().toDescriptorString());
        assertFalse(l.isNullable());
      }
    });
  }

  // One very complicated example.
  private static void typeConfusion(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod method =
        inspector.clazz("TestObject")
            .method(
                new MethodSignature("a", "void",
                    ImmutableList.of("Test", "Test", "Test", "Test")))
            .getMethod();
    DexType test = appInfo.dexItemFactory.createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
        ArrayLength.class, INT,
        ConstString.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false),
        CheckCast.class, new ClassTypeLatticeElement(test, true),
        NewInstance.class, new ClassTypeLatticeElement(test, false));
    IRCode irCode =
        method.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, method);
    analysis.widening(method, irCode);
    forEachOutValue(irCode, (v, l) -> {
      verifyTypeEnvironment(expectedLattices, v, l);
      // There are double-to-int, long-to-int, and int-to-long conversions in this example.
      if (v.definition != null && v.definition.isNumberConversion()) {
        NumberConversion instr = v.definition.asNumberConversion();
        if (instr.to.isWide()) {
          assertEquals(LONG, l);
        } else {
          assertEquals(INT, l);
        }
      }
    });
  }

  // One more complicated example.
  private static void typeConfusion5(AppInfo appInfo) {
    CodeInspector inspector = new CodeInspector(appInfo.app);
    DexEncodedMethod method =
        inspector.clazz("TestObject")
            .method(
                new MethodSignature("onClick", "void", ImmutableList.of("Test")))
            .getMethod();
    DexType test = appInfo.dexItemFactory.createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices = ImmutableMap.of(
      ConstString.class, new ClassTypeLatticeElement(appInfo.dexItemFactory.stringType, false),
      InstanceOf.class, INT,
      StaticGet.class, new ClassTypeLatticeElement(test, true));
    IRCode irCode =
        method.buildIR(appInfo, GraphLense.getIdentityLense(), TEST_OPTIONS, Origin.unknown());
    TypeAnalysis analysis = new TypeAnalysis(appInfo, method);
    analysis.widening(method, irCode);
    forEachOutValue(irCode, (v, l) -> verifyTypeEnvironment(expectedLattices, v, l));
  }

  private static void assertEither(TypeLatticeElement actual, TypeLatticeElement... expected) {
    assertTrue(Arrays.stream(expected).anyMatch(e -> e == actual));
  }

  private static void verifyTypeEnvironment(
      Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices,
      Value v,
      TypeLatticeElement l) {
    if (v.definition == null) {
      return;
    }
    TypeLatticeElement expected = expectedLattices.get(v.definition.getClass());
    if (expected != null) {
      assertEquals(expected, l);
    }
  }
}
