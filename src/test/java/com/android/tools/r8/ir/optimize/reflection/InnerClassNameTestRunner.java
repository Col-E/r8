// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InnerClassNameTestRunner extends TestBase {

  private static final Class<?> MAIN_CLASS = InnerClassNameTest.class;
  private static final String PACKAGE = "com/android/tools/r8/ir/optimize/reflection/";
  private static final String REPACKAGE =
      "com/android/some/other/repackage/tools/r8/ir/optimize/reflection/";

  public enum TestNamingConfig {
    DEFAULT,
    DOLLAR2_SEPARATOR,
    EMTPY_SEPARATOR,
    UNDERBAR_SEPARATOR,
    NON_NESTED_INNER,
    WRONG_REPACKAGE,
    OUTER_ENDS_WITH_DOLLAR,
    $_$_$;

    public String getOuterTypeRaw() {
      switch (this) {
        case DEFAULT:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case NON_NESTED_INNER:
        case WRONG_REPACKAGE:
          return "OuterClass";
        case OUTER_ENDS_WITH_DOLLAR:
          return "OuterClass$";
        case $_$_$:
          return "$";
      }
      throw new Unreachable();
    }

    public String getSeparator() {
      switch (this) {
        case DEFAULT:
        case WRONG_REPACKAGE:
        case OUTER_ENDS_WITH_DOLLAR:
        case $_$_$:
          return "$";
        case DOLLAR2_SEPARATOR:
          return "$$";
        case UNDERBAR_SEPARATOR:
          return "_";
        case NON_NESTED_INNER:
        case EMTPY_SEPARATOR:
          return "";
      }
      throw new Unreachable();
    }

    public String getMinifiedSeparator() {
      switch (this) {
        case DEFAULT:
        case OUTER_ENDS_WITH_DOLLAR:
        case $_$_$:
        case UNDERBAR_SEPARATOR:
        case NON_NESTED_INNER:
        case WRONG_REPACKAGE:
        case EMTPY_SEPARATOR:
          return "$";
        case DOLLAR2_SEPARATOR:
          return "$$";
      }
      throw new Unreachable();
    }

    public String getInnerClassName() {
      return this == $_$_$ ? "$" : "InnerClass";
    }

    public String getInnerTypeRaw() {
      switch (this) {
        case DEFAULT:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case WRONG_REPACKAGE:
        case OUTER_ENDS_WITH_DOLLAR:
        case $_$_$:
          return getOuterTypeRaw() + getSeparator() + getInnerClassName();
        case NON_NESTED_INNER:
          return getInnerClassName();
      }
      throw new Unreachable();
    }

    public String getInnerInternalType() {
      // b/130706685: Intentionally repackage inner type only.
      return (this == WRONG_REPACKAGE ? REPACKAGE : PACKAGE) + getInnerTypeRaw();
    }

    public String getOuterInternalType() {
      return PACKAGE + getOuterTypeRaw();
    }

    public String getInnerDescriptor() {
      return "L" + getInnerInternalType() + ";";
    }

    public String getOuterDescriptor() {
      return "L" + getOuterInternalType() + ";";
    }

    public static boolean isGetTypeNameSupported(TestParameters parameters) {
      return parameters.getRuntime().isCf()
          || parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_7_0_0_HOST);
    }
  }

  @Parameters(name = "{0} minify:{1} {2}")
  public static Collection<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        TestNamingConfig.values());
  }

  private final TestParameters parameters;
  private final boolean minify;
  private final TestNamingConfig config;

  public InnerClassNameTestRunner(
      TestParameters parameters, boolean minify, TestNamingConfig config) {
    this.parameters = parameters;
    this.minify = minify;
    this.config = config;
  }

  public boolean hasMalformedInnerClassAttribute() {
    switch (config) {
      case DEFAULT:
      case OUTER_ENDS_WITH_DOLLAR:
      case $_$_$:
      case DOLLAR2_SEPARATOR:
        return false;
      case EMTPY_SEPARATOR:
      case UNDERBAR_SEPARATOR:
      case NON_NESTED_INNER:
      case WRONG_REPACKAGE:
        return true;
      default:
        throw new Unreachable("Unexpected test configuration: " + config);
    }
  }

  private void checkWarningsAboutMalformedAttribute(TestCompileResult<?, ?> result) {
    switch (config) {
      case DEFAULT:
      case OUTER_ENDS_WITH_DOLLAR:
      case $_$_$:
      case DOLLAR2_SEPARATOR:
        result.assertNoMessages();
        break;
      case EMTPY_SEPARATOR:
      case UNDERBAR_SEPARATOR:
      case NON_NESTED_INNER:
      case WRONG_REPACKAGE:
        result
            .assertInfoMessageThatMatches(containsString("Malformed inner-class attribute"))
            .assertInfoMessageThatMatches(containsString(config.getOuterTypeRaw()))
            .assertInfoMessageThatMatches(containsString(config.getInnerTypeRaw()));
        break;
      default:
        throw new Unreachable("Unexpected test configuration: " + config);
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime() && !minify);
    D8TestCompileResult d8CompileResult =
        testForD8()
            .addProgramClassFileData(InnerClassNameTestDump.dump(config, parameters))
            .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
            .setMinApi(parameters)
            .compile();
    checkWarningsAboutMalformedAttribute(d8CompileResult);
    D8TestRunResult d8RunResult = d8CompileResult.run(parameters.getRuntime(), MAIN_CLASS);
    d8RunResult.assertSuccessWithOutput(getExpectedNonMinified(config.getInnerClassName()));
  }

  @Test
  public void testR8() throws Exception {
    JvmTestRunResult runResult = null;
    if (parameters.isCfRuntime()) {
      runResult =
          testForJvm(parameters)
              .addProgramClassFileData(InnerClassNameTestDump.dump(config, parameters))
              .run(parameters.getRuntime(), MAIN_CLASS);
    }

    R8TestCompileResult r8CompileResult =
        testForR8(parameters.getBackend())
            .addKeepMainRule(MAIN_CLASS)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addProgramClassFileData(InnerClassNameTestDump.dump(config, parameters))
            .allowDiagnosticInfoMessages(hasMalformedInnerClassAttribute())
            .minification(minify)
            .addOptionsModification(
                options -> {
                  options.disableInnerClassSeparatorValidationWhenRepackaging = true;
                  options.disableNameReflectionOptimization();
                })
            .setMinApi(parameters)
            .compile()
            .apply(this::checkWarningsAboutMalformedAttribute);

    CodeInspector inspector = r8CompileResult.inspector();
    R8TestRunResult r8RunResult = r8CompileResult.run(parameters.getRuntime(), MAIN_CLASS);
    switch (config) {
      case DEFAULT:
      case OUTER_ENDS_WITH_DOLLAR:
      case $_$_$:
        if (parameters.isCfRuntime()) {
          runResult.assertSuccessWithOutput(getExpectedNonMinified(config.getInnerClassName()));
        }
        r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        break;
      case DOLLAR2_SEPARATOR:
        if (parameters.isCfRuntime()
            && parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8)) {
          // $$ as separator and InnerClass as name, results in $InnerClass from getSimpleName...
          String expectedWithDollarOnInnerName =
              getExpectedNonMinified("$" + config.getInnerClassName());
          runResult.assertSuccessWithOutput(expectedWithDollarOnInnerName);
          // R8 map the inner name to 'a' while keeping $$ as a separator, whose inner name will be
          // "$a" in JDK under 8.
          r8RunResult.assertSuccessWithOutput(
              minify ? getExpectedMinified(inspector, true) : expectedWithDollarOnInnerName);
        } else {
          // $$ in DEX or JDK 9+ will not change the InnerName/getSimpleName.
          r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        }
        break;
      case EMTPY_SEPARATOR:
      case UNDERBAR_SEPARATOR:
      case NON_NESTED_INNER:
      case WRONG_REPACKAGE:
        if (!minify
            && parameters.isCfRuntime()
            && parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8)) {
          // Any non-$ separator results in a runtime exception in getCanonicalName.
          r8RunResult.assertFailureWithErrorThatMatches(containsString("Malformed class name"));
        } else {
          assert minify
              || parameters.isDexRuntime()
              || !parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8);
          r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        }
        break;
      default:
        throw new Unreachable("Unexpected test configuration: " + config);
    }
  }

  private String getExpected(String typeName, String canonicalName, String simpleName) {
    if (TestNamingConfig.isGetTypeNameSupported(parameters)) {
      return StringUtils.lines(
          "getName: " + typeName,
          "getTypeName: " + typeName,
          "getCanonicalName: " + canonicalName,
          "getSimpleName: " + simpleName);
    } else {
      return StringUtils.lines(
          "getName: " + typeName,
          "getCanonicalName: " + canonicalName,
          "getSimpleName: " + simpleName);
    }
  }

  private String getExpectedNonMinified(String innerName) {
    String outerClassType = DescriptorUtils.descriptorToJavaType(config.getOuterDescriptor());
    String innerClassType = DescriptorUtils.descriptorToJavaType(config.getInnerDescriptor());
    return getExpected(innerClassType, outerClassType + "." + innerName, innerName);
  }

  private String getExpectedMinified(CodeInspector inspector) {
    return getExpectedMinified(inspector, false);
  }

  private String getExpectedMinified(CodeInspector inspector, boolean withDollarOnInnerName) {
    String outerClassType = DescriptorUtils.descriptorToJavaType(config.getOuterDescriptor());
    String innerClassType = DescriptorUtils.descriptorToJavaType(config.getInnerDescriptor());
    String outerClassTypeFinal = inspector.clazz(outerClassType).getFinalName();
    String innerClassTypeFinal = inspector.clazz(innerClassType).getFinalName();
    String innerNameFinal =
        !minify
            ? config.getInnerClassName()
            : innerClassTypeFinal.substring(
                outerClassTypeFinal.length() + config.getMinifiedSeparator().length());
    innerNameFinal = withDollarOnInnerName ? "$" + innerNameFinal : innerNameFinal;
    return getExpected(
        innerClassTypeFinal, outerClassTypeFinal + "." + innerNameFinal, innerNameFinal);
  }
}
