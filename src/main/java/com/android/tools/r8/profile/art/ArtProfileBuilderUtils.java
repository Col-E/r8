// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;


import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;

public class ArtProfileBuilderUtils {

  /**
   * Helper for creating an {@link ArtProfileBuilder} that performs callbacks on the given {@param
   * startupProfileBuilder}.
   */
  public static ArtProfileBuilder createBuilderForArtProfileToStartupProfileConversion(
      StartupProfileBuilder startupProfileBuilder) {
    return new ArtProfileBuilder() {

      @Override
      public ArtProfileBuilder addClassRule(
          Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
        MutableArtProfileClassRule classRule = new MutableArtProfileClassRule();
        classRuleBuilderConsumer.accept(classRule);
        startupProfileBuilder.addStartupClass(
            startupClassBuilder ->
                startupClassBuilder.setClassReference(classRule.getClassReference()));
        return this;
      }

      @Override
      public ArtProfileBuilder addMethodRule(
          Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
        MutableArtProfileMethodRule methodRule = new MutableArtProfileMethodRule();
        methodRuleBuilderConsumer.accept(methodRule);
        startupProfileBuilder.addStartupMethod(
            startupMethodBuilder ->
                startupMethodBuilder.setMethodReference(methodRule.getMethodReference()));
        return this;
      }

      @Override
      public ArtProfileBuilder addHumanReadableArtProfile(
          TextInputStream textInputStream,
          Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
        // The ART profile parser never calls addHumanReadableArtProfile().
        throw new Unreachable();
      }
    };
  }

  static class MutableArtProfileClassRule implements ArtProfileClassRuleBuilder {

    private ClassReference classReference;

    MutableArtProfileClassRule() {}

    public ClassReference getClassReference() {
      return classReference;
    }

    @Override
    public ArtProfileClassRuleBuilder setClassReference(ClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public ArtProfileClassRuleInfo getClassRuleInfo() {
      return ArtProfileClassRuleInfoImpl.empty();
    }

    public void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException {
      writer.write(classReference.getDescriptor());
    }
  }

  static class MutableArtProfileMethodRule implements ArtProfileMethodRuleBuilder {

    private MethodReference methodReference;
    private ArtProfileMethodRuleInfoImpl methodRuleInfo = ArtProfileMethodRuleInfoImpl.empty();

    MutableArtProfileMethodRule() {}

    public MethodReference getMethodReference() {
      return methodReference;
    }

    public ArtProfileMethodRuleInfo getMethodRuleInfo() {
      return methodRuleInfo;
    }

    @Override
    public ArtProfileMethodRuleBuilder setMethodReference(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    @Override
    public ArtProfileMethodRuleBuilder setMethodRuleInfo(
        Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer) {
      ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder =
          ArtProfileMethodRuleInfoImpl.builder();
      methodRuleInfoBuilderConsumer.accept(methodRuleInfoBuilder);
      methodRuleInfo = methodRuleInfoBuilder.build();
      return this;
    }

    public void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException {
      methodRuleInfo.writeHumanReadableFlags(writer);
      writer.write(MethodReferenceUtils.toSmaliString(methodReference));
    }
  }
}
