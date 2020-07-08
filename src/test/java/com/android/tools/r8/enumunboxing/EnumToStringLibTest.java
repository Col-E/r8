// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumToStringLibTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;
  private final boolean enumUnboxing;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2} unbox: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules(),
        BooleanUtils.values());
  }

  public EnumToStringLibTest(
      TestParameters parameters,
      boolean enumValueOptimization,
      EnumKeepRules enumKeepRules,
      boolean enumUnboxing) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
    this.enumUnboxing = enumUnboxing;
  }

  @Test
  public void testToStringLib() throws Exception {
    Assume.assumeFalse(
        "The test rely on valueOf, so only studio or snap keep rules are valid.",
        enumKeepRules == EnumKeepRules.NONE);
    // Compile the lib cf to cf.
    R8TestCompileResult javaLibShrunk = compileLibrary();
    assertEnumFieldsMinified(javaLibShrunk.inspector());
    // Compile the program with the lib.
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(AlwaysCorrectProgram.class, AlwaysCorrectProgram2.class)
            .addProgramFiles(javaLibShrunk.writeToZip())
            .addKeepMainRule(AlwaysCorrectProgram.class)
            .addKeepMainRule(AlwaysCorrectProgram2.class)
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(
                options -> {
                  options.enableEnumUnboxing = enumUnboxing;
                  options.enableEnumValueOptimization = enumValueOptimization;
                  options.enableEnumSwitchMapRemoval = enumValueOptimization;
                  options.testing.enableEnumUnboxingDebugLogs = enumUnboxing;
                })
            .allowDiagnosticInfoMessages(enumUnboxing)
            .setMinApi(parameters.getApiLevel())
            .compile();
    compile
        .run(parameters.getRuntime(), AlwaysCorrectProgram.class)
        .assertSuccessWithOutputLines("0", "1", "2", "0", "1", "2", "0", "1", "2");
    if (!enumKeepRules.isSnap() && enumUnboxing) {
      // TODO(b/160667929): Fix toString and enum unboxing.
      compile
          .run(parameters.getRuntime(), AlwaysCorrectProgram2.class)
          .assertFailureWithErrorThatMatches(containsString("IllegalArgumentException"));
      return;
    }
    compile
        .run(parameters.getRuntime(), AlwaysCorrectProgram2.class)
        .assertSuccessWithOutputLines("0", "1", "2", "0", "1", "2");
  }

  private void assertEnumFieldsMinified(CodeInspector codeInspector) throws Exception {
    if (enumKeepRules.isSnap()) {
      return;
    }
    ClassSubject clazz = codeInspector.clazz(ToStringLib.LibEnum.class);
    assertThat(clazz, isPresent());
    for (String fieldName : new String[] {"COFFEE", "BEAN", "SUGAR"}) {
      assertThat(
          codeInspector.field(ToStringLib.LibEnum.class.getField(fieldName)),
          isPresentAndRenamed());
    }
  }

  private R8TestCompileResult compileLibrary() throws Exception {
    return testForR8(Backend.CF)
        .addProgramClasses(ToStringLib.class, ToStringLib.LibEnum.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        // TODO(b/160535629): Work-around on some optimizations relying on $VALUES name.
        .addKeepRules(
            "-keep enum "
                + ToStringLib.LibEnum.class.getName()
                + " { static "
                + ToStringLib.LibEnum.class.getName()
                + "[] $VALUES; }")
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addKeepMethodRules(
            Reference.methodFromMethod(
                ToStringLib.class.getDeclaredMethod("lookupByName", String.class)),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("getCoffee")),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("getBean")),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("getSugar")),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("directCoffee")),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("directBean")),
            Reference.methodFromMethod(ToStringLib.class.getDeclaredMethod("directSugar")))
        .addKeepClassRules(ToStringLib.LibEnum.class)
        .allowDiagnosticMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectDiagnosticMessages(
            msg ->
                assertEnumIsBoxed(
                    ToStringLib.LibEnum.class, ToStringLib.LibEnum.class.getSimpleName(), msg));
  }

  // This class emulates a library with the three public methods getEnumXXX.
  public static class ToStringLib {

    // We pick names here that we assume won't be picked by the minifier (i.e., not a,b,c).
    public enum LibEnum {
      COFFEE,
      BEAN,
      SUGAR;
    }

    // If there is a keep rule on LibEnum fields, then ToStringLib.lookupByName("COFFEE")
    // should answer 0, else, the behavior of ToStringLib.lookupByName("COFFEE") is undefined.
    // ToStringLib.lookupByName(LibEnum.COFFEE.toString()) should always answer 0, no matter
    // what keep rules are on LibEnum.
    public static int lookupByName(String key) {
      if (key == null) {
        return -1;
      } else if (key.contains(LibEnum.COFFEE.name())) {
        return LibEnum.COFFEE.ordinal();
      } else if (key.contains(LibEnum.BEAN.name())) {
        return LibEnum.BEAN.ordinal();
      } else if (key.contains(LibEnum.SUGAR.name())) {
        return LibEnum.SUGAR.ordinal();
      } else {
        return -2;
      }
    }

    // The following method should always return 0, no matter what keep rules are on LibEnum.
    public static int directCoffee() {
      return LibEnum.valueOf(LibEnum.COFFEE.toString()).ordinal();
    }

    public static int directBean() {
      return LibEnum.valueOf(LibEnum.BEAN.toString()).ordinal();
    }

    public static int directSugar() {
      return LibEnum.valueOf(LibEnum.SUGAR.toString()).ordinal();
    }

    public static LibEnum getCoffee() {
      return LibEnum.COFFEE;
    }

    public static LibEnum getBean() {
      return LibEnum.BEAN;
    }

    public static LibEnum getSugar() {
      return LibEnum.SUGAR;
    }
  }

  // The next two classes emulate a program using the ToStringLib library.
  public static class AlwaysCorrectProgram {

    public static void main(String[] args) {
      System.out.println(ToStringLib.directCoffee());
      System.out.println(ToStringLib.directBean());
      System.out.println(ToStringLib.directSugar());
      System.out.println(ToStringLib.lookupByName(ToStringLib.getCoffee().toString()));
      System.out.println(ToStringLib.lookupByName(ToStringLib.getBean().toString()));
      System.out.println(ToStringLib.lookupByName(ToStringLib.getSugar().toString()));
      System.out.println(ToStringLib.LibEnum.valueOf(ToStringLib.getCoffee().toString()).ordinal());
      System.out.println(ToStringLib.LibEnum.valueOf(ToStringLib.getBean().toString()).ordinal());
      System.out.println(ToStringLib.LibEnum.valueOf(ToStringLib.getSugar().toString()).ordinal());
    }
  }

  public static class AlwaysCorrectProgram2 {

    public static void main(String[] args) {
      System.out.println(ToStringLib.lookupByName("COFFEE"));
      System.out.println(ToStringLib.lookupByName("BEAN"));
      System.out.println(ToStringLib.lookupByName("SUGAR"));
      System.out.println(ToStringLib.LibEnum.valueOf("COFFEE").ordinal());
      System.out.println(ToStringLib.LibEnum.valueOf("BEAN").ordinal());
      System.out.println(ToStringLib.LibEnum.valueOf("SUGAR").ordinal());
    }
  }
}
