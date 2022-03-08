// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.vivifiedTypeFor;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.EnumConversionCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticClasspathClassBuilder;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder.SyntheticCodeGenerator;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.google.common.collect.Iterables;

public class DesugaredLibraryEnumConversionSynthesizer {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public DesugaredLibraryEnumConversionSynthesizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  private void buildEnumConvert(
      SyntheticMethodBuilder builder,
      DexType src,
      DexType dest,
      SyntheticCodeGenerator codeGenerator) {
    builder
        .setName(factory.convertMethodName)
        .setProto(factory.createProto(dest, src))
        .setAccessFlags(
            MethodAccessFlags.fromCfAccessFlags(
                Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC | Constants.ACC_STATIC, false))
        // Will be traced by the enqueuer.
        .disableAndroidApiLevelCheck()
        .setCode(codeGenerator);
  }

  private void buildEnumMethodsWithCode(
      SyntheticProgramClassBuilder builder,
      Iterable<DexEncodedField> enumFields,
      DexType enumType,
      DexType convertType) {
    builder
        .addMethod(
            methodBuilder ->
                buildEnumConvert(
                    methodBuilder,
                    enumType,
                    convertType,
                    codeSynthesizor ->
                        new EnumConversionCfCodeProvider(
                                appView,
                                codeSynthesizor.getHolderType(),
                                enumFields,
                                enumType,
                                convertType)
                            .generateCfCode()))
        .addMethod(
            methodBuilder ->
                buildEnumConvert(
                    methodBuilder,
                    convertType,
                    enumType,
                    codeSynthesizor ->
                        new EnumConversionCfCodeProvider(
                                appView,
                                codeSynthesizor.getHolderType(),
                                enumFields,
                                convertType,
                                enumType)
                            .generateCfCode()));
  }

  private void buildEnumMethodsWithoutCode(
      SyntheticClasspathClassBuilder builder, DexType enumType, DexType convertType) {
    builder
        .addMethod(
            methodBuilder ->
                buildEnumConvert(methodBuilder, enumType, convertType, ignored -> null))
        .addMethod(
            methodBuilder ->
                buildEnumConvert(methodBuilder, convertType, enumType, ignored -> null));
  }

  DexMethod ensureEnumConversionMethod(
      DexClass clazz,
      DexType srcType,
      DexType destType,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    DexClass enumConversion = ensureEnumConversionClass(clazz, eventConsumer);
    DexMethod method =
        factory.createMethod(
            enumConversion.type, factory.createProto(destType, srcType), factory.convertMethodName);
    assert enumConversion.lookupDirectMethod(method) != null;
    return method;
  }

  DexMethod getExistingProgramEnumConversionMethod(
      DexClass clazz, DexType srcType, DexType destType) {
    DexProgramClass enumConversion =
        appView
            .getSyntheticItems()
            .getExistingFixedClass(SyntheticKind.ENUM_CONVERSION, clazz, appView);
    DexMethod method =
        factory.createMethod(
            enumConversion.type, factory.createProto(destType, srcType), factory.convertMethodName);
    assert enumConversion.lookupProgramMethod(method) != null;
    return method;
  }

  DexProgramClass ensureProgramEnumConversionClass(
      DexClass context, CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    assert eventConsumer != null;
    assert context.isProgramClass();
    DexType type = context.type;
    DexType vivifiedType = vivifiedTypeFor(context.type, appView);
    assert appView.options().isDesugaredLibraryCompilation();
    DexProgramClass programContext = context.asProgramClass();
    Iterable<DexEncodedField> enumFields =
        Iterables.filter(programContext.staticFields(), DexEncodedField::isEnum);
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            SyntheticKind.ENUM_CONVERSION,
            programContext,
            appView,
            builder -> buildEnumMethodsWithCode(builder, enumFields, type, vivifiedType),
            eventConsumer::acceptEnumConversionProgramClass);
  }

  private DexClass ensureEnumConversionClass(
      DexClass context, DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    assert eventConsumer != null;
    if (context.isProgramClass()) {
      return appView
          .getSyntheticItems()
          .getExistingFixedClass(SyntheticKind.ENUM_CONVERSION, context, appView);
    }
    DexType type = context.type;
    DexType vivifiedType = vivifiedTypeFor(context.type, appView);
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            SyntheticKind.ENUM_CONVERSION,
            context.asClasspathOrLibraryClass(),
            appView,
            builder -> buildEnumMethodsWithoutCode(builder, type, vivifiedType),
            eventConsumer::acceptEnumConversionClasspathClass);
  }
}
