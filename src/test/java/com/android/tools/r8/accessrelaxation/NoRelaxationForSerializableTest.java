// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static java.lang.System.exit;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@NoVerticalClassMerging
class MySerializable implements Serializable {

  @NeverPropagateValue transient int value;

  MySerializable(int value) {
    this.value = value;
  }

  @NeverInline
  private void writeObject(ObjectOutputStream out) throws IOException {
    System.out.println("Serializable::write");
    out.writeInt(value);
  }

  @NeverInline
  private void readObject(ObjectInputStream in) throws IOException {
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
  private static final List<Class<?>> CLASSES = ImmutableList.of(MySerializable.class, MAIN);
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

  @Parameters(name = "{0}, access-modification: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
            .build(),
        BooleanUtils.values());
  }

  public NoRelaxationForSerializableTest(TestParameters parameters, boolean accessModification) {
    super(parameters);
    this.accessModification = accessModification;
  }

  @Test
  public void testProguard_withKeepRules() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.getLatest())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN)
        .addKeepRules(KEEPMEMBER_RULES)
        .addInliningAnnotations()
        .addMemberValuePropagationAnnotations()
        .addNoVerticalClassMergingAnnotations()
        .allowAccessModification(accessModification)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testR8_withKeepRules() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addMemberValuePropagationAnnotations()
        .addNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(KEEPMEMBER_RULES)
        .allowAccessModification(accessModification)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V8_1_0)
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V10_0_0),
            runResult -> runResult.assertSuccessWithOutput(EXPECTED_OUTPUT),
            parameters.isDexRuntimeVersion(Version.V6_0_1)
                || parameters.isDexRuntimeVersion(Version.V7_0_0),
            runResult -> runResult.assertFailureWithErrorThatThrows(UnsatisfiedLinkError.class),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(NoSuchAlgorithmException.class));
  }

  @Test
  public void testProguard_withoutKeepRules() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.getLatest())
        .addProgramClasses(CLASSES)
        .addInliningAnnotations()
        .addMemberValuePropagationAnnotations()
        .addNoVerticalClassMergingAnnotations()
        .addKeepMainRule(MAIN)
        .allowAccessModification(accessModification)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("Could not deserialize"));
  }

  @Test
  public void testR8_withoutKeepRules() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .addKeepMainRule(MAIN)
        .allowAccessModification(accessModification)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V8_1_0)
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V10_0_0),
            runResult ->
                runResult.assertFailureWithErrorThatMatches(
                    containsString("Could not deserialize")),
            parameters.isDexRuntimeVersion(Version.V6_0_1)
                || parameters.isDexRuntimeVersion(Version.V7_0_0),
            runResult -> runResult.assertFailureWithErrorThatThrows(UnsatisfiedLinkError.class),
            runResult ->
                runResult.assertFailureWithErrorThatThrows(NoSuchAlgorithmException.class));
  }

  private void inspect(CodeInspector inspector) {
    assertNotPublic(inspector, MySerializable.class,
        new MethodSignature("writeObject", "void", ImmutableList.of("java.io.ObjectOutputStream")));
    assertNotPublic(inspector, MySerializable.class,
        new MethodSignature("readObject", "void", ImmutableList.of("java.io.ObjectInputStream")));
  }
}
