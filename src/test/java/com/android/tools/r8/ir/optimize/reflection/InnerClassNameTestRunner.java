// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InnerClassNameTestRunner extends TestBase {

  private static final Class<?> MAIN_CLASS = InnerClassNameTest.class;
  public static final String PACKAGE = "com/android/tools/r8/ir/optimize/reflection/";

  public enum TestNamingConfig {
    DEFAULT,
    DOLLAR2_SEPARATOR,
    EMTPY_SEPARATOR,
    UNDERBAR_SEPARATOR,
    NON_NESTED_INNER,
    OUTER_ENDS_WITH_DOLLAR,
    $_$_$;

    public String getOuterTypeRaw() {
      switch (this) {
        case DEFAULT:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case NON_NESTED_INNER:
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

    public String getInnerClassName() {
      return this == $_$_$ ? "$" : "InnerClass";
    }

    public String getInnerTypeRaw() {
      switch (this) {
        case DEFAULT:
        case OUTER_ENDS_WITH_DOLLAR:
        case DOLLAR2_SEPARATOR:
        case EMTPY_SEPARATOR:
        case UNDERBAR_SEPARATOR:
        case $_$_$:
          return getOuterTypeRaw() + getSeparator() + getInnerClassName();
        case NON_NESTED_INNER:
          return getInnerClassName();
      }
      throw new Unreachable();
    }

    public String getInnerInternalType() {
      return PACKAGE + getInnerTypeRaw();
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

    public static boolean isGetTypeNameSupported() {
      return ToolHelper.getDexVm().isNewerThan(DexVm.ART_7_0_0_HOST);
    }
  }

  @Parameters(name = "{0} minify:{1} {2}")
  public static Collection<Object[]> parameters() {
    return buildParameters(Backend.values(), BooleanUtils.values(), TestNamingConfig.values());
  }

  private final Backend backend;
  private final boolean minify;
  private final TestNamingConfig config;

  public InnerClassNameTestRunner(Backend backend, boolean minify, TestNamingConfig config) {
    this.backend = backend;
    this.minify = minify;
    this.config = config;
  }

  @Test
  public void test() throws IOException, CompilationFailedException, ExecutionException {
    JvmTestRunResult runResult =
        testForJvm().addProgramClassFileData(InnerClassNameTestDump.dump(config)).run(MAIN_CLASS);

    R8TestBuilder r8TestBuilder =
        testForR8(backend)
            .addKeepMainRule(MAIN_CLASS)
            .addKeepRules("-keep,allowobfuscation class * { *; }")
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .addProgramClassFileData(InnerClassNameTestDump.dump(config));

    if (!minify) {
      r8TestBuilder.noMinification();
    }

    R8TestCompileResult r8CompileResult;
    try {
      r8CompileResult =
          r8TestBuilder
              .addOptionsModification(o -> o.testing.allowFailureOnInnerClassErrors = true)
              .compile();
    } catch (CompilationFailedException e) {
      // TODO(b/120639028) R8 does not keep the structure of inner classes.
      assertTrue(
          "b/120639028",
          config == TestNamingConfig.DEFAULT
              || config == TestNamingConfig.DOLLAR2_SEPARATOR
              || config == TestNamingConfig.OUTER_ENDS_WITH_DOLLAR
              || config == TestNamingConfig.$_$_$);
      assertTrue("b/120639028", minify);
      return;
    }
    CodeInspector inspector = r8CompileResult.inspector();
    R8TestRunResult r8RunResult = r8CompileResult.run(MAIN_CLASS);
    switch (config) {
      case DEFAULT:
      case OUTER_ENDS_WITH_DOLLAR:
      case $_$_$:
        if (backend == Backend.CF) {
          runResult.assertSuccessWithOutput(getExpectedNonMinified(config.getInnerClassName()));
        }
        r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        break;
      case DOLLAR2_SEPARATOR:
        if (backend == Backend.CF && minify) {
          // TODO(b/120639028) R8 does not keep the structure of inner classes.
          r8RunResult.assertFailureWithErrorThatMatches(containsString("Malformed class name"));
        } else if (backend == Backend.CF) {
          // $$ as separator and InnerClass as name, results in $InnerClass from getSimpleName...
          String expectedWithDollarOnInnerName =
              getExpectedNonMinified("$" + config.getInnerClassName());
          runResult.assertSuccessWithOutput(expectedWithDollarOnInnerName);
          // When minifying with R8 the classname $InnerName will map to, eg, 'a' and thus the
          // dollar will not appear. This is coincidental and could be seen as an error, in which
          // case R8 should map it to 'a' but with '$$' kept as the separator.
          r8RunResult.assertSuccessWithOutput(
              minify ? getExpectedMinified(inspector) : expectedWithDollarOnInnerName);
        } else {
          // $$ in DEX will not change the InnerName/getSimpleName.
          r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        }
        break;
      case EMTPY_SEPARATOR:
      case UNDERBAR_SEPARATOR:
      case NON_NESTED_INNER:
        if (backend == Backend.CF) {
          // NOTE(b/120597515): These cases should fail, but if they succeed, we have recovered via
          // minification, likely by not using the same separator from output in input.
          // Any non-$ separator results in a runtime exception in getCanonicalName.
          r8RunResult.assertFailureWithErrorThatMatches(containsString("Malformed class name"));
        } else {
          assert backend == Backend.DEX;
          r8RunResult.assertSuccessWithOutput(getExpectedMinified(inspector));
        }
        break;
      default:
        throw new Unreachable("Unexpected test configuration: " + config);
    }
  }

  private static String getExpected(String typeName, String canonicalName, String simpleName) {
    if (TestNamingConfig.isGetTypeNameSupported()) {
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
    String outerClassType = DescriptorUtils.descriptorToJavaType(config.getOuterDescriptor());
    String innerClassType = DescriptorUtils.descriptorToJavaType(config.getInnerDescriptor());
    String outerClassTypeFinal = inspector.clazz(outerClassType).getFinalName();
    String innerClassTypeFinal = inspector.clazz(innerClassType).getFinalName();
    String innerNameFinal =
        !minify
            ? config.getInnerClassName()
            : innerClassTypeFinal.substring(innerClassTypeFinal.length() - 1);
    return getExpected(
        innerClassTypeFinal, outerClassTypeFinal + "." + innerNameFinal, innerNameFinal);
  }
}
