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
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
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
  private static final TypeElement NULL = TypeElement.getNull();
  private static final TypeElement SINGLE = TypeElement.getSingle();
  private static final TypeElement INT = TypeElement.getInt();
  private static final TypeElement LONG = TypeElement.getLong();

  private final String dirName;
  private final String smaliFileName;
  private final BiConsumer<AppView<?>, CodeInspector> inspection;

  public TypeAnalysisTest(String test, BiConsumer<AppView<?>, CodeInspector> inspection) {
    dirName = test.substring(0, test.lastIndexOf('/'));
    smaliFileName = test.substring(test.lastIndexOf('/') + 1) + ".dex";
    this.inspection = inspection;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    List<String> tests =
        Arrays.asList(
            "arithmetic/arithmetic",
            "fibonacci/fibonacci",
            "fill-array-data/fill-array-data",
            "filled-new-array/filled-new-array",
            "infinite-loop/infinite-loop",
            "try-catch/try-catch",
            "type-confusion-regression/type-confusion-regression",
            "type-confusion-regression5/type-confusion-regression5");

    Map<String, BiConsumer<AppView<?>, CodeInspector>> inspections = new HashMap<>();
    inspections.put("arithmetic/arithmetic", TypeAnalysisTest::arithmetic);
    inspections.put("fibonacci/fibonacci", TypeAnalysisTest::fibonacci);
    inspections.put("fill-array-data/fill-array-data", TypeAnalysisTest::fillArrayData);
    inspections.put("filled-new-array/filled-new-array", TypeAnalysisTest::filledNewArray);
    inspections.put("infinite-loop/infinite-loop", TypeAnalysisTest::infiniteLoop);
    inspections.put("try-catch/try-catch", TypeAnalysisTest::tryCatch);
    inspections.put(
        "type-confusion-regression/type-confusion-regression", TypeAnalysisTest::typeConfusion);
    inspections.put(
        "type-confusion-regression5/type-confusion-regression5", TypeAnalysisTest::typeConfusion5);

    List<Object[]> testCases = new ArrayList<>();
    for (String test : tests) {
      BiConsumer<AppView<?>, CodeInspector> inspection = inspections.get(test);
      testCases.add(new Object[]{test, inspection});
    }
    return testCases;
  }

  @Test
  public void typeAnalysisTest() throws Exception {
    byte[] content =
        Files.readAllBytes(Paths.get(ToolHelper.SMALI_BUILD_DIR, dirName, smaliFileName));
    AndroidApp app = AndroidApp.builder().addDexProgramData(content, Origin.unknown()).build();
    AppView<AppInfo> appView = computeAppView(app);
    inspection.accept(appView, new CodeInspector(appView.appInfo().app()));
  }

  private static void forEachOutValue(IRCode irCode, BiConsumer<Value, TypeElement> consumer) {
    irCode
        .instructionIterator()
        .forEachRemaining(
            instruction -> {
              Value outValue = instruction.outValue();
              if (outValue != null) {
                TypeElement element = outValue.getType();
                consumer.accept(outValue, element);
              }
            });
  }

  // Simple one path with a lot of arithmetic operations.
  private static void arithmetic(AppView<?> appView, CodeInspector inspector) {
    MethodSubject subtractSubject =
        inspector
            .clazz("Test")
            .method(
                new MethodSignature("subtractConstants8bitRegisters", "int", ImmutableList.of()));
    IRCode irCode = subtractSubject.buildIR();
    forEachOutValue(irCode, (v, l) -> {
      // v9 <- 9 (INT_OR_FLOAT), which is never used later, hence imprecise.
      assertEither(l, SINGLE, INT, NULL);
    });
  }

  // A couple branches, along with some recursive calls.
  private static void fibonacci(AppView<?> appView, CodeInspector inspector) {
    MethodSubject fibSubject =
        inspector
            .clazz("Test")
            .method(new MethodSignature("fibonacci", "int", ImmutableList.of("int")));
    IRCode irCode = fibSubject.buildIR();
    forEachOutValue(irCode, (v, l) -> assertEither(l, INT, NULL));
  }

  // fill-array-data
  private static void fillArrayData(AppView<?> appView, CodeInspector inspector) {
    MethodSubject test1Subject =
        inspector.clazz("Test").method(new MethodSignature("test1", "int[]", ImmutableList.of()));
    IRCode code = test1Subject.buildIR();
    Value array = null;
    for (Instruction instruction : code.instructions()) {
      if (instruction instanceof NewArrayEmpty) {
        array = instruction.outValue();
        break;
      }
    }
    assertNotNull(array);
    final Value finalArray = array;
    forEachOutValue(
        code,
        (v, l) -> {
          if (v == finalArray) {
            assertTrue(l.isArrayType());
            ArrayTypeElement lattice = l.asArrayType();
            assertTrue(lattice.getMemberType().isPrimitiveType());
            assertEquals(1, lattice.getNesting());
            assertFalse(lattice.isNullable());
          }
        });
  }

  // filled-new-array
  private static void filledNewArray(AppView<?> appView, CodeInspector inspector) {
    MethodSubject test4Subject =
        inspector.clazz("Test").method(new MethodSignature("test4", "int[]", ImmutableList.of()));
    IRCode code = test4Subject.buildIR();
    Value array = null;
    for (Instruction instruction : code.instructions()) {
      if (instruction instanceof NewArrayFilled) {
        array = instruction.outValue();
        break;
      }
    }
    assertNotNull(array);
    final Value finalArray = array;
    forEachOutValue(
        code,
        (v, l) -> {
          if (v == finalArray) {
            assertTrue(l.isArrayType());
            ArrayTypeElement lattice = l.asArrayType();
            assertTrue(lattice.getMemberType().isPrimitiveType());
            assertEquals(1, lattice.getNesting());
            assertFalse(lattice.isNullable());
          }
        });
  }

  // Make sure the analysis does not hang.
  private static void infiniteLoop(AppView<?> appView, CodeInspector inspector) {
    MethodSubject loop2Subject =
        inspector.clazz("Test").method(new MethodSignature("loop2", "void", ImmutableList.of()));
    IRCode irCode = loop2Subject.buildIR();
    forEachOutValue(
        irCode,
        (v, l) -> {
          if (l.isClassType()) {
            ClassTypeElement lattice = l.asClassType();
            assertEquals("Ljava/io/PrintStream;", lattice.getClassType().toDescriptorString());
            // TODO(b/70795205): Can be refined by using control-flow info.
            assertTrue(l.isNullable());
          }
        });
  }

  // move-exception
  private static void tryCatch(AppView<?> appView, CodeInspector inspector) {
    MethodSubject test2Subject =
        inspector
            .clazz("Test")
            .method(new MethodSignature("test2_throw", "int", ImmutableList.of()));
    IRCode irCode = test2Subject.buildIR();
    forEachOutValue(
        irCode,
        (v, l) -> {
          if (l.isClassType()) {
            ClassTypeElement lattice = l.asClassType();
            assertEquals("Ljava/lang/Throwable;", lattice.getClassType().toDescriptorString());
            assertFalse(l.isNullable());
          }
        });
  }

  // One very complicated example.
  private static void typeConfusion(AppView<?> appView, CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector
            .clazz("TestObject")
            .method(
                new MethodSignature("a", "void", ImmutableList.of("Test", "Test", "Test", "Test")));
    DexType test = appView.dexItemFactory().createType("LTest;");
    Map<Class<? extends Instruction>, TypeElement> expectedLattices =
        ImmutableMap.of(
            ArrayLength.class, INT,
            ConstString.class, TypeElement.stringClassType(appView, definitelyNotNull()),
            CheckCast.class, TypeElement.fromDexType(test, maybeNull(), appView),
            NewInstance.class, TypeElement.fromDexType(test, definitelyNotNull(), appView));
    IRCode irCode = methodSubject.buildIR();
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
  private static void typeConfusion5(AppView<?> appView, CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector
            .clazz("TestObject")
            .method(new MethodSignature("onClick", "void", ImmutableList.of("Test")));
    DexType test = appView.dexItemFactory().createType("LTest;");
    Map<Class<? extends Instruction>, TypeElement> expectedLattices =
        ImmutableMap.of(
            ConstString.class, TypeElement.stringClassType(appView, definitelyNotNull()),
            InstanceOf.class, INT,
            StaticGet.class, TypeElement.fromDexType(test, maybeNull(), appView));
    IRCode irCode = methodSubject.buildIR();
    forEachOutValue(irCode, (v, l) -> verifyTypeEnvironment(expectedLattices, v, l));
  }

  private static void assertEither(TypeElement actual, TypeElement... expected) {
    assertTrue(Arrays.stream(expected).anyMatch(e -> e == actual));
  }

  private static void verifyTypeEnvironment(
      Map<Class<? extends Instruction>, TypeElement> expectedLattices, Value v, TypeElement l) {
    if (v.definition == null) {
      return;
    }
    TypeElement expected = expectedLattices.get(v.definition.getClass());
    if (expected != null) {
      assertEquals(expected, l);
    }
  }
}
