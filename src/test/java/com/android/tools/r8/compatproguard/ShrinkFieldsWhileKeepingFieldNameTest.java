// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ShrinkFieldsWhileKeepingFieldNameTest extends TestBase {

  private static final String KEEP_FIELD_NAMES_RULE =
      "-keepclassmembernames,allowoptimization class"
          + " com.android.tools.r8.compatproguard.ShrinkFieldsWhileKeepingFieldNameTest$Person {"
          + " <fields>; }";
  private static final String EXPECTED_RESULT =
      StringUtils.lines("Person[name=John; age=42]", "Person[name=Jane; age=42]");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ShrinkFieldsWhileKeepingFieldNameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testShrinkFieldWhileKeepingFieldNameR8() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClasses(Person.class, Main.class)
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .addKeepRules(KEEP_FIELD_NAMES_RULE)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertSingleFieldWithOriginalName)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testShrinkFieldWhileKeepingFieldNameR8Compat() throws Throwable {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(Person.class, Main.class)
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .addKeepRules(KEEP_FIELD_NAMES_RULE)
        .setMinApi(parameters)
        .compile()
        // TODO(b/200933020): this assert shall pass.
        // .inspect(this::assertSingleFieldWithOriginalName)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertSingleFieldWithOriginalName(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Person.class);
    assertEquals(1, clazz.allInstanceFields().size());
    assertEquals("name", clazz.field("java.lang.String", "name").getFinalName());
  }

  @NeverClassInline
  static class Person {

    private final String name;
    private final int age;

    Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Person)) {
        return false;
      }
      Person person = (Person) o;
      return age == person.age && Objects.equals(name, person.name);
    }

    @Override
    public String toString() {
      return "Person[name=" + name + "; age=" + age + "]";
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new Person("John", 42));
      System.out.println(new Person("Jane", 42));
    }
  }
}
