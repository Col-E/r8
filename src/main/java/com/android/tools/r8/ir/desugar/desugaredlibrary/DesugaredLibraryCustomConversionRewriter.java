// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter.vivifiedTypeFor;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizer.conversionEncodedMethod;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfCodeTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryCustomConversionEventConsumer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * L8 specific pass, rewrites or generates custom conversions.
 *
 * <p>In normal set-ups, custom conversions are provided with the types: type <-> rewrittenType.
 * However the compiler uses internally the types: vivifiedType <-> type during compilation. This
 * pass rewrites the custom conversions from type <-> rewrittenType to vivifiedType <-> type so the
 * compilation can look-up and find the methods.
 *
 * <p>In broken set-ups, where the input is missing, instead generates the custom conversions on the
 * classpath since they are assumed to be present in L8.
 */
public class DesugaredLibraryCustomConversionRewriter implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryWrapperSynthesizer synthesizer;
  private final DexString libraryPrefix;

  public DesugaredLibraryCustomConversionRewriter(
      AppView<?> appView, DesugaredLibraryWrapperSynthesizer synthesizer) {
    assert appView.options().isDesugaredLibraryCompilation();
    this.appView = appView;
    this.synthesizer = synthesizer;
    this.libraryPrefix =
        appView
            .dexItemFactory()
            .createString(
                "L"
                    + appView
                        .options()
                        .desugaredLibraryConfiguration
                        .getSynthesizedLibraryClassesPackagePrefix());
  }

  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    Map<DexProgramClass, Set<DexEncodedMethod>> methodsToProcessPerClass =
        readConversionMethodsToConvert(eventConsumer);
    methodsToProcessPerClass.forEach(
        (conversionHolder, conversionsMethods) -> {
          Set<DexEncodedMethod> convertedMethods = convertMethods(conversionsMethods);
          conversionHolder.getMethodCollection().removeMethods(conversionsMethods);
          conversionHolder.addDirectMethods(convertedMethods);
        });
  }

  private Map<DexProgramClass, Set<DexEncodedMethod>> readConversionMethodsToConvert(
      DesugaredLibraryCustomConversionEventConsumer eventConsumer) {
    Map<DexProgramClass, Set<DexEncodedMethod>> methodsToProcessPerClass = new IdentityHashMap<>();
    appView
        .options()
        .desugaredLibraryConfiguration
        .getCustomConversions()
        .forEach(
            (convertedType, conversionHolder) -> {
              DexClass dexClass = appView.definitionFor(conversionHolder);
              if (dexClass == null || dexClass.isNotProgramClass()) {
                generateMissingConversion(eventConsumer, convertedType, conversionHolder);
                return;
              }
              DexProgramClass conversionProgramClass = dexClass.asProgramClass();
              DexType rewrittenType = appView.rewritePrefix.rewrittenType(convertedType, appView);
              Set<DexEncodedMethod> methods =
                  methodsToProcessPerClass.computeIfAbsent(
                      conversionProgramClass, ignored -> Sets.newIdentityHashSet());
              methods.add(
                  conversionEncodedMethod(
                      conversionProgramClass,
                      rewrittenType,
                      convertedType,
                      appView.dexItemFactory()));
              methods.add(
                  conversionEncodedMethod(
                      conversionProgramClass,
                      convertedType,
                      rewrittenType,
                      appView.dexItemFactory()));
            });
    return methodsToProcessPerClass;
  }

  private void generateMissingConversion(
      DesugaredLibraryCustomConversionEventConsumer eventConsumer,
      DexType convertedType,
      DexType conversionHolder) {
    appView
        .options()
        .reporter
        .warning(
            "Missing custom conversion "
                + conversionHolder
                + " from L8 compilation: Cannot convert "
                + convertedType);
    DexType vivifiedType = vivifiedTypeFor(convertedType, appView);
    synthesizer.ensureClasspathCustomConversion(
        vivifiedType, convertedType, eventConsumer, conversionHolder);
    synthesizer.ensureClasspathCustomConversion(
        convertedType, vivifiedType, eventConsumer, conversionHolder);
  }

  private Set<DexEncodedMethod> convertMethods(Set<DexEncodedMethod> conversionsMethods) {
    Set<DexEncodedMethod> newMethods = Sets.newIdentityHashSet();
    CfCodeTypeRewriter cfCodeTypeRewriter = new CfCodeTypeRewriter(appView.dexItemFactory());
    for (DexEncodedMethod conversionMethod : conversionsMethods) {
      Map<DexType, DexType> replacement = computeReplacement(conversionMethod.getReference());
      newMethods.add(cfCodeTypeRewriter.rewriteCfCode(conversionMethod, replacement));
    }
    return newMethods;
  }

  private Map<DexType, DexType> computeReplacement(DexMethod reference) {
    assert reference.getArity() == 1;
    DexType argumentType = reference.getArgumentType(0, true);
    if (isLibraryPrefixed(argumentType)) {
      return ImmutableMap.of(
          argumentType,
          convertDesugaredLibraryToVivifiedType(argumentType, reference.getReturnType()));
    }
    assert isLibraryPrefixed(reference.getReturnType());
    return ImmutableMap.of(
        reference.getReturnType(),
        convertDesugaredLibraryToVivifiedType(reference.getReturnType(), argumentType));
  }

  private boolean isLibraryPrefixed(DexType argumentType) {
    return argumentType.getDescriptor().startsWith(libraryPrefix);
  }

  private DexType convertDesugaredLibraryToVivifiedType(DexType type, DexType oppositeType) {
    if (!isLibraryPrefixed(type)) {
      return type;
    }
    return vivifiedTypeFor(oppositeType, appView);
  }
}
