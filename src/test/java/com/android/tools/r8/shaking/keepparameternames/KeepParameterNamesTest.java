// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepparameternames;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepParameterNamesTest extends TestBase {

  private final TestParameters parameters;
  private final boolean keepParameterNames;
  private final boolean enableMinification;

  @Parameterized.Parameters(name = "{0}, keepparameternames {1}, minification {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public KeepParameterNamesTest(
      TestParameters parameters, boolean keepParameterNames, boolean enableMinification) {
    this.parameters = parameters;
    this.keepParameterNames = keepParameterNames;
    this.enableMinification = enableMinification;
  }

  private void checkLocalVariable(
      LocalVariableTableEntry localVariable,
      int index,
      String name,
      TypeSubject type,
      String signature) {
    assertEquals(index, localVariable.index);
    assertEquals(name, localVariable.name);
    assertEquals(type, localVariable.type);
    assertEquals(signature, localVariable.signature);
  }

  private void checkLocalVariableTable(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Api.class);
    assertThat(classSubject, isPresent());

    MethodSubject method;
    method = classSubject.uniqueMethodWithOriginalName("apiNoArgs");
    assertThat(method, isPresent());
    assertEquals(keepParameterNames, method.hasLocalVariableTable());
    if (keepParameterNames) {
      LocalVariableTable localVariableTable = method.getLocalVariableTable();
      assertEquals(1, localVariableTable.size());
      assertEquals("this", localVariableTable.get(0).name);
      assertTrue(localVariableTable.get(0).type.is(classSubject));
    } else {
      assertTrue(method.getLocalVariableTable().isEmpty());
    }

    method = classSubject.uniqueMethodWithOriginalName("apiNoArgsStatic");
    assertThat(method, isPresent());
    assertFalse(method.hasLocalVariableTable());
    assertTrue(method.getLocalVariableTable().isEmpty());

    method = classSubject.uniqueMethodWithOriginalName("api1");
    assertThat(method, isPresent());
    assertEquals(keepParameterNames, method.hasLocalVariableTable());
    if (keepParameterNames) {
      LocalVariableTable localVariableTable = method.getLocalVariableTable();
      assertEquals(3, localVariableTable.size());
      checkLocalVariable(
          localVariableTable.get(0),
          0,
          "this",
          classSubject.asFoundClassSubject().asTypeSubject(),
          null);
      checkLocalVariable(
          localVariableTable.get(1), 1, "parameter1", inspector.getTypeSubject("int"), null);
      checkLocalVariable(
          localVariableTable.get(2),
          2,
          "parameter2",
          inspector.getTypeSubject("java.lang.String"),
          null);
    } else {
      assertTrue(method.getLocalVariableTable().isEmpty());
    }

    method = classSubject.uniqueMethodWithOriginalName("api2");
    assertThat(method, isPresent());
    assertEquals(keepParameterNames, method.hasLocalVariableTable());
    if (keepParameterNames) {
      LocalVariableTable localVariableTable = method.getLocalVariableTable();
      assertEquals(3, localVariableTable.size());
      checkLocalVariable(
          localVariableTable.get(0),
          0,
          "this",
          classSubject.asFoundClassSubject().asTypeSubject(),
          null);
      checkLocalVariable(
          localVariableTable.get(1), 1, "parameter1", inspector.getTypeSubject("long"), null);
      checkLocalVariable(
          localVariableTable.get(2), 3, "parameter2", inspector.getTypeSubject("double"), null);
    } else {
      assertTrue(method.getLocalVariableTable().isEmpty());
    }

    method = classSubject.uniqueMethodWithOriginalName("api3");
    assertThat(method, isPresent());
    assertEquals(keepParameterNames, method.hasLocalVariableTable());
    if (keepParameterNames) {
      LocalVariableTable localVariableTable = method.getLocalVariableTable();
      assertEquals(3, localVariableTable.size());
      checkLocalVariable(
          localVariableTable.get(0),
          0,
          "this",
          classSubject.asFoundClassSubject().asTypeSubject(),
          null);
      checkLocalVariable(
          localVariableTable.get(1),
          1,
          "parameter1",
          inspector.getTypeSubject("java.util.List"),
          "Ljava/util/List<Ljava/lang/String;>;");
      checkLocalVariable(
          localVariableTable.get(2),
          2,
          "parameter2",
          inspector.getTypeSubject("java.util.Map"),
          "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;");
    } else {
      assertTrue(method.getLocalVariableTable().isEmpty());
    }
  }

  @Test
  public void testApiKept() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "In Api.apiNoArgs",
            "In Api.apiNoArgsStatic",
            "In Api.api1",
            "In Api.api2",
            "In Api.api3");
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepParameterNamesTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class " + Api.class.getTypeName() + "{ api*(...); }")
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableUnusedArgumentAnnotations()
        .minification(enableMinification)
        .apply(this::configureKeepParameterNames)
        .compile()
        .inspect(this::checkLocalVariableTable)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void checkLocalVariableTableNotKept(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Api.class);
    assertThat(classSubject, isPresent());
    assertEquals(enableMinification, classSubject.isRenamed());

    MethodSubject method;
    for (String name : new String[] {"apiNoArgs", "apiNoArgsStatic", "api1", "api2", "api3"}) {
      method = classSubject.uniqueMethodWithOriginalName(name);
      assertThat(method, isPresent());
      assertEquals(enableMinification, method.isRenamed());
      assertFalse(method.hasLocalVariableTable());
      assertTrue(method.getLocalVariableTable().isEmpty());
    }
  }

  @Test
  public void testApiNotKept() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "In Api.apiNoArgs",
            "In Api.apiNoArgsStatic",
            "In Api.api1",
            "In Api.api2",
            "In Api.api3");
    testForR8(parameters.getBackend())
        .enableConstantArgumentAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableUnusedArgumentAnnotations()
        .addInnerClasses(KeepParameterNamesTest.class)
        .addKeepMainRule(TestClass.class)
        .minification(enableMinification)
        .apply(this::configureKeepParameterNames)
        .compile()
        .inspect(this::checkLocalVariableTableNotKept)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void configureKeepParameterNames(TestShrinkerBuilder builder) {
    if (keepParameterNames) {
      builder.addKeepRules("-keepparameternames");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      Api api = new Api();
      api.apiNoArgs();
      Api.apiNoArgsStatic();
      api.api1(0, "Hello, world!");
      api.api2(0, 0.0d);
      api.api3(null, null);
    }
  }

  @NeverClassInline
  static class Api {
    @NeverInline
    void apiNoArgs() {
      System.out.println("In Api.apiNoArgs");
    }

    @NeverInline
    static void apiNoArgsStatic() {
      System.out.println("In Api.apiNoArgsStatic");
    }

    @NeverInline
    @KeepConstantArguments
    @KeepUnusedArguments
    void api1(int parameter1, String parameter2) {
      try {
        // Reflective behaviour to trigger IR building in the enqueuer.
        Class.forName("NotFound");
      } catch (Exception e) {
      }
      System.out.println("In Api.api1");
    }

    @NeverInline
    @KeepConstantArguments
    @KeepUnusedArguments
    void api2(long parameter1, double parameter2) {
      System.out.println("In Api.api2");
    }

    @NeverInline
    @KeepConstantArguments
    @KeepUnusedArguments
    void api3(List<String> parameter1, Map<String, Object> parameter2) {
      System.out.println("In Api.api3");
    }
  }
}
