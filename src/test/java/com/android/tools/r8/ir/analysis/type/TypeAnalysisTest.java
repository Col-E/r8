// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.utils.codeinspector.MethodSubject;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypeAnalysisTest extends SmaliTestBase {
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();
  private static final TypeLatticeElement NULL = TypeLatticeElement.NULL;
  private static final TypeLatticeElement SINGLE = TypeLatticeElement.SINGLE;
  private static final TypeLatticeElement INT = TypeLatticeElement.INT;
  private static final TypeLatticeElement LONG = TypeLatticeElement.LONG;

  private final String dirName;
  private final String smaliFileName;
  private final BiConsumer<AppView<? extends AppInfo>, CodeInspector> inspection;

  public TypeAnalysisTest(
      String test, BiConsumer<AppView<? extends AppInfo>, CodeInspector> inspection) {
    dirName = test.substring(0, test.lastIndexOf('/'));
    smaliFileName = test.substring(test.lastIndexOf('/') + 1) + ".smali";
    this.inspection = inspection;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
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

    Map<String, BiConsumer<AppView<? extends AppInfo>, CodeInspector>> inspections =
        new HashMap<>();
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
      BiConsumer<AppView<? extends AppInfo>, CodeInspector> inspection = inspections.get(test);
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
    inspection.accept(
        AppView.createForR8(new AppInfoWithSubtyping(dexApplication), TEST_OPTIONS),
        new CodeInspector(dexApplication));
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
  private static void arithmetic(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject subtractSubject =
        inspector
            .clazz("Test")
            .method(
                new MethodSignature("subtractConstants8bitRegisters", "int", ImmutableList.of()));
    DexEncodedMethod subtract = subtractSubject.getMethod();
    IRCode irCode = subtractSubject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, subtract);
    analysis.widening(subtract, irCode);
    forEachOutValue(irCode, (v, l) -> {
      // v9 <- 9 (INT_OR_FLOAT), which is never used later, hence imprecise.
      assertEither(l, SINGLE, INT, NULL);
    });
  }

  // A couple branches, along with some recursive calls.
  private static void fibonacci(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject fibSubject =
        inspector
            .clazz("Test")
            .method(new MethodSignature("fibonacci", "int", ImmutableList.of("int")));
    DexEncodedMethod fib = fibSubject.getMethod();
    IRCode irCode = fibSubject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, fib);
    analysis.widening(fib, irCode);
    forEachOutValue(irCode, (v, l) -> assertEither(l, INT, NULL));
  }

  // fill-array-data
  private static void fillArrayData(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject test1Subject =
        inspector.clazz("Test").method(new MethodSignature("test1", "int[]", ImmutableList.of()));
    DexEncodedMethod test1 = test1Subject.getMethod();
    IRCode irCode = test1Subject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, test1);
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
        assertTrue(lattice.getArrayMemberTypeAsMemberType().isPrimitive());
        assertEquals(1, lattice.getNesting());
        assertFalse(lattice.isNullable());
      }
    });
  }

  // filled-new-array
  private static void filledNewArray(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject test4Subject =
        inspector.clazz("Test").method(new MethodSignature("test4", "int[]", ImmutableList.of()));
    DexEncodedMethod test4 = test4Subject.getMethod();
    IRCode irCode = test4Subject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, test4);
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
        assertTrue(lattice.getArrayMemberTypeAsMemberType().isPrimitive());
        assertEquals(1, lattice.getNesting());
        assertFalse(lattice.isNullable());
      }
    });
  }

  // Make sure the analysis does not hang.
  private static void infiniteLoop(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject loop2Subject =
        inspector.clazz("Test").method(new MethodSignature("loop2", "void", ImmutableList.of()));
    DexEncodedMethod loop2 = loop2Subject.getMethod();
    IRCode irCode = loop2Subject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, loop2);
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
  private static void tryCatch(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject test2Subject =
        inspector
            .clazz("Test")
            .method(new MethodSignature("test2_throw", "int", ImmutableList.of()));
    DexEncodedMethod test2 = test2Subject.getMethod();
    IRCode irCode = test2Subject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, test2);
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
  private static void typeConfusion(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector
            .clazz("TestObject")
            .method(
                new MethodSignature("a", "void", ImmutableList.of("Test", "Test", "Test", "Test")));
    DexEncodedMethod method = methodSubject.getMethod();
    DexType test = appView.dexItemFactory().createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices =
        ImmutableMap.of(
            ArrayLength.class, INT,
            ConstString.class, TypeLatticeElement.stringClassType(appView, definitelyNotNull()),
            CheckCast.class, TypeLatticeElement.fromDexType(test, maybeNull(), appView),
            NewInstance.class, TypeLatticeElement.fromDexType(test, definitelyNotNull(), appView));
    IRCode irCode = methodSubject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, method);
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
  private static void typeConfusion5(AppView<? extends AppInfo> appView, CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector
            .clazz("TestObject")
            .method(new MethodSignature("onClick", "void", ImmutableList.of("Test")));
    DexEncodedMethod method = methodSubject.getMethod();
    DexType test = appView.dexItemFactory().createType("LTest;");
    Map<Class<? extends Instruction>, TypeLatticeElement> expectedLattices =
        ImmutableMap.of(
            ConstString.class, TypeLatticeElement.stringClassType(appView, definitelyNotNull()),
            InstanceOf.class, INT,
            StaticGet.class, TypeLatticeElement.fromDexType(test, maybeNull(), appView));
    IRCode irCode = methodSubject.buildIR();
    TypeAnalysis analysis = new TypeAnalysis(appView, method);
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
