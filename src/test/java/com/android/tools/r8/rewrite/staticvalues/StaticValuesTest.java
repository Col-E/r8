// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.staticvalues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueBoolean;
import com.android.tools.r8.graph.DexValue.DexValueByte;
import com.android.tools.r8.graph.DexValue.DexValueChar;
import com.android.tools.r8.graph.DexValue.DexValueDouble;
import com.android.tools.r8.graph.DexValue.DexValueFloat;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueLong;
import com.android.tools.r8.graph.DexValue.DexValueShort;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class StaticValuesTest extends SmaliTestBase {

  @Test
  public void testAllTypes() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("booleanField", "Z");
    builder.addStaticField("byteField", "B");
    builder.addStaticField("shortField", "S");
    builder.addStaticField("intField", "I");
    builder.addStaticField("longField", "J");
    builder.addStaticField("floatField", "F");
    builder.addStaticField("doubleField", "D");
    builder.addStaticField("charField", "C");
    builder.addStaticField("stringField", "Ljava/lang/String;");

    builder.addStaticInitializer(
        2,
        "const               v0, 1",
        "sput-byte           v0, LTest;->booleanField:Z",
        "sput-byte           v0, LTest;->byteField:B",
        "const               v0, 2",
        "sput-short          v0, LTest;->shortField:S",
        "const               v0, 3",
        "sput                v0, LTest;->intField:I",
        "const-wide          v0, 4",
        "sput-wide           v0, LTest;->longField:J",
        "const               v0, 0x40a00000",  // 5.0.
        "sput                v0, LTest;->floatField:F",
        "const-wide          v0, 0x4018000000000000L",  // 6.0.
        "sput-wide           v0, LTest;->doubleField:D",
        "const               v0, 0x37",  // ASCII 7.
        "sput-char           v0, LTest;->charField:C",
        "const-string        v0, \"8\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget-boolean        v1, LTest;->booleanField:Z",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Z)V",
        "sget-byte           v1, LTest;->byteField:B",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget-short          v1, LTest;->shortField:S",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget                v1, LTest;->intField:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget-wide           v1, LTest;->longField:J",
        "invoke-virtual      { v0, v1, v2 }, Ljava/io/PrintStream;->println(J)V",
        "sget                v1, LTest;->floatField:F",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(F)V",
        "sget-wide           v1, LTest;->doubleField:D",
        "invoke-virtual      { v0, v1, v2 }, Ljava/io/PrintStream;->println(D)V",
        "sget-char           v1, LTest;->charField:C",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(C)V",
        "sget-object         v1, LTest;->stringField:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    // Test is running without tree-shaking, so the empty <clinit> is not removed.
    assertTrue(
        inspector.clazz("Test").clinit().getMethod().getCode().asDexCode().isEmptyVoidMethod());

    DexValue value;
    assertTrue(inspector.clazz("Test").field("boolean", "booleanField").hasStaticValue());
    value = inspector.clazz("Test").field("boolean", "booleanField").getStaticValue();
    assertTrue(value instanceof DexValueBoolean);
    assertEquals(true, ((DexValueBoolean) value).getValue());

    assertTrue(inspector.clazz("Test").field("byte", "byteField").hasStaticValue());
    value = inspector.clazz("Test").field("byte", "byteField").getStaticValue();
    assertTrue(value instanceof DexValueByte);
    assertEquals(1, ((DexValueByte) value).getValue());

    assertTrue(inspector.clazz("Test").field("short", "shortField").hasStaticValue());
    value = inspector.clazz("Test").field("short", "shortField").getStaticValue();
    assertTrue(value instanceof DexValueShort);
    assertEquals(2, ((DexValueShort) value).getValue());

    assertTrue(inspector.clazz("Test").field("int", "intField").hasStaticValue());
    value = inspector.clazz("Test").field("int", "intField").getStaticValue();
    assertTrue(value instanceof DexValueInt);
    assertEquals(3, ((DexValueInt) value).getValue());

    assertTrue(inspector.clazz("Test").field("long", "longField").hasStaticValue());
    value = inspector.clazz("Test").field("long", "longField").getStaticValue();
    assertTrue(value instanceof DexValueLong);
    assertEquals(4, ((DexValueLong) value).getValue());

    assertTrue(inspector.clazz("Test").field("float", "floatField").hasStaticValue());
    value = inspector.clazz("Test").field("float", "floatField").getStaticValue();
    assertTrue(value instanceof DexValueFloat);
    assertEquals(5.0f, ((DexValueFloat) value).getValue(), 0.0);

    assertTrue(inspector.clazz("Test").field("double", "doubleField").hasStaticValue());
    value = inspector.clazz("Test").field("double", "doubleField").getStaticValue();
    assertTrue(value instanceof DexValueDouble);
    assertEquals(6.0f, ((DexValueDouble) value).getValue(), 0.0);

    assertTrue(inspector.clazz("Test").field("char", "charField").hasStaticValue());
    value = inspector.clazz("Test").field("char", "charField").getStaticValue();
    assertTrue(value instanceof DexValueChar);
    assertEquals(0x30 + 7, ((DexValueChar) value).getValue());

    assertTrue(inspector.clazz("Test").field("java.lang.String", "stringField").hasStaticValue());
    value = inspector.clazz("Test").field("java.lang.String", "stringField").getStaticValue();
    assertTrue(value instanceof DexValueString);
    assertEquals(("8"), ((DexValueString) value).getValue().toString());

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("true", "1", "2", "3", "4", "5.0", "6.0", "7", "8"), result);
  }

  @Test
  public void getBeforePut() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("field1", "I", "1");
    builder.addStaticField("field2", "I", "2");

    builder.addStaticInitializer(
        1,
        "sget                v0, LTest;->field1:I",
        "sput                v0, LTest;->field2:I",
        "const               v0, 0",
        "sput                v0, LTest;->field1:I",
        "return-void"
    );
    builder.addMainMethod(
        2,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget                v1, LTest;->field1:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget                v1, LTest;->field2:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    MethodSubject clinit = inspector.clazz("Test").clinit();
    // Nothing changed in the class initializer.
    assertEquals(5, clinit.getMethod().getCode().asDexCode().instructions.length);

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("0", "1"), result);
  }

  @Test
  public void testNull() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("stringField", "Ljava/lang/String;", "Hello");
    builder.addStaticField("arrayField", "[I");
    builder.addStaticField("arrayField2", "[[[[I");

    builder.addStaticInitializer(
        2,
        "const               v0, 0",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "sput-object         v0, LTest;->arrayField:[I",
        "sput-object         v0, LTest;->arrayField2:[[[[I",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget-object         v1, LTest;->stringField:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, LTest;->arrayField:[I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V",
        "sget-object         v1, LTest;->arrayField2:[[[[I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    // Test is running without tree-shaking, so the empty <clinit> is not removed.
    assertTrue(
        inspector.clazz("Test").clinit().getMethod().getCode().asDexCode().isEmptyVoidMethod());

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("null", "null", "null"), result);
  }

  @Test
  public void testString() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("stringField1", "Ljava/lang/String;", "Hello");
    builder.addStaticField("stringField2", "Ljava/lang/String;", "Hello");
    builder.addStaticField("stringField3", "Ljava/lang/String;", "Hello");

    builder.addStaticInitializer(
        2,
        "const-string        v0, \"Value1\"",
        "sput-object         v0, LTest;->stringField1:Ljava/lang/String;",
        "const-string        v0, \"Value2\"",
        "sput-object         v0, LTest;->stringField2:Ljava/lang/String;",
        "sput-object         v0, LTest;->stringField3:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget-object         v1, LTest;->stringField1:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, LTest;->stringField2:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, LTest;->stringField3:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    // Test is running without tree-shaking, so the empty <clinit> is not removed.
    assertTrue(
        inspector.clazz("Test").clinit().getMethod().getCode().asDexCode().isEmptyVoidMethod());

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("Value1", "Value2", "Value2"), result);
  }

  @Test
  public void testMultiplePuts() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("intField", "I");
    builder.addStaticField("stringField", "Ljava/lang/String;");

    builder.addStaticInitializer(
        1,
        "const               v0, 0",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"4\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "const               v0, 1",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"5\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "const               v0, 2",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"6\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "const               v0, 3",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"7\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        2,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget                v1, LTest;->intField:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget-object         v1, LTest;->stringField:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    // Test is running without tree-shaking, so the empty <clinit> is not removed.
    assertTrue(
        inspector.clazz("Test").clinit().getMethod().getCode().asDexCode().isEmptyVoidMethod());

    DexValue value;
    assertTrue(inspector.clazz("Test").field("int", "intField").hasStaticValue());
    value = inspector.clazz("Test").field("int", "intField").getStaticValue();
    assertTrue(value instanceof DexValueInt);
    assertEquals(3, ((DexValueInt) value).getValue());

    assertTrue(inspector.clazz("Test").field("java.lang.String", "stringField").hasStaticValue());
    value = inspector.clazz("Test").field("java.lang.String", "stringField").getStaticValue();
    assertTrue(value instanceof DexValueString);
    assertEquals(("7"), ((DexValueString) value).getValue().toString());

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("3", "7") , result);
  }


  @Test
  public void testMultiplePutsWithControlFlow() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticField("booleanField", "Z");
    builder.addStaticField("intField", "I");
    builder.addStaticField("intField2", "I");
    builder.addStaticField("stringField", "Ljava/lang/String;");

    builder.addStaticInitializer(
        1,
        "const               v0, 0",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"4\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "const               v0, 1",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"5\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "sget-boolean        v0, LTest;->booleanField:Z",
        "if-eqz              v0, :label_1",
        "const               v0, 8",
        "sput                v0, LTest;->intField2:I",
        ":label_1",
        "const               v0, 9",
        "sput                v0, LTest;->intField2:I",
        "goto                :label_2",
        ":label_2",
        "const               v0, 2",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"6\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "const               v0, 3",
        "sput                v0, LTest;->intField:I",
        "const-string        v0, \"7\"",
        "sput-object         v0, LTest;->stringField:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        2,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget                v1, LTest;->intField:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "sget-object         v1, LTest;->stringField:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    assertTrue(inspector.clazz("Test").clinit().isPresent());

    DexValue value;
    assertTrue(inspector.clazz("Test").field("int", "intField").hasStaticValue());
    value = inspector.clazz("Test").field("int", "intField").getStaticValue();
    assertTrue(value instanceof DexValueInt);
    assertEquals(3, ((DexValueInt) value).getValue());

    assertTrue(inspector.clazz("Test").field("java.lang.String", "stringField").hasStaticValue());
    value = inspector.clazz("Test").field("java.lang.String", "stringField").getStaticValue();
    assertTrue(value instanceof DexValueString);
    assertEquals(("7"), ((DexValueString) value).getValue().toString());

    DexCode code = inspector.clazz("Test").clinit().getMethod().getCode().asDexCode();
    for (Instruction instruction : code.instructions) {
      if (instruction instanceof Sput) {
        Sput put = (Sput) instruction;
        // Only int put ot intField2.
        assertEquals(put.getField().name.toString(), "intField2");
      } else {
        // No Object (String) puts.
        assertFalse(instruction instanceof SputObject);
      }
    }

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("3", "7"), result);
  }

  @Test
  public void testInitializationToOwnClassName() throws Exception {
    String className = "org.example.Test";
    SmaliBuilder builder = new SmaliBuilder(className);

    builder.addStaticField("name1", "Ljava/lang/String;");
    builder.addStaticField("name2", "Ljava/lang/String;");
    builder.addStaticField("name3", "Ljava/lang/String;");
    builder.addStaticField("simpleName1", "Ljava/lang/String;");
    builder.addStaticField("simpleName2", "Ljava/lang/String;");
    builder.addStaticField("simpleName3", "Ljava/lang/String;");

    String descriptor = builder.getCurrentClassDescriptor();

    builder.addStaticInitializer(
        3,
        "const-class         v0, " + descriptor,
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;",
        "move-result-object  v0",
        "sput-object         v0, " + descriptor + "->simpleName1:Ljava/lang/String;",
        "const-class         v0, " + descriptor,
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getName()Ljava/lang/String;",
        "move-result-object  v0",
        "sput-object         v0, " + descriptor + "->name1:Ljava/lang/String;",
        "const-class         v0, " + descriptor,
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;",
        "move-result-object  v1",
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getName()Ljava/lang/String;",
        "move-result-object  v2",
        "sput-object         v1, " + descriptor + "->simpleName2:Ljava/lang/String;",
        "sput-object         v1, " + descriptor + "->simpleName3:Ljava/lang/String;",
        "sput-object         v2, " + descriptor + "->name2:Ljava/lang/String;",
        "sput-object         v2, " + descriptor + "->name3:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget-object         v1, " + descriptor + "->simpleName1:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->name1:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->simpleName2:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->name2:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->simpleName3:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->name3:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    assertTrue(inspector.clazz(className).isPresent());
    // Test is running without tree-shaking, so the empty <clinit> is not removed.
    assertTrue(
        inspector.clazz(className).clinit().getMethod().getCode().asDexCode().isEmptyVoidMethod());

    String result = runArt(processedApplication, options, className);

    assertEquals(StringUtils.lines("Test", className, "Test", className, "Test", className),
        result);
  }

  @Test
  public void testInitializationToOtherClassName() throws Exception {
    String className = "org.example.Test";
    SmaliBuilder builder = new SmaliBuilder(className);

    builder.addStaticField("simpleName", "Ljava/lang/String;");
    builder.addStaticField("name", "Ljava/lang/String;");

    String descriptor = builder.getCurrentClassDescriptor();

    builder.addStaticInitializer(
        3,
        "const-class         v0, Lorg/example/Test2;",
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;",
        "move-result-object  v0",
        "sput-object         v0, " + descriptor + "->simpleName:Ljava/lang/String;",
        "const-class         v0, Lorg/example/Test2;",
        "invoke-virtual      { v0 }, Ljava/lang/Class;->getName()Ljava/lang/String;",
        "move-result-object  v0",
        "sput-object         v0, " + descriptor + "->name:Ljava/lang/String;",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget-object         v1, " + descriptor + "->simpleName:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sget-object         v1, " + descriptor + "->name:Ljava/lang/String;",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "return-void"
    );

    builder.addClass("org.example.Test2");

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    assertTrue(inspector.clazz(className).isPresent());
    assertTrue(inspector.clazz(className).clinit().isPresent());

    String result = runArt(processedApplication, options, className);

    assertEquals(StringUtils.lines("Test2", "org.example.Test2"), result);
  }

  @Test
  public void fieldOnOtherClass() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    builder.addStaticInitializer(
        1,
        "const               v0, 2",
        "sput                v0, LOther;->field:I",
        "return-void"
    );
    builder.addMainMethod(
        2,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget                v1, LOther;->field:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "return-void"
    );

    builder.addClass("Other");
    builder.addStaticField("field", "I", "1");

    InternalOptions options = new InternalOptions();
    DexApplication originalApplication = buildApplication(builder, options);
    DexApplication processedApplication = processApplication(originalApplication, options);

    DexInspector inspector = new DexInspector(processedApplication);
    MethodSubject clinit = inspector.clazz("Test").clinit();
    // Nothing changed in the class initializer.
    assertEquals(3, clinit.getMethod().getCode().asDexCode().instructions.length);

    String result = runArt(processedApplication, options);

    assertEquals(StringUtils.lines("2"), result);
  }

  @Test
  public void b67468748() throws Exception {
    final String CLASS_NAME = "Test";

    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);

    // The static field LTest;->intField:I is not defined even though it is written in the
    // <clinit> code. This class cannot load, but we can still process it to output which still
    // cannot load.
    builder.addStaticInitializer(
        2,
        "const               v0, 3",
        "sput                v0, LTest;->intField:I",
        "return-void"
    );
    builder.addMainMethod(
        3,
        "sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "sget                v1, LTest;->intField:I",
        "invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->println(I)V",
        "return-void"
    );

    AndroidApp application = builder.build();

    // The code does not run on Art, as there is a missing field.
    ProcessResult result = runOnArtRaw(application, CLASS_NAME);
    assertEquals(1, result.exitCode);
    assertTrue(result.stderr.contains("java.lang.NoSuchFieldError"));

    // Run in release mode to turn on initializer defaults rewriting.
    application = compileWithD8(application, options -> options.debug = false);

    // The code does still not run on Art, as there is still a missing field.
    result = runOnArtRaw(application, CLASS_NAME);
    assertEquals(1, result.exitCode);
    assertTrue(result.stderr.contains("java.lang.NoSuchFieldError"));
  }

}
