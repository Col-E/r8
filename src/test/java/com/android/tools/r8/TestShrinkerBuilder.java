// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class TestShrinkerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestShrinkerBuilder<C, B, CR, RR, T>>
    extends TestCompilerBuilder<C, B, CR, RR, T> {

  protected OptionalBool enableTreeShaking = OptionalBool.UNKNOWN;
  protected OptionalBool enableMinification = OptionalBool.UNKNOWN;

  private final Set<Class<? extends Annotation>> addedTestingAnnotations =
      Sets.newIdentityHashSet();

  TestShrinkerBuilder(TestState state, B builder, Backend backend) {
    super(state, builder, backend);
  }

  public boolean isProguardTestBuilder() {
    return false;
  }

  public boolean isR8TestBuilder() {
    return false;
  }

  public boolean isR8CompatTestBuilder() {
    return false;
  }

  @Override
  public boolean isTestShrinkerBuilder() {
    return true;
  }

  // TODO(b/270021825): Look into if we can assert backend is DEX.
  @Override
  public T setMinApi(AndroidApiLevel minApiLevel) {
    return backend == Backend.DEX ? super.setMinApi(minApiLevel.getLevel()) : self();
  }

  @Override
  public T setMinApi(TestParameters parameters) {
    if (backend.isCf()) {
      parameters.assertIsRepresentativeApiLevelForRuntime();
      return self();
    } else {
      assert backend.isDex();
      return super.setMinApi(parameters);
    }
  }

  // TODO(b/270021825): Look into if we can assert backend is DEX.
  @Override
  public T setMinApi(int minApiLevel) {
    return super.setMinApi(minApiLevel);
  }

  @Override
  protected int getMinApiLevel() {
    return backend == Backend.DEX ? super.getMinApiLevel() : -1;
  }

  public T treeShaking(boolean enable) {
    enableTreeShaking = OptionalBool.of(enable);
    return self();
  }

  public T noTreeShaking() {
    return treeShaking(false);
  }

  public T minification(boolean enable) {
    enableMinification = OptionalBool.of(enable);
    return self();
  }

  @Deprecated
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

  public T addKeepRuleFiles(Path... files) {
    return addKeepRuleFiles(Arrays.asList(files));
  }

  public abstract T addKeepRules(Collection<String> rules);

  public T addKeepRules(String... rules) {
    return addKeepRules(Arrays.asList(rules));
  }

  public T addDontObfuscate() {
    return addKeepRules("-dontobfuscate");
  }

  public T addDontObfuscate(Class<?> clazz) {
    return addKeepRules(
        "-keep,allowaccessmodification,allowannotationremoval,allowoptimization,allowshrinking"
            + " class "
            + clazz.getTypeName());
  }

  public T addDontOptimize() {
    return addKeepRules("-dontoptimize");
  }

  public T addDontShrink() {
    return addKeepRules("-dontshrink");
  }

  public T addDontNote(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addDontNote(clazz.getTypeName());
    }
    return self();
  }

  public T addDontNote(Collection<String> classes) {
    for (String clazz : classes) {
      addKeepRules("-dontnote " + clazz);
    }
    return self();
  }

  public T addDontNote(String... classes) {
    return addDontNote(Arrays.asList(classes));
  }

  public T addDontWarn(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addDontWarn(clazz.getTypeName());
    }
    return self();
  }

  public T addDontWarn(Collection<String> classes) {
    for (String clazz : classes) {
      addKeepRules("-dontwarn " + clazz);
    }
    return self();
  }

  public T addDontWarn(String... classes) {
    return addDontWarn(Arrays.asList(classes));
  }

  public T addDontWarnGoogle() {
    return addDontWarn("com.google.**");
  }

  public T addDontWarnJavax() {
    return addDontWarn("javax.**");
  }

  public T addDontWarnJavaxNullableAnnotation() {
    return addDontWarn("javax.annotation.Nullable");
  }

  public T addDontWarnJavaLangInvokeLambdaMetadataFactory() {
    return addDontWarn("java.lang.invoke.LambdaMetafactory");
  }

  public T addIgnoreWarnings() {
    return addIgnoreWarnings(true);
  }

  public T addIgnoreWarnings(boolean condition) {
    return condition ? addKeepRules("-ignorewarnings") : self();
  }

  public T addKeepKotlinMetadata() {
    return addKeepRules("-keepkotlinmetadata");
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

  private final List<String> keepModifiers =
      ImmutableList.of(
          // TODO: Add allowannotationremoval (currently requires options.isTestingOptionsEnabled())
          // TODO: Add (optional) allowshrinking as well?
          "allowobfuscation", "allowoptimization", "allowrepackage");

  public T addKeepPermittedSubclasses(Class<?>... classes) {
    return addKeepPermittedSubclasses(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepPermittedSubclasses(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep," + String.join(",", keepModifiers) + " class " + clazz);
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
    return addKeepPackageNamesRule(pkg.getName());
  }

  public T addKeepPackageNamesRule(String packageName) {
    return addKeepRules("-keeppackagenames " + packageName);
  }

  public T addKeepMainRule(Class<?> mainClass) {
    return addKeepMainRule(mainClass.getTypeName());
  }

  public T addKeepMainRule(ClassReference mainClass) {
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

  public T addKeepEnumsRule() {
    addKeepRules(
        StringUtils.lines(
            "-keepclassmembers enum * {",
            "    public static **[] values();",
            "    public static ** valueOf(java.lang.String);",
            "}"));
    return self();
  }

  public T addPrintSeeds() {
    return addKeepRules("-printseeds");
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
    return addKeepAttributes(Arrays.asList(attributes));
  }

  public T addKeepAttributes(List<String> attributes) {
    return addKeepRules("-keepattributes " + String.join(",", attributes));
  }

  public T addKeepAttributeAnnotationDefault() {
    return addKeepAttributes(ProguardKeepAttributes.ANNOTATION_DEFAULT);
  }

  public T addKeepAttributeExceptions() {
    return addKeepAttributes(ProguardKeepAttributes.EXCEPTIONS);
  }

  public T addKeepAttributeInnerClassesAndEnclosingMethod() {
    return addKeepAttributes(
        ProguardKeepAttributes.INNER_CLASSES, ProguardKeepAttributes.ENCLOSING_METHOD);
  }

  public T addKeepAttributeLineNumberTable() {
    return addKeepAttributes(ProguardKeepAttributes.LINE_NUMBER_TABLE);
  }

  public T addKeepAttributeLocalVariableTable() {
    return addKeepAttributes(ProguardKeepAttributes.LOCAL_VARIABLE_TABLE);
  }

  public T addKeepAttributeSignature() {
    return addKeepAttributes(ProguardKeepAttributes.SIGNATURE);
  }

  public T addKeepAttributeSourceFile() {
    return addKeepAttributes(ProguardKeepAttributes.SOURCE_FILE);
  }

  public T addKeepRuntimeInvisibleAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS);
  }

  public T addKeepRuntimeInvisibleParameterAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);
  }

  public T addKeepRuntimeVisibleAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS);
  }

  public T addKeepRuntimeVisibleParameterAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
  }

  public T addKeepRuntimeVisibleTypeAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
  }

  public T addKeepRuntimeInvisibleTypeAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
  }

  public T addKeepAttributePermittedSubclasses() {
    return addKeepAttributes(ProguardKeepAttributes.PERMITTED_SUBCLASSES);
  }

  public T addKeepAllAttributes() {
    return addKeepAttributes("*");
  }

  public abstract T addApplyMapping(String proguardMap);

  public final T addAlwaysClassInlineAnnotation() {
    return addTestingAnnotation(AlwaysClassInline.class);
  }

  public final T addAlwaysInliningAnnotations() {
    return addTestingAnnotation(AlwaysInline.class);
  }

  public final T addAssumeNotNullAnnotation() {
    return addTestingAnnotation(AssumeNotNull.class);
  }

  public final T addAssumeNoClassInitializationSideEffectsAnnotation() {
    return addTestingAnnotation(AssumeNoClassInitializationSideEffects.class);
  }

  public final T addAssumeNoSideEffectsAnnotations() {
    return addTestingAnnotation(AssumeNoSideEffects.class);
  }

  public final T addCheckEnumUnboxedAnnotation() {
    return addTestingAnnotation(CheckEnumUnboxed.class);
  }

  public final T addConstantArgumentAnnotations() {
    return addTestingAnnotation(KeepConstantArguments.class);
  }

  public final T addInliningAnnotations() {
    return addTestingAnnotation(NeverInline.class);
  }

  public final T addKeepAnnotation() {
    return addTestingAnnotation(Keep.class);
  }

  public final T addKeepUnusedReturnValueAnnotation() {
    return addTestingAnnotation(KeepUnusedReturnValue.class);
  }

  public final T addMemberValuePropagationAnnotations() {
    return addTestingAnnotation(NeverPropagateValue.class);
  }

  public final T addNeverClassInliningAnnotations() {
    return addTestingAnnotation(NeverClassInline.class);
  }

  public final T addNeverReprocessClassInitializerAnnotations() {
    return addTestingAnnotation(NeverReprocessClassInitializer.class);
  }

  public final T addNeverReprocessMethodAnnotations() {
    return addTestingAnnotation(NeverReprocessMethod.class);
  }

  public final T addNeverSingleCallerInlineAnnotations() {
    return addTestingAnnotation(NeverSingleCallerInline.class);
  }

  public final T addNoAccessModificationAnnotation() {
    return addTestingAnnotation(NoAccessModification.class);
  }

  public final T addNoFieldTypeStrengtheningAnnotation() {
    return addTestingAnnotation(NoFieldTypeStrengthening.class);
  }

  public final T addNoHorizontalClassMergingAnnotations() {
    return addTestingAnnotation(NoHorizontalClassMerging.class);
  }

  public final T addNoInliningOfDefaultInitializerAnnotation() {
    return addTestingAnnotation(NoInliningOfDefaultInitializer.class);
  }

  public final T addNoMethodStaticizingAnnotation() {
    return addTestingAnnotation(NoMethodStaticizing.class);
  }

  public final T addNoParameterReorderingAnnotation() {
    return addTestingAnnotation(NoParameterReordering.class);
  }

  public final T addNoParameterTypeStrengtheningAnnotation() {
    return addTestingAnnotation(NoParameterTypeStrengthening.class);
  }

  public final T addNoRedundantFieldLoadEliminationAnnotation() {
    return addTestingAnnotation(NoRedundantFieldLoadElimination.class);
  }

  public final T addNoReturnTypeStrengtheningAnnotation() {
    return addTestingAnnotation(NoReturnTypeStrengthening.class);
  }

  public final T addNoUnusedInterfaceRemovalAnnotations() {
    return addTestingAnnotation(NoUnusedInterfaceRemoval.class);
  }

  public final T addNoVerticalClassMergingAnnotations() {
    return addTestingAnnotation(NoVerticalClassMerging.class);
  }

  public final T addReprocessClassInitializerAnnotations() {
    return addTestingAnnotation(ReprocessClassInitializer.class);
  }

  public final T addReprocessMethodAnnotations() {
    return addTestingAnnotation(ReprocessMethod.class);
  }

  public final T addSideEffectAnnotations() {
    return addTestingAnnotation(AssumeMayHaveSideEffects.class);
  }

  public final T addUnusedArgumentAnnotations() {
    return addTestingAnnotation(KeepUnusedArguments.class);
  }

  private T addTestingAnnotation(Class<? extends Annotation> clazz) {
    return addedTestingAnnotations.add(clazz) ? addProgramClasses(clazz) : self();
  }

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
