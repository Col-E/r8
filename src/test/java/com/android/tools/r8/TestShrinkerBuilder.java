// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class TestShrinkerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestShrinkerBuilder<C, B, CR, RR, T>>
    extends TestCompilerBuilder<C, B, CR, RR, T> {

  protected boolean enableTreeShaking = true;
  protected boolean enableOptimization = true;
  protected boolean enableMinification = true;

  TestShrinkerBuilder(TestState state, B builder, Backend backend) {
    super(state, builder, backend);
  }

  @Override
  public boolean isTestShrinkerBuilder() {
    return true;
  }

  @Override
  public T setMinApi(AndroidApiLevel minApiLevel) {
    if (backend == Backend.DEX) {
      return super.setMinApi(minApiLevel.getLevel());
    }
    return self();
  }

  public T treeShaking(boolean enable) {
    enableTreeShaking = enable;
    return self();
  }

  public T noTreeShaking() {
    return treeShaking(false);
  }

  public T optimization(boolean enable) {
    enableOptimization = enable;
    return self();
  }

  public T noOptimization() {
    return optimization(false);
  }

  public T minification(boolean enable) {
    enableMinification = enable;
    return self();
  }

  public T noMinification() {
    return minification(false);
  }

  public T addClassObfuscationDictionary(String... names) throws IOException {
    Path path = getState().getNewTempFolder().resolve("classobfuscationdictionary.txt");
    FileUtils.writeTextFile(path, StringUtils.join(" ", names));
    return addKeepRules("-classobfuscationdictionary " + path.toString());
  }

  public abstract T addDataEntryResources(DataEntryResource... resources);

  public abstract T addKeepRuleFiles(List<Path> files);

  public T addKeepRuleFiles(Path... files) throws IOException {
    return addKeepRuleFiles(Arrays.asList(files));
  }

  public abstract T addKeepRules(Collection<String> rules);

  public T addKeepRules(String... rules) {
    return addKeepRules(Arrays.asList(rules));
  }

  public T addKeepKotlinMetadata() {
    return addKeepRules("-keep class kotlin.Metadata { *; }");
  }

  public T addKeepAllClassesRule() {
    return addKeepRules("-keep class ** { *; }");
  }

  public T addKeepAllClassesRuleWithAllowObfuscation() {
    return addKeepRules("-keep,allowobfuscation class ** { *; }");
  }

  public T addKeepAllInterfacesRule() {
    return addKeepRules("-keep interface ** { *; }");
  }

  public T addKeepClassRules(Class<?>... classes) {
    return addKeepClassRules(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassRules(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz);
    }
    return self();
  }

  public T addKeepClassRulesWithAllowObfuscation(Class<?>... classes) {
    return addKeepClassRulesWithAllowObfuscation(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassRulesWithAllowObfuscation(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep,allowobfuscation class " + clazz);
    }
    return self();
  }

  public T addKeepClassAndMembersRules(Class<?>... classes) {
    return addKeepClassAndMembersRules(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndMembersRules(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz + " { *; }");
    }
    return self();
  }

  public T addKeepClassAndMembersRulesWithAllowObfuscation(Class<?>... classes) {
    return addKeepClassAndMembersRulesWithAllowObfuscation(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndMembersRulesWithAllowObfuscation(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep,allowobfuscation class " + clazz + " { *; }");
    }
    return self();
  }

  public T addKeepClassAndDefaultConstructor(Class<?>... classes) {
    return addKeepClassAndDefaultConstructor(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndDefaultConstructor(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz + " { <init>(); }");
    }
    return self();
  }

  public T addKeepPackageRules(Package pkg) {
    return addKeepRules("-keep class " + pkg.getName() + ".*");
  }

  public T addKeepPackageNamesRule(Package pkg) {
    return addKeepRules("-keeppackagenames " + pkg.getName());
  }

  public T addKeepMainRule(Class<?> mainClass) {
    return addKeepMainRule(mainClass.getTypeName());
  }

  public T addKeepMainRules(Class<?>... mainClasses) {
    for (Class<?> mainClass : mainClasses) {
      this.addKeepMainRule(mainClass);
    }
    return self();
  }

  public T addKeepMainRule(String mainClass) {
    return addKeepRules(
        "-keep class " + mainClass + " { public static void main(java.lang.String[]); }");
  }

  public T addKeepMainRules(List<String> mainClasses) {
    mainClasses.forEach(this::addKeepMainRule);
    return self();
  }

  public T addKeepFeatureMainRule(Class<?> mainClass) {
    return addKeepFeatureMainRule(mainClass.getTypeName());
  }

  public T addKeepFeatureMainRules(Class<?>... mainClasses) {
    for (Class<?> mainClass : mainClasses) {
      this.addKeepFeatureMainRule(mainClass);
    }
    return self();
  }

  public T addKeepFeatureMainRule(String mainClass) {
    return addKeepRules(
        "-keep public class " + mainClass,
        "    implements " + RunInterface.class.getTypeName() + " {",
        "  public void <init>();",
        "  public void run();",
        "}");
  }

  public T addKeepFeatureMainRules(List<String> mainClasses) {
    mainClasses.forEach(this::addKeepFeatureMainRule);
    return self();
  }

  public T addKeepMethodRules(Class<?> clazz, String... methodSignatures) {
    StringBuilder sb = new StringBuilder();
    sb.append("-keep class " + clazz.getTypeName() + " {\n");
    for (String methodSignature : methodSignatures) {
      sb.append("  " + methodSignature + ";\n");
    }
    sb.append("}");
    addKeepRules(sb.toString());
    return self();
  }

  public T addKeepMethodRules(MethodReference... methods) {
    for (MethodReference method : methods) {
      addKeepRules(
          "-keep class "
              + method.getHolderClass().getTypeName()
              + " { "
              + getMethodLine(method)
              + " }");
    }
    return self();
  }

  public T allowAccessModification() {
    return allowAccessModification(true);
  }

  public T allowAccessModification(boolean allowAccessModification) {
    if (allowAccessModification) {
      return addKeepRules("-allowaccessmodification");
    }
    return self();
  }

  public T addKeepAttributes(String... attributes) {
    return addKeepRules("-keepattributes " + String.join(",", attributes));
  }

  public T addKeepAttributeInnerClassesAndEnclosingMethod() {
    return addKeepAttributes(
        ProguardKeepAttributes.INNER_CLASSES, ProguardKeepAttributes.ENCLOSING_METHOD);
  }

  public T addKeepAttributeLineNumberTable() {
    return addKeepAttributes(ProguardKeepAttributes.LINE_NUMBER_TABLE);
  }

  public T addKeepAttributeSourceFile() {
    return addKeepAttributes(ProguardKeepAttributes.SOURCE_FILE);
  }

  public T addKeepRuntimeVisibleAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS);
  }

  public T addKeepRuntimeVisibleParameterAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
  }

  public T addKeepAllAttributes() {
    return addKeepAttributes("*");
  }

  public abstract T addApplyMapping(String proguardMap);

  private static String getMethodLine(MethodReference method) {
    // Should we encode modifiers in method references?
    StringBuilder builder = new StringBuilder();
    builder
        .append(method.getReturnType() == null ? "void" : method.getReturnType().getTypeName())
        .append(' ')
        .append(method.getMethodName())
        .append("(");
    boolean first = true;
    for (TypeReference parameterType : method.getFormalTypes()) {
      if (!first) {
        builder.append(", ");
      }
      builder.append(parameterType.getTypeName());
      first = false;
    }
    return builder.append(");").toString();
  }
}
