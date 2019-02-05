// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static java.lang.System.exit;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@NeverMerge
class MySerializable implements Serializable {
  transient int value;

  MySerializable(int value) {
    this.value = value;
  }

  @NeverInline
  private void writeObject(ObjectOutputStream out) throws IOException {
    System.out.println("Serializable::write");
    out.writeInt(value);
  }

  @NeverInline
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    System.out.println("Serializable::read");
    value = in.readInt();
  }
}

class NoRelaxationForSerializableTestRunner {

  public static void main(String[] args) {
    MySerializable instance = new MySerializable(8);
    byte[] bytes = {};
    try(ByteArrayOutputStream bas = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bas)) {
      oos.writeObject(instance);
      oos.flush();
      bytes = bas.toByteArray();
    } catch(IOException e) {
      e.printStackTrace(System.err);
      exit(1);
    }
    try(ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      MySerializable obj = (MySerializable) ois.readObject();
      if (obj.value != 8) {
        throw new AssertionError("Could not deserialize");
      }
      System.out.println(obj.value);
    } catch(IOException | ClassNotFoundException e) {
      e.printStackTrace(System.err);
      exit(1);
    }
  }
}

@RunWith(Parameterized.class)
public class NoRelaxationForSerializableTest extends AccessRelaxationTestBase {
  private static final Class<?> MAIN = NoRelaxationForSerializableTestRunner.class;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      NeverMerge.class, NeverInline.class, MySerializable.class, MAIN);
  private static final String KEEPMEMBER_RULES = StringUtils.lines(
      "-keepclassmembers class * implements java.io.Serializable {",
      "  private void writeObject(java.io.ObjectOutputStream);",
      "  private void readObject(java.io.ObjectInputStream);",
      "}"
  );
  private static final String EXPECTED_OUTPUT = StringUtils.lines(
      "Serializable::write",
      "Serializable::read",
      "8"
  );

  private final boolean accessModification;
  private Path configuration;

  @Parameterized.Parameters(name = "Backend: {0}, access-modification: {1}")
  public static List<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public NoRelaxationForSerializableTest(Backend backend, boolean accessModification) {
    super(backend);
    this.accessModification = accessModification;
  }

  @Before
  public void setUpConfiguration() throws Exception {
    configuration = temp.newFile("pg.conf").toPath().toAbsolutePath();
    FileUtils.writeTextFile(configuration, StringUtils.lines(
        keepMainProguardConfiguration(MAIN),
        accessModification ? "-allowaccessmodification" : ""
    ));
  }

  @Test
  public void testProguard_withKeepRules() throws Exception {
    assumeTrue(backend == Backend.CF);
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepRuleFiles(configuration)
        .addKeepRules(KEEPMEMBER_RULES)
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testR8_withKeepRules() throws Exception {
    R8TestCompileResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRuleFiles(configuration)
        .addKeepRules(KEEPMEMBER_RULES)
        .compile()
        .inspect(this::inspect);
    // TODO(b/117302947): Need to update ART binary.
    if (backend == Backend.CF) {
      result.run(MAIN).assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testProguard_withoutKeepRules() throws Exception {
    assumeTrue(backend == Backend.CF);
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepRuleFiles(configuration)
        .compile()
        .run(MAIN)
        .assertFailureWithErrorThatMatches(containsString("Could not deserialize"));
  }

  @Test
  public void testR8_withoutKeepRules() throws Exception {
    R8TestCompileResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRuleFiles(configuration)
        .compile();
    // TODO(b/117302947): Need to update ART binary.
    if (backend == Backend.CF) {
      result.run(MAIN).assertFailureWithErrorThatMatches(containsString("Could not deserialize"));
    }
  }

  private void inspect(CodeInspector inspector) {
    assertNotPublic(inspector, MySerializable.class,
        new MethodSignature("writeObject", "void", ImmutableList.of("java.io.ObjectOutputStream")));
    assertNotPublic(inspector, MySerializable.class,
        new MethodSignature("readObject", "void", ImmutableList.of("java.io.ObjectInputStream")));
  }

}
