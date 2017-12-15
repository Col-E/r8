// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.DivIntLit8;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.RemIntLit8;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.code.ReturnWide;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.code.WideConstant;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;

public class ConstantFoldingTest extends SmaliTestBase {

  @FunctionalInterface
  public interface TriConsumer<T, U, V> {
    public void accept(T t, U u, V v);
  }

  private class SmaliBuilderWithCheckers {
    List<Object> values = new ArrayList<>();
    List<BiConsumer<DexEncodedMethod, Object>> checkers = new ArrayList<>();

    private final SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    public void addTest(
        TriConsumer<SmaliBuilder, String, Object> methodBilder,
        BiConsumer<DexEncodedMethod, Object> methodChecker,
        Object value) {
      methodBilder.accept(builder, "m" + values.size(), value);
      values.add(value);
      checkers.add(methodChecker);
    }

    public void run() throws Exception {
      AndroidApp processdApplication = processApplication(buildApplication(builder));
      assertEquals(1, getNumberOfProgramClasses(processdApplication));
      DexInspector inspector = new DexInspector(processdApplication);
      ClassSubject clazz = inspector.clazz(DEFAULT_CLASS_NAME);
      clazz.forAllMethods(method -> {
        int index = Integer.parseInt(method.getMethod().method.name.toString().substring(1));
        checkers.get(index).accept(method.getMethod(), values.get(index));
      });
    }
  }

  public class BinopTestData {
    public final String type;
    public final String op;
    public final List<Long> values;
    public final Long result;

    BinopTestData(String type, String op, List<Long> values, Long result) {
      this.type = type;
      this.op = op;
      this.values = values;
      this.result = result;
    }
  }

  private long floatBits(float f) {
    return Float.floatToIntBits(f);
  }

  private long doubleBits(double d) {
    return Double.doubleToLongBits(d);
  }

  private ImmutableList<Long> arguments = ImmutableList.of(1L, 2L, 3L, 4L);
  private ImmutableList<Long> floatArguments = ImmutableList.of(
      floatBits(1.0f), floatBits(2.0f), floatBits(3.0f), floatBits(4.0f));
  private ImmutableList<Long> doubleArguments = ImmutableList.of(
      doubleBits(1.0), doubleBits(2.0), doubleBits(3.0), doubleBits(4.0));

  private void binopMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    BinopTestData test = (BinopTestData) parameters;
    boolean wide = test.type.equals("long") || test.type.equals("double");
    StringBuilder source = new StringBuilder();
    int factor = wide ? 2 : 1;
    for (int i = 0; i < test.values.size(); i++) {
      source.append("    ");
      source.append(wide ? "const-wide " : "const ");
      source.append("v" + (i * factor));
      source.append(", ");
      source.append("0x" + Long.toHexString(test.values.get(i)));
      source.append(wide ? "L" : "");
      source.append("\n");
    }

    for (int i = 0; i < test.values.size() - 1; i++) {
      source.append("    ");
      source.append(test.op + "-" + test.type + "/2addr ");
      source.append("v" + ((i + 1) * factor));
      source.append(", ");
      source.append("v" + (i * factor));
      source.append("\n");
    }

    source.append("    ");
    source.append(wide ? "return-wide " : "return ");
    source.append("v" + ((test.values.size() - 1) * factor));

    builder.addStaticMethod(
        test.type, name, Collections.singletonList(test.type),
        test.values.size() * factor,
        source.toString());
  }

  private void binopMethodChecker(DexEncodedMethod method, Object parameters) {
    BinopTestData test = (BinopTestData) parameters;
    boolean wide = test.type.equals("long") || test.type.equals("double");
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    if (wide) {
      assertTrue(code.instructions[0] instanceof WideConstant);
      assertEquals(test.result.longValue(),
          ((WideConstant) code.instructions[0]).decodedValue());
      assertTrue(code.instructions[1] instanceof ReturnWide);
    } else {
      assertTrue(code.instructions[0] instanceof SingleConstant);
      assertEquals(
          test.result.longValue(),
          (long) ((SingleConstant) code.instructions[0]).decodedValue());
      assertTrue(code.instructions[1] instanceof Return);
    }
  }

  private void addBinopTest(SmaliBuilderWithCheckers testBuilder, BinopTestData test) {
    testBuilder.addTest(this::binopMethodBuilder, this::binopMethodChecker, test);
  }

  private void addBinopFoldingTests(SmaliBuilderWithCheckers testBuilder) {
    // Add tests.
    addBinopTest(testBuilder, new BinopTestData("int", "add", arguments, 10L));
    addBinopTest(testBuilder, new BinopTestData("long", "add", arguments, 10L));
    addBinopTest(testBuilder,
        new BinopTestData("float", "add", floatArguments, floatBits(10.0f)));
    addBinopTest(testBuilder,
        new BinopTestData("double", "add", doubleArguments, doubleBits(10.0)));

    // Mul tests.
    addBinopTest(testBuilder, new BinopTestData("int", "mul", arguments, 24L));
    addBinopTest(testBuilder, new BinopTestData("long", "mul", arguments, 24L));
    addBinopTest(testBuilder,
        new BinopTestData("float", "mul", floatArguments, floatBits(24.0f)));
    addBinopTest(testBuilder,
        new BinopTestData("double", "mul", doubleArguments, doubleBits(24.0)));

    // Sub tests.
    addBinopTest(testBuilder, new BinopTestData("int", "sub", arguments.reverse(), -2L));
    addBinopTest(testBuilder, new BinopTestData("long", "sub", arguments.reverse(), -2L));
    addBinopTest(testBuilder,
        new BinopTestData("float", "sub", floatArguments.reverse(), floatBits(-2.0f)));
    addBinopTest(testBuilder,
        new BinopTestData("double", "sub", doubleArguments.reverse(), doubleBits(-2.0)));

    // Div tests.
    {
      ImmutableList<Long> arguments = ImmutableList.of(2L, 24L, 48L, 4L);
      ImmutableList<Long> floatArguments = ImmutableList.of(
          floatBits(2.0f), floatBits(24.0f), floatBits(48.0f), floatBits(4.0f));
      ImmutableList<Long> doubleArguments = ImmutableList.of(
          doubleBits(2.0), doubleBits(24.0), doubleBits(48.0), doubleBits(4.0));

      addBinopTest(testBuilder, new BinopTestData("int", "div", arguments, 1L));
      addBinopTest(testBuilder, new BinopTestData("long", "div", arguments, 1L));
      addBinopTest(testBuilder,
          new BinopTestData("float", "div", floatArguments, floatBits(1.0f)));
      addBinopTest(testBuilder,
          new BinopTestData("double", "div", doubleArguments, doubleBits(1.0)));
    }

    // Rem tests.
    {
      ImmutableList<Long> arguments = ImmutableList.of(10L, 6L, 3L, 2L);
      ImmutableList<Long> floatArguments = ImmutableList.of(
          floatBits(10.0f), floatBits(6.0f), floatBits(3.0f), floatBits(2.0f));
      ImmutableList<Long> doubleArguments = ImmutableList.of(
          doubleBits(10.0), doubleBits(6.0), doubleBits(3.0), doubleBits(2.0));

      addBinopTest(testBuilder, new BinopTestData("int", "rem", arguments, 2L));
      addBinopTest(testBuilder, new BinopTestData("long", "rem", arguments, 2L));
      addBinopTest(testBuilder,
          new BinopTestData("float", "rem", floatArguments, floatBits(2.0f)));
      addBinopTest(testBuilder,
          new BinopTestData("double", "rem", doubleArguments, doubleBits(2.0)));
    }
  }

  private void addDivIntFoldDivByZero(SmaliBuilderWithCheckers testBuilder) {
    testBuilder.addTest(
        (builder, name, parameters) -> {
          builder.addStaticMethod(
              "int", name, Collections.singletonList("int"),
              2,
              "    const/4 v0, 1           ",
              "    const/4 v1, 0           ",
              "    div-int/2addr v0, v1    ",
              "    return v0\n             "
          );
        },
        (method, parameters) -> {
          DexCode code = method.getCode().asDexCode();
          // Division by zero is not folded, but div-int/lit8 is used.
          assertEquals(3, code.instructions.length);
          assertTrue(code.instructions[0] instanceof Const4);
          assertTrue(code.instructions[1] instanceof DivIntLit8);
          assertEquals(0, ((DivIntLit8) code.instructions[1]).CC);
          assertTrue(code.instructions[2] instanceof Return);
        },
        null
    );
  }

  private void addDivIntFoldRemByZero(SmaliBuilderWithCheckers testBuilder) {
    testBuilder.addTest(
        (builder, name, parameters) -> {
          builder.addStaticMethod(
            "int", name, Collections.singletonList("int"),
            2,
            "    const/4 v0, 1           ",
            "    const/4 v1, 0           ",
            "    rem-int/2addr v0, v1    ",
            "    return v0\n             "
          );
        },
        (method, parameters) -> {
          DexCode code = method.getCode().asDexCode();
          // Division by zero is not folded, but rem-int/lit8 is used.
          assertEquals(3, code.instructions.length);
          assertTrue(code.instructions[0] instanceof Const4);
          assertTrue(code.instructions[1] instanceof RemIntLit8);
          assertEquals(0, ((RemIntLit8) code.instructions[1]).CC);
          assertTrue(code.instructions[2] instanceof Return);
        },
        null
    );
  }

  public class UnopTestData {
    public final String type;
    public final String op;
    public final Long value;
    public final Long result;

    UnopTestData(String type, String op, Long value, Long result) {
      this.type = type;
      this.op = op;
      this.value = value;
      this.result = result;
    }
  }

  private void unopMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    UnopTestData test = (UnopTestData) parameters;
    boolean wide = test.type.equals("long") || test.type.equals("double");
    StringBuilder source = new StringBuilder();
    source.append("    ");
    source.append(wide ? "const-wide " : "const ");
    source.append("v0 , ");
    source.append("0x" + Long.toHexString(test.value));
    source.append(wide ? "L" : "");
    source.append("\n");

    source.append("    ");
    source.append(test.op + "-" + test.type + " v0, v0\n");

    source.append("    ");
    source.append(wide ? "return-wide v0" : "return v0");

    builder.addStaticMethod(
        test.type, name, Collections.singletonList(test.type),
        wide ? 2 : 1,
        source.toString());
  }

  private void unopMethodChecker(DexEncodedMethod method, Object parameters) {
    UnopTestData test = (UnopTestData) parameters;
    boolean wide = test.type.equals("long") || test.type.equals("double");
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    if (wide) {
      assertTrue(code.instructions[0] instanceof WideConstant);
      assertEquals(test.result.longValue(), ((WideConstant) code.instructions[0]).decodedValue());
      assertTrue(code.instructions[1] instanceof ReturnWide);
    } else {
      assertTrue(code.instructions[0] instanceof SingleConstant);
      assertEquals(
          test.result.longValue(), (long) ((SingleConstant) code.instructions[0]).decodedValue());
      assertTrue(code.instructions[1] instanceof Return);
    }
  }

  private void addUnopTest(SmaliBuilderWithCheckers testBuilder, UnopTestData test) {
    testBuilder.addTest(this::unopMethodBuilder, this::unopMethodChecker, test);
  }

  private void addNegFoldingTest(SmaliBuilderWithCheckers testBuilder) throws Exception {
    addUnopTest(testBuilder, new UnopTestData("int", "neg", 2L, -2L));
    addUnopTest(testBuilder, new UnopTestData("int", "neg", -2L, 2L));
    addUnopTest(testBuilder, new UnopTestData("long", "neg", 2L, -2L));
    addUnopTest(testBuilder, new UnopTestData("long", "neg", -2L, 2L));
    addUnopTest(testBuilder, new UnopTestData("float", "neg", floatBits(2.0f), floatBits(-2.0f)));
    addUnopTest(testBuilder, new UnopTestData("float", "neg", floatBits(-2.0f), floatBits(2.0f)));
    addUnopTest(testBuilder, new UnopTestData("float", "neg", floatBits(0.0f), floatBits(-0.0f)));
    addUnopTest(testBuilder, new UnopTestData("float", "neg", floatBits(-0.0f), floatBits(0.0f)));
    addUnopTest(testBuilder, new UnopTestData("double", "neg", doubleBits(2.0), doubleBits(-2.0)));
    addUnopTest(testBuilder, new UnopTestData("double", "neg", doubleBits(-2.0), doubleBits(2.0)));
    addUnopTest(testBuilder, new UnopTestData("double", "neg", doubleBits(0.0), doubleBits(-0.0)));
    addUnopTest(testBuilder, new UnopTestData("double", "neg", doubleBits(-0.0), doubleBits(0.0)));
  }

  private void assertConstValue(int expected, Instruction insn) {
    assertTrue(insn instanceof SingleConstant);
    assertEquals(expected, ((SingleConstant) insn).decodedValue());
  }

  private void assertConstValue(long expected, Instruction insn) {
    assertTrue(insn instanceof WideConstant);
    assertEquals(expected, ((WideConstant) insn).decodedValue());
  }

  class LogicalOperatorTestData {
    final String op;
    final int[] values;
    final int expected;

    LogicalOperatorTestData(String op, int[] values) {
      this.op = op;
      this.values = values;

      int v0 = values[0];
      int v1 = values[1];
      int v2 = values[2];
      int v3 = values[3];

      switch (op) {
        case "and":
          this.expected = v0 & v1 & v2 & v3;
          break;
        case "or":
          this.expected = v0 | v1 | v2 | v3;
          break;
        case "xor":
          this.expected = v0 ^ v1 ^ v2 ^ v3;
          break;
        default:
          this.expected = 0;
          fail("Unsupported logical binop " + op);
      }
    }
  }

  private void logicalOperatorMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    LogicalOperatorTestData test = (LogicalOperatorTestData) parameters;
    builder.addStaticMethod(
        "int", name, Collections.singletonList("int"),
        4,
        "    const v0, " + test.values[0],
        "    const v1, " + test.values[1],
        "    const v2, " + test.values[2],
        "    const v3, " + test.values[3],
        // E.g. and-int//2addr v1, v0
        "    " + test.op + "-int/2addr v1, v0    ",
        "    " + test.op + "-int/2addr v2, v1    ",
        "    " + test.op + "-int/2addr v3, v2    ",
        "    return v3\n                    ");
  }

  private void logicalOperatorMethodChecker(DexEncodedMethod method, Object parameters) {
    LogicalOperatorTestData test = (LogicalOperatorTestData) parameters;
    DexCode code = method.getCode().asDexCode();
    // Test that this just returns a constant.
    assertEquals(2, code.instructions.length);
    assertConstValue(test.expected, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  private void addLogicalOperatorsFoldTests(SmaliBuilderWithCheckers testBuilder) {
    int[][] testValues = new int[][]{
        new int[]{0x00, 0x00, 0x00, 0x00},
        new int[]{0x0b, 0x06, 0x03, 0x00},
        new int[]{0x0f, 0x07, 0x03, 0x01},
        new int[]{0x08, 0x04, 0x02, 0x01},
    };

    for (int[] values : testValues) {
      for (String op : new String[]{"and", "or", "xor"}) {
        testBuilder.addTest(
            this::logicalOperatorMethodBuilder,
            this::logicalOperatorMethodChecker,
            new LogicalOperatorTestData(op, values));
      }
    }
  }

  class ShiftTestData {
    final int[] values;
    final String op;
    final int expected;

    ShiftTestData(String op, int[] values) {
      this.values = values;
      this.op = op;


      int v0 = values[0];
      int v1 = values[1];
      int v2 = values[2];
      int v3 = values[3];

      switch (op) {
        case "shl":
          v0 = v0 << v1;
          v0 = v0 << v2;
          v0 = v0 << v3;
          break;
        case "shr":
          v0 = v0 >> v1;
          v0 = v0 >> v2;
          v0 = v0 >> v3;
          break;
        case "ushr":
          v0 = v0 >>> v1;
          v0 = v0 >>> v2;
          v0 = v0 >>> v3;
          break;
        default:
          fail("Unsupported shift " + op);
      }

      this.expected = v0;
    }
  }

  private void shiftOperatorMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    ShiftTestData data = (ShiftTestData) parameters;
    builder.addStaticMethod(
        "int", name, Collections.singletonList("int"),
        4,
        "    const v0, " + data.values[0],
        "    const v1, " + data.values[1],
        "    const v2, " + data.values[2],
        "    const v3, " + data.values[3],
        // E.g. and-int//2addr v1, v0
        "    " + data.op + "-int/2addr v0, v1    ",
        "    " + data.op + "-int/2addr v0, v2    ",
        "    " + data.op + "-int/2addr v0, v3    ",
        "    return v0\n                    "
    );
  }

  private void shiftOperatorMethodChecker(DexEncodedMethod method, Object parameters) {
    ShiftTestData data = (ShiftTestData) parameters;
    DexCode code = method.getCode().asDexCode();
    // Test that this just returns a constant.
    assertEquals(2, code.instructions.length);
    assertConstValue(data.expected, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  public void addShiftOperatorsFolding(SmaliBuilderWithCheckers testBuilder) {
    int[][] testValues = new int[][]{
        new int[]{0x01, 0x01, 0x01, 0x01},
        new int[]{0x01, 0x02, 0x03, 0x04},
        new int[]{0x7f000000, 0x01, 0x2, 0x03},
        new int[]{0x80000000, 0x01, 0x2, 0x03},
        new int[]{0xffffffff, 0x01, 0x2, 0x03},
    };

    for (int[] values : testValues) {
      for (String op : new String[]{"shl", "shr", "ushr"}) {
        testBuilder.addTest(
            this::shiftOperatorMethodBuilder,
            this::shiftOperatorMethodChecker,
            new ShiftTestData(op, values));
      }
    }
  }

  class ShiftWideTestData {
    final long[] values;
    final String op;
    final long expected;

    ShiftWideTestData(String op, long[] values) {
      this.values = values;
      this.op = op;

      long v0 = values[0];
      int v2 = (int) values[1];
      int v4 = (int) values[2];
      int v6 = (int) values[3];

      switch (op) {
        case "shl":
          v0 = v0 << v2;
          v0 = v0 << v4;
          v0 = v0 << v6;
          break;
        case "shr":
          v0 = v0 >> v2;
          v0 = v0 >> v4;
          v0 = v0 >> v6;
          break;
        case "ushr":
          v0 = v0 >>> v2;
          v0 = v0 >>> v4;
          v0 = v0 >>> v6;
          break;
        default:
          fail("Unsupported shift " + op);
      }

      this.expected = v0;
    }
  }

  private void shiftOperatorWideMethodBuilder(
      SmaliBuilder builder, String name, Object parameters) {
    ShiftWideTestData data = (ShiftWideTestData) parameters;
    builder.addStaticMethod(
    "long", name, Collections.singletonList("long"),
        5,
        "    const-wide v0, 0x" + Long.toHexString(data.values[0]) + "L",
        "    const v2, " + data.values[1],
        "    const v3, " + data.values[2],
        "    const v4, " + data.values[3],
        // E.g. and-long//2addr v1, v0
        "    " + data.op + "-long/2addr v0, v2    ",
        "    " + data.op + "-long/2addr v0, v3    ",
        "    " + data.op + "-long/2addr v0, v4    ",
        "    return-wide v0\n                    "
    );
  }

  private void shiftOperatorWideMethodChecker(DexEncodedMethod method, Object parameters) {
    ShiftWideTestData data = (ShiftWideTestData) parameters;
    DexCode code = method.getCode().asDexCode();
    // Test that this just returns a constant.
    assertEquals(2, code.instructions.length);
    assertConstValue(data.expected, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof ReturnWide);
  }

  public void addShiftOperatorsFoldingWide(SmaliBuilderWithCheckers testBuilder) {
    long[][] testValues = new long[][]{
        new long[]{0x01, 0x01, 0x01, 0x01},
        new long[]{0x01, 0x02, 0x03, 0x04},
        new long[]{0x7f0000000000L, 0x01, 0x2, 0x03},
        new long[]{0x800000000000L, 0x01, 0x2, 0x03},
        new long[]{0x7f00000000000000L, 0x01, 0x2, 0x03},
        new long[]{0x8000000000000000L, 0x01, 0x2, 0x03},
        new long[]{0xffffffffffffffffL, 0x01, 0x2, 0x03},
    };

    for (long[] values : testValues) {
      for (String op : new String[]{"shl", "shr", "ushr"}) {
        testBuilder.addTest(
            this::shiftOperatorWideMethodBuilder,
            this::shiftOperatorWideMethodChecker,
            new ShiftWideTestData(op, values));
      }
    }
  }

  private void notIntMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    Integer value = (Integer) parameters;
    builder.addStaticMethod("long", name, Collections.emptyList(),
        1,
        "    const v0, " + value,
        "    not-int v0, v0",
        "    return v0");
  }

  private void notIntMethodChecker(DexEncodedMethod method, Object parameters) {
    Integer value = (Integer) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(~value, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  private void addNotIntFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    ImmutableList.of(0, 1, 0xff, 0xffffffff, 0xff000000, 0x80000000)
        .forEach(v -> testBuilder.addTest(this::notIntMethodBuilder, this::notIntMethodChecker, v));
  }

  private void notLongMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    Long value = (Long) parameters;
    builder.addStaticMethod("long", name, Collections.emptyList(),
        2,
        "    const-wide v0, 0x" + Long.toHexString(value) + "L",
        "    not-long v0, v0",
        "    return-wide v0");
  }

  private void notLongMethodChecker(DexEncodedMethod method, Object parameters) {
    Long value = (Long) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(~value, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof ReturnWide);
  }


  private void addNotLongFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    ImmutableList.of(
        0L,
        1L,
        0xffL,
        0xffffffffffffffffL,
        0x00ffffffffffffffL,
        0xff00000000000000L,
        0x8000000000000000L
    ).forEach(v -> testBuilder.addTest(this::notLongMethodBuilder, this::notLongMethodChecker, v));
  }

  private void negIntMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    Integer value = (Integer) parameters;
    builder.addStaticMethod("int", name, Collections.emptyList(),
        1,
        "    const v0, " + value,
        "    neg-int v0, v0",
        "    return v0");
  }

  private void negIntMethodChecker(DexEncodedMethod method, Object parameters) {
    Integer value = (Integer) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(-value, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  private void addNegIntFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    ImmutableList.of(0, 1, 0xff, 0xffffffff, 0xff000000, 0x80000000)
        .forEach(v -> testBuilder.addTest(this::negIntMethodBuilder, this::negIntMethodChecker, v));
  }

  private void negLongMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    Long value = (Long) parameters;
    builder.addStaticMethod(
        "long", name, Collections.emptyList(),
        2,
        "    const-wide v0, 0x" + Long.toHexString(value) + "L",
        "    neg-long v0, v0",
        "    return-wide v0");
  }

  private void negLongMethodChecker(DexEncodedMethod method, Object parameters) {
    Long value = (Long) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(-value, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof ReturnWide);
  }

  private void addNegLongFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    ImmutableList.of(
        0L,
        1L,
        0xffL,
        0xffffffffffffffffL,
        0x00ffffffffffffffL,
        0xff00000000000000L,
        0x8000000000000000L
    ).forEach(v -> testBuilder.addTest(this::negLongMethodBuilder, this::negLongMethodChecker, v));
  }

  class FloatTestData {

    final float a;
    final float b;
    final Type type;
    final Bias bias;
    final boolean expected;

    FloatTestData(float a, float b, Type type, Bias bias) {
      this.a = a;
      this.b = b;
      this.type = type;
      this.bias = bias;
      switch (type) {
        case EQ: expected = a == b; break;
        case NE: expected = a != b; break;
        case LE: expected = a <= b; break;
        case GE: expected = a >= b; break;
        case LT: expected = a < b; break;
        case GT: expected = a > b; break;
        default: expected = false; assert false;
      }
    }
  }

  private void cmpFloatMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    String[] ifOpcode = new String[6];
    ifOpcode[Type.EQ.ordinal()] = "if-eqz";
    ifOpcode[Type.NE.ordinal()] = "if-nez";
    ifOpcode[Type.LE.ordinal()] = "if-lez";
    ifOpcode[Type.GE.ordinal()] = "if-gez";
    ifOpcode[Type.LT.ordinal()] = "if-ltz";
    ifOpcode[Type.GT.ordinal()] = "if-gtz";

    FloatTestData test = (FloatTestData) parameters;
    String cmpInstruction;
    if (test.bias == Bias.LT) {
      cmpInstruction = "    cmpl-float v0, v0, v1";
    } else {
      cmpInstruction = "    cmpg-float v0, v0, v1";
    }
    builder.addStaticMethod("int", name, Collections.emptyList(), 2,
        "    const v0, 0x" + Integer.toHexString(Float.floatToRawIntBits(test.a)),
        "    const v1, 0x" + Integer.toHexString(Float.floatToRawIntBits(test.b)),
        cmpInstruction,
        "    " + ifOpcode[test.type.ordinal()] + " v0, :label_2",
        "    const v0, 0",
        ":label_1",
        "    return v0",
        ":label_2",
        "  const v0, 1",
        "  goto :label_1"
    );
  }

  private void cmpFloatMethodChecker(DexEncodedMethod method, Object parameters) {
    FloatTestData test = (FloatTestData) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(test.expected ? 1: 0, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  private void addCmpFloatFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    float[] testValues = new float[]{
        Float.NEGATIVE_INFINITY,
        -100.0f,
        -0.0f,
        0.0f,
        100.0f,
        Float.POSITIVE_INFINITY,
        Float.NaN
    };

    // Build the test configuration.
    for (int i = 0; i < testValues.length; i++) {
      for (int j = 0; j < testValues.length; j++) {
        for (Type type : Type.values()) {
          for (Bias bias : Bias.values()) {
            if (bias == Bias.NONE) {
              // Bias NONE is only for long comparison.
              continue;
            }
            // If no NaNs are involved either bias produce the same result.
            if (Float.isNaN(testValues[i]) || Float.isNaN(testValues[j])) {
              // For NaN comparison only test with the bias that provide Java semantics.
              // The Java Language Specification 4.2.3. Floating-Point Types, Formats, and Values
              // says:
              //
              // The numerical comparison operators <, <=, >, and >= return false if either or both
              // operands are NaN
              if ((type == Type.GE || type == Type.GT) && bias == Bias.GT) {
                continue;
              }
              if ((type == Type.LE || type == Type.LT) && bias == Bias.LT) {
                continue;
              }
            }
            testBuilder.addTest(
                this::cmpFloatMethodBuilder,
                this::cmpFloatMethodChecker,
                new FloatTestData(testValues[i], testValues[j], type, bias));
          }
        }
      }
    }
  }

  class DoubleTestData {

    final double a;
    final double b;
    final Type type;
    final Bias bias;
    final boolean expected;

    DoubleTestData(double a, double b, Type type, Bias bias) {
      this.a = a;
      this.b = b;
      this.type = type;
      this.bias = bias;
      switch (type) {
        case EQ: expected = a == b; break;
        case NE: expected = a != b; break;
        case LE: expected = a <= b; break;
        case GE: expected = a >= b; break;
        case LT: expected = a < b; break;
        case GT: expected = a > b; break;
        default: expected = false; assert false;
      }
    }
  }

  private void cmpDoubleMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    String[] ifOpcode = new String[6];
    ifOpcode[Type.EQ.ordinal()] = "if-eqz";
    ifOpcode[Type.NE.ordinal()] = "if-nez";
    ifOpcode[Type.LE.ordinal()] = "if-lez";
    ifOpcode[Type.GE.ordinal()] = "if-gez";
    ifOpcode[Type.LT.ordinal()] = "if-ltz";
    ifOpcode[Type.GT.ordinal()] = "if-gtz";

    DoubleTestData test = (DoubleTestData) parameters;
    String cmpInstruction;
    if (test.bias == Bias.LT) {
      cmpInstruction = "    cmpl-double v0, v0, v2";
    } else {
      cmpInstruction = "    cmpg-double v0, v0, v2";
    }
    builder.addStaticMethod("int", name, Collections.emptyList(), 4,
        "    const-wide v0, 0x" + Long.toHexString(Double.doubleToRawLongBits(test.a)) + "L",
        "    const-wide v2, 0x" + Long.toHexString(Double.doubleToRawLongBits(test.b)) + "L",
        cmpInstruction,
        "    " + ifOpcode[test.type.ordinal()] + " v0, :label_2",
        "    const v0, 0",
        ":label_1",
        "    return v0",
        ":label_2",
        "  const v0, 1",
        "  goto :label_1"
    );
  }

  private void cmpDoubleMethodChecker(DexEncodedMethod method, Object parameters) {
    DoubleTestData test = (DoubleTestData) parameters;
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(test.expected ? 1: 0, code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }


  private void addCmpDoubleFoldTests(SmaliBuilderWithCheckers testBuilder) throws Exception {
    double[] testValues = new double[]{
        Double.NEGATIVE_INFINITY,
        -100.0f,
        -0.0f,
        0.0f,
        100.0f,
        Double.POSITIVE_INFINITY,
        Double.NaN
    };

    // Build the test configuration.
    for (int i = 0; i < testValues.length; i++) {
      for (int j = 0; j < testValues.length; j++) {
        for (Type type : Type.values()) {
          for (Bias bias : Bias.values()) {
            if (bias == Bias.NONE) {
              // Bias NONE is only for long comparison.
              continue;
            }
            if (Double.isNaN(testValues[i]) || Double.isNaN(testValues[j])) {
              // For NaN comparison only test with the bias that provide Java semantics.
              // The Java Language Specification 4.2.3. Doubleing-Point Types, Formats, and Values
              // says:
              //
              // The numerical comparison operators <, <=, >, and >= return false if either or both
              // operands are NaN
              if ((type == Type.GE || type == Type.GT) && bias == Bias.GT) {
                continue;
              }
              if ((type == Type.LE || type == Type.LT) && bias == Bias.LT) {
                continue;
              }
            }
            testBuilder.addTest(
                this::cmpDoubleMethodBuilder,
                this::cmpDoubleMethodChecker,
                new DoubleTestData(testValues[i], testValues[j], type, bias));
          }
        }
      }
    }
  }

  private void cmpLongMethodBuilder(SmaliBuilder builder, String name, Object parameters) {
    long[] values = (long[]) (parameters);
    builder.addStaticMethod(
        "int", name, Collections.emptyList(),
        4,
        "    const-wide v0, 0x" + Long.toHexString(values[0]) + "L",
        "    const-wide v2, 0x" + Long.toHexString(values[1]) + "L",
        "    cmp-long v0, v0, v2",
        "    return v0");
  }

  private void cmpLongMethodChecker(DexEncodedMethod method, Object parameters) {
    long[] values = (long[]) (parameters);
    DexCode code = method.getCode().asDexCode();
    assertEquals(2, code.instructions.length);
    assertConstValue(Long.compare(values[0], values[1]), code.instructions[0]);
    assertTrue(code.instructions[1] instanceof Return);
  }

  private void addCmpLongFold(SmaliBuilderWithCheckers testBuilder) throws Exception {
    ImmutableList.of(
        new long[]{Long.MIN_VALUE, 1L},
        new long[]{Long.MAX_VALUE, 1L},
        new long[]{Long.MIN_VALUE, 0L},
        new long[]{Long.MAX_VALUE, 0L},
        new long[]{Long.MIN_VALUE, -1L},
        new long[]{Long.MAX_VALUE, -1L}
    ).forEach(v -> testBuilder.addTest(this::cmpLongMethodBuilder, this::cmpLongMethodChecker, v));
  }

  @Test
  public void foldingTest() throws Exception {
    SmaliBuilderWithCheckers testBuilder = new SmaliBuilderWithCheckers();
    addBinopFoldingTests(testBuilder);
    addDivIntFoldDivByZero(testBuilder);
    addDivIntFoldRemByZero(testBuilder);
    addNegFoldingTest(testBuilder);
    addShiftOperatorsFolding(testBuilder);
    addShiftOperatorsFoldingWide(testBuilder);
    addLogicalOperatorsFoldTests(testBuilder);
    addNotIntFoldTests(testBuilder);
    addNotLongFoldTests(testBuilder);
    addNegIntFoldTests(testBuilder);
    addNegLongFoldTests(testBuilder);
    addCmpFloatFoldTests(testBuilder);
    addCmpDoubleFoldTests(testBuilder);
    addCmpLongFold(testBuilder);
    testBuilder.run();
  }

}
