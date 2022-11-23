// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.nestaccesscontrol.NestAttributesInDexTest.Host.Member1;
import com.android.tools.r8.desugar.nestaccesscontrol.NestAttributesInDexTest.Host.Member2;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NestAttributesInDexTest extends NestAttributesInDexTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "true", "true", "true", "true", "true", "true", "true", "true", "true", "false", "false",
          "true", "true", "true", "false", "false", "true", "true", "true", "false", "false",
          "true", "true", "true");

  // Right now R8 removes the nest attributes if they are not required for runtime execution, so
  // most reflective calls will return false.
  private static final String R8_EXPECTED_OUTPUT =
      StringUtils.lines(
          "false", "false", "false", "true", "false", "false", "true", "false", "false", "false",
          "false", "false", "true", "false", "false", "false", "false", "false", "true", "false",
          "false", "true", "true", "true");

  private void checkResult(TestRunResult<?> result) {
    if (isRuntimeWithNestSupport(parameters.getRuntime())) {
      result.assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    }
  }

  private void checkResultR8(TestRunResult<?> result) {
    if (isRuntimeWithNestSupport(parameters.getRuntime())) {
      result.assertSuccessWithOutput(R8_EXPECTED_OUTPUT);
    } else {
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    }
  }

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && isRuntimeWithNestSupport(parameters.asCfRuntime())
            && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm()
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean emitNestAnnotationsInDex) {
    ClassSubject host = inspector.clazz(Host.class);
    ClassSubject member1 = inspector.clazz(Member1.class);
    ClassSubject member2 = inspector.clazz(Member2.class);
    assertEquals(
        emitNestAnnotationsInDex
            ? ImmutableList.of(member1.asTypeSubject(), member2.asTypeSubject())
            : Collections.emptyList(),
        host.getFinalNestMembersAttribute());
    TypeSubject expectedNestHostAttribute = emitNestAnnotationsInDex ? host.asTypeSubject() : null;
    assertEquals(expectedNestHostAttribute, member1.getFinalNestHostAttribute());
    assertEquals(expectedNestHostAttribute, member2.getFinalNestHostAttribute());
    ClassSubject otherHost = inspector.clazz(OtherHost.class);
    assertNull(otherHost.getFinalNestHostAttribute());
    assertEquals(0, otherHost.getFinalNestMembersAttribute().size());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  @Test
  public void testD8NoDesugar() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .disableDesugaring()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> assertFalse(options.emitNestAnnotationsInDex))
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  @Test
  public void testR8NoKeep() throws Exception {
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule(TestClass.class)
        .compile()
        // Don't expect any nest info. The classes Host, Member1, Member2 and OtherHost remains
        // due to the use of class constants in the code, but they have no methods so no nest
        // attributes are required for runtime execution.
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResultR8);
  }

  @Test
  public void testR8KeepHost() throws Exception {
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Host.class)
        .compile()
        // Don't expect any nest info. Class Host is kept and the classes Member1, Members and
        // OtherHost remains due to the use of class constants in the code, but they have no methods
        // so no nest attributes are required for runtime execution.
        // TODO(b/130716228#comment5): How to keep nest attributes?
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResultR8);
  }

  @Test
  public void testR8KeepMembers() throws Exception {
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Member1.class, Member2.class)
        .compile()
        // Don't expect any nest info. Member1 and Member2 are kept and the classes Host and
        // OtherHost remains due to the use of class constants in the code, but they have no
        // methods so no nest attributes are required for runtime execution.
        // TODO(b/130716228#comment5): How to keep nest attributes?
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResultR8);
  }

  @Test
  public void testR8KeepBoth() throws Exception {
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addProgramClasses(OtherHost.class)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Host.class, Member1.class, Member2.class)
        .compile()
        // Don't expect any nest info. All of Host, Member1 and Member2 are kept,
        // but they have no methods so no nest attributes are required for runtime execution.
        // TODO(b/130716228#comment5): How to keep nest attributes?
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResultR8);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    ClassFileTransformer transformer =
        transformer(TestClass.class)
            .setMinVersion(CfVm.JDK11)
            .transformMethodInsnInMethod(
                "main",
                ((opcode, owner, name, descriptor, isInterface, continuation) -> {
                  if (owner.equals(DescriptorUtils.getClassBinaryName(AdditionalClassAPIs.class))) {
                    if (name.equals("getNestMembers")) {
                      continuation.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/lang/Class",
                          "getNestMembers",
                          "()[Ljava/lang/Class;",
                          false);
                    } else if (name.equals("getNestHost")) {
                      continuation.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/lang/Class",
                          "getNestHost",
                          "()Ljava/lang/Class;",
                          false);
                    } else if (name.equals("isNestmateOf")) {
                      continuation.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/lang/Class",
                          "isNestmateOf",
                          "(Ljava/lang/Class;)Z",
                          false);
                    } else {
                      fail("Unsupported rewriting of API " + owner + "." + name + descriptor);
                    }
                  } else {
                    continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                }));

    return ImmutableList.of(
        transformer.transform(),
        withNest(Host.class).transform(),
        withNest(Member1.class).transform(),
        withNest(Member2.class).transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    return transformer(clazz).setNest(Host.class, Member1.class, Member2.class);
  }

  static class AdditionalClassAPIs {
    public static Class<?>[] getNestMembers(Class<?> clazz) {
      throw new RuntimeException();
    }

    public static Class<?> getNestHost(Class<?> clazz) {
      throw new RuntimeException();
    }

    public static boolean isNestmateOf(Class<?> class1, Class<?> class2) {
      throw new RuntimeException();
    }
  }

  static class TestClass {

    public static boolean sameArrayContent(Class<?>[] array1, Class<?>[] array2) {
      Set<Class<?>> expected = new HashSet<>(Arrays.asList(array1));
      for (Class<?> clazz : array2) {
        if (!expected.remove(clazz)) {
          return false;
        }
      }
      return expected.isEmpty();
    }

    public static void main(String[] args) {
      Class<?>[] nestMembers = new Class<?>[] {Host.class, Member1.class, Member2.class};
      System.out.println(
          sameArrayContent(nestMembers, AdditionalClassAPIs.getNestMembers(Host.class)));
      System.out.println(
          sameArrayContent(nestMembers, AdditionalClassAPIs.getNestMembers(Member1.class)));
      System.out.println(
          sameArrayContent(nestMembers, AdditionalClassAPIs.getNestMembers(Member2.class)));
      System.out.println(AdditionalClassAPIs.getNestHost(Host.class).equals(Host.class));
      System.out.println(AdditionalClassAPIs.getNestHost(Member1.class).equals(Host.class));
      System.out.println(AdditionalClassAPIs.getNestHost(Member2.class).equals(Host.class));
      for (Class<?> class1 : nestMembers) {
        for (Class<?> class2 : nestMembers) {
          System.out.println(AdditionalClassAPIs.isNestmateOf(class1, class2));
        }
        System.out.println(AdditionalClassAPIs.isNestmateOf(OtherHost.class, class1));
        System.out.println(AdditionalClassAPIs.isNestmateOf(class1, OtherHost.class));
      }
      System.out.println(AdditionalClassAPIs.getNestHost(OtherHost.class).equals(OtherHost.class));
      System.out.println(
          sameArrayContent(
              new Class<?>[] {OtherHost.class},
              AdditionalClassAPIs.getNestMembers(OtherHost.class)));
      System.out.println(AdditionalClassAPIs.isNestmateOf(OtherHost.class, OtherHost.class));
    }
  }

  static class OtherHost {}

  static class Host {
    static class Member1 {}

    static class Member2 {}
  }
}
