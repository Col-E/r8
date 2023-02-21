// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class TestAppViewBuilder {

  private AndroidApp.Builder builder = AndroidApp.builder();
  private List<Function<DexItemFactory, List<ProguardConfigurationRule>>> rules = new ArrayList<>();
  private List<Consumer<InternalOptions>> optionModifications = new ArrayList<>();

  public static TestAppViewBuilder builder() {
    return new TestAppViewBuilder();
  }

  private TestAppViewBuilder() {}

  public TestAppViewBuilder addProgramClasses(Class<?>... classes) {
    return addProgramClasses(Arrays.asList(classes));
  }

  public TestAppViewBuilder addProgramClasses(Collection<Class<?>> classes) {
    classes.forEach(clazz -> builder.addProgramFile(ToolHelper.getClassFileForTestClass(clazz)));
    return this;
  }

  public TestAppViewBuilder addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public TestAppViewBuilder addProgramClassFileData(Collection<byte[]> classes) {
    builder.addClassProgramData(classes);
    return this;
  }

  public TestAppViewBuilder addAndroidApp(AndroidApp app) {
    app.getProgramResourceProviders().forEach(builder::addProgramResourceProvider);
    app.getClasspathResourceProviders().forEach(builder::addClasspathResourceProvider);
    app.getLibraryResourceProviders().forEach(builder::addLibraryResourceProvider);
    assert !app.hasMainDexList() : "todo";
    return this;
  }

  public TestAppViewBuilder addKeepAllRule() {
    rules = null;
    return this;
  }

  public TestAppViewBuilder addKeepMainRule(Class<?> mainClass) {
    return addKeepRuleBuilder(
        factory -> TestBase.buildKeepRuleForClassAndMethods(mainClass, factory));
  }

  public TestAppViewBuilder addKeepRuleBuilder(
      Function<DexItemFactory, List<ProguardConfigurationRule>> ruleBuilder) {
    if (rules != null) {
      rules.add(ruleBuilder);
    }
    return this;
  }

  public TestAppViewBuilder addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    optionModifications.add(optionsConsumer);
    return this;
  }

  public AppView<AppInfoWithLiveness> buildWithLiveness() throws Exception {
    return TestBase.computeAppViewWithLiveness(
        builder.build(),
        (rules == null
            ? null
            : factory ->
                TestBase.buildConfigForRules(
                    factory, ListUtils.flatMap(rules, r -> r.apply(factory)))),
        options -> optionModifications.forEach(consumer -> consumer.accept(options)));
  }

  public TestAppViewBuilder setMinApi(AndroidApiLevel minApi) {
    optionModifications.add(options -> options.setMinApiLevel(minApi));
    return this;
  }

  public TestAppViewBuilder setMinApi(TestParameters parameters) {
    parameters.configureApiLevel(this);
    return this;
  }

  public TestAppViewBuilder addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public TestAppViewBuilder addClasspathClasses(Collection<Class<?>> classes) {
    classes.forEach(clazz -> addClasspathFiles(ToolHelper.getClassFileForTestClass(clazz)));
    return this;
  }

  public TestAppViewBuilder addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public TestAppViewBuilder addClasspathFiles(List<Path> files) {
    builder.addClasspathFiles(files);
    return this;
  }

  public TestAppViewBuilder addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  public TestAppViewBuilder addLibraryFiles(List<Path> files) {
    builder.addLibraryFiles(files);
    return this;
  }

  public TestAppViewBuilder addTestingAnnotations() {
    return addProgramClasses(TestBuilder.getTestingAnnotations());
  }
}
