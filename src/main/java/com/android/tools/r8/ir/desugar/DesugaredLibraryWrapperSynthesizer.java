// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterConstructorCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterThrowRuntimeExceptionCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterVivifiedWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperConversionCfCodeProvider;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// I am responsible for the generation of wrappers used to call library APIs when desugaring
// libraries. Wrappers can be both ways, wrapping the desugarType as a type, or the type as
// a desugar type.
// This file use the vivifiedType -> type, type -> desugarType convention described in the
// DesugaredLibraryAPIConverter class.
// Wrappers contain the following:
// - a single static method convert, which is used by the DesugaredLibraryAPIConverter for
// conversion, it's the main public API (public).
// - a constructor setting the wrappedValue (private).
// - a getter for the wrappedValue (public unwrap()).
// - a single instance field holding the wrapped value (private final).
// - a copy of all implemented methods in the class/interface wrapped. Such methods only do type
// conversions and forward the call to the wrapped type. Parameters and return types are also
// converted.
// Generation of the conversion method in the wrappers is postponed until the compiler knows if the
// reversed wrapper is needed.

// Example of the type wrapper ($-WRP) of java.util.BiFunction at the end of the compilation. I
// omitted
// generic values for simplicity and wrote .... instead of .util.function. Note the difference
// between $-WRP and $-V-WRP wrappers:
// public class j$....BiFunction$-WRP implements java....BiFunction {
//   private final j$....BiFunction wrappedValue;
//   private BiFunction (j$....BiFunction wrappedValue) {
//     this.wrappedValue = wrappedValue;
//   }
//   public R apply(T t, U u) {
//     return wrappedValue.apply(t, u);
//   }
//   public BiFunction andThen(java....Function after) {
//     j$....BiFunction afterConverted = j$....BiFunction$-V-WRP.convert(after);
//     return wrappedValue.andThen(afterConverted);
//   }
//   public static convert(j$....BiFunction function){
//     if (function == null) {
//       return null;
//     }
//     if (function instanceof j$....BiFunction$-V-WRP) {
//       return ((j$....BiFunction$-V-WRP) function).wrappedValue;
//     }
//     return new j$....BiFunction$-WRP(wrappedValue);
//     }
//   }
public class DesugaredLibraryWrapperSynthesizer {

  public static final String WRAPPER_PREFIX = "$r8$wrapper$";
  public static final String TYPE_WRAPPER_SUFFIX = "$-WRP";
  public static final String VIVIFIED_TYPE_WRAPPER_SUFFIX = "$-V-WRP";

  private final AppView<?> appView;
  private final String dexWrapperPrefixString;
  private final DexString dexWrapperPrefixDexString;
  private final Map<DexType, DexType> typeWrappers = new ConcurrentHashMap<>();
  private final Map<DexType, DexType> vivifiedTypeWrappers = new ConcurrentHashMap<>();
  // The invalidWrappers are wrappers with incorrect behavior because of final methods that could
  // not be overridden. Such wrappers are awful because the runtime behavior is undefined and does
  // not raise explicit errors. So we register them here and conversion methods for such wrappers
  // raise a runtime exception instead of generating the wrapper.
  private final Set<DexType> invalidWrappers = Sets.newConcurrentHashSet();
  private final DexItemFactory factory;
  private final DesugaredLibraryAPIConverter converter;

  DesugaredLibraryWrapperSynthesizer(AppView<?> appView, DesugaredLibraryAPIConverter converter) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    dexWrapperPrefixString =
        "L"
            + appView
                .options()
                .desugaredLibraryConfiguration
                .getSynthesizedLibraryClassesPackagePrefix()
            + WRAPPER_PREFIX;
    dexWrapperPrefixDexString = factory.createString(dexWrapperPrefixString);
    this.converter = converter;
  }

  boolean hasSynthesized(DexType type) {
    return type.descriptor.startsWith(dexWrapperPrefixDexString);
  }

  boolean canGenerateWrapper(DexType type) {
    return appView.options().desugaredLibraryConfiguration.getWrapperConversions().contains(type);
  }

  DexType getTypeWrapper(DexType type) {
    // Force create the reverse wrapper.
    getWrapper(type, VIVIFIED_TYPE_WRAPPER_SUFFIX, vivifiedTypeWrappers);
    return getWrapper(type, TYPE_WRAPPER_SUFFIX, typeWrappers);
  }

  DexType getVivifiedTypeWrapper(DexType type) {
    // Force create the reverse wrapper.
    getWrapper(type, TYPE_WRAPPER_SUFFIX, typeWrappers);
    return getWrapper(type, VIVIFIED_TYPE_WRAPPER_SUFFIX, vivifiedTypeWrappers);
  }

  private DexType createWrapperType(DexType type, String suffix) {
    return factory.createType(
        dexWrapperPrefixString + type.toString().replace('.', '$') + suffix + ";");
  }

  private DexType getWrapper(DexType type, String suffix, Map<DexType, DexType> wrappers) {
    assert !type.toString().startsWith(DesugaredLibraryAPIConverter.VIVIFIED_PREFIX);
    return wrappers.computeIfAbsent(
        type,
        t -> {
          assert canGenerateWrapper(type) : type;
          DexType wrapperType = createWrapperType(type, suffix);
          assert converter.canGenerateWrappersAndCallbacks()
                  || appView.definitionFor(wrapperType).isClasspathClass()
              : "Wrapper " + wrapperType + " should have been generated in the enqueuer.";
          return wrapperType;
        });
  }

  private DexClass getValidClassToWrap(DexType type) {
    DexClass dexClass = appView.definitionFor(type);
    // The dexClass should be a library class, so it cannot be null.
    assert dexClass != null;
    assert dexClass.isLibraryClass() || appView.options().isDesugaredLibraryCompilation();
    assert !dexClass.accessFlags.isFinal();
    return dexClass;
  }

  private DexType vivifiedTypeFor(DexType type) {
    return DesugaredLibraryAPIConverter.vivifiedTypeFor(type, appView);
  }

  private DexClass generateTypeWrapper(
      ClassKind classKind, DexClass dexClass, DexType typeWrapperType) {
    DexType type = dexClass.type;
    DexEncodedField wrapperField = synthesizeWrappedValueEncodedField(typeWrapperType, type);
    return synthesizeWrapper(
        classKind,
        vivifiedTypeFor(type),
        dexClass,
        synthesizeVirtualMethodsForTypeWrapper(dexClass, wrapperField),
        generateTypeConversion(type, typeWrapperType),
        wrapperField);
  }

  private DexClass generateVivifiedTypeWrapper(
      ClassKind classKind, DexClass dexClass, DexType vivifiedTypeWrapperType) {
    DexType type = dexClass.type;
    DexEncodedField wrapperField =
        synthesizeWrappedValueEncodedField(vivifiedTypeWrapperType, vivifiedTypeFor(type));
    return synthesizeWrapper(
        classKind,
        type,
        dexClass,
        synthesizeVirtualMethodsForVivifiedTypeWrapper(dexClass, wrapperField),
        generateVivifiedTypeConversion(type, vivifiedTypeWrapperType),
        wrapperField);
  }

  private DexClass synthesizeWrapper(
      ClassKind classKind,
      DexType wrappingType,
      DexClass clazz,
      DexEncodedMethod[] virtualMethods,
      DexEncodedMethod conversionMethod,
      DexEncodedField wrapperField) {
    boolean isItf = clazz.isInterface();
    DexType superType = isItf ? factory.objectType : wrappingType;
    DexTypeList interfaces =
        isItf ? new DexTypeList(new DexType[] {wrappingType}) : DexTypeList.empty();
    return classKind.create(
        wrapperField.holder(),
        Kind.CF,
        new SynthesizedOrigin("Desugared library API Converter", getClass()),
        ClassAccessFlags.fromSharedAccessFlags(
            Constants.ACC_FINAL | Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC),
        superType,
        interfaces,
        clazz.sourceFile,
        null,
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        ClassSignature.noSignature(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY, // No static fields.
        new DexEncodedField[] {wrapperField},
        new DexEncodedMethod[] {synthesizeConstructor(wrapperField.field), conversionMethod},
        virtualMethods,
        factory.getSkipNameValidationForTesting(),
        DexProgramClass::checksumFromType);
  }

  private DexEncodedMethod[] synthesizeVirtualMethodsForVivifiedTypeWrapper(
      DexClass dexClass, DexEncodedField wrapperField) {
    List<DexEncodedMethod> dexMethods = allImplementedMethods(dexClass);
    List<DexEncodedMethod> generatedMethods = new ArrayList<>();
    // Each method should use only types in their signature, but each method the wrapper forwards
    // to should used only vivified types.
    // Generated method looks like:
    // long foo (type, int)
    //   v0 <- arg0;
    //   v1 <- arg1;
    //   v2 <- convertTypeToVivifiedType(v0);
    //   v3 <- wrappedValue.foo(v2,v1);
    //   return v3;
    Set<DexMethod> finalMethods = Sets.newIdentityHashSet();
    for (DexEncodedMethod dexEncodedMethod : dexMethods) {
      DexClass holderClass = appView.definitionFor(dexEncodedMethod.holder());
      boolean isInterface;
      if (holderClass == null) {
        assert appView
            .options()
            .desugaredLibraryConfiguration
            .getEmulateLibraryInterface()
            .containsValue(dexEncodedMethod.holder());
        isInterface = true;
      } else {
        isInterface = holderClass.isInterface();
      }
      DexMethod methodToInstall =
          factory.createMethod(
              wrapperField.holder(), dexEncodedMethod.method.proto, dexEncodedMethod.method.name);
      CfCode cfCode;
      if (dexEncodedMethod.isFinal()) {
        invalidWrappers.add(wrapperField.holder());
        finalMethods.add(dexEncodedMethod.method);
        continue;
      } else {
        cfCode =
            new APIConverterVivifiedWrapperCfCodeProvider(
                    appView,
                    methodToInstall,
                    wrapperField.field,
                    converter,
                isInterface)
                .generateCfCode();
      }
      DexEncodedMethod newDexEncodedMethod =
          newSynthesizedMethod(methodToInstall, dexEncodedMethod, cfCode);
      generatedMethods.add(newDexEncodedMethod);
    }
    return finalizeWrapperMethods(generatedMethods, finalMethods);
  }

  private DexEncodedMethod[] synthesizeVirtualMethodsForTypeWrapper(
      DexClass dexClass, DexEncodedField wrapperField) {
    List<DexEncodedMethod> dexMethods = allImplementedMethods(dexClass);
    List<DexEncodedMethod> generatedMethods = new ArrayList<>();
    // Each method should use only vivified types in their signature, but each method the wrapper
    // forwards
    // to should used only types.
    // Generated method looks like:
    // long foo (type, int)
    //   v0 <- arg0;
    //   v1 <- arg1;
    //   v2 <- convertVivifiedTypeToType(v0);
    //   v3 <- wrappedValue.foo(v2,v1);
    //   return v3;
    Set<DexMethod> finalMethods = Sets.newIdentityHashSet();
    for (DexEncodedMethod dexEncodedMethod : dexMethods) {
      DexClass holderClass = appView.definitionFor(dexEncodedMethod.holder());
      assert holderClass != null || appView.options().isDesugaredLibraryCompilation();
      boolean isInterface = holderClass == null || holderClass.isInterface();
      DexMethod methodToInstall =
          DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature(
              dexEncodedMethod.method, wrapperField.holder(), appView);
      CfCode cfCode;
      if (dexEncodedMethod.isFinal()) {
        invalidWrappers.add(wrapperField.holder());
        finalMethods.add(dexEncodedMethod.method);
        continue;
      } else {
        cfCode =
            new APIConverterWrapperCfCodeProvider(
                    appView, dexEncodedMethod.method, wrapperField.field, converter, isInterface)
                .generateCfCode();
      }
      DexEncodedMethod newDexEncodedMethod =
          newSynthesizedMethod(methodToInstall, dexEncodedMethod, cfCode);
      generatedMethods.add(newDexEncodedMethod);
    }
    return finalizeWrapperMethods(generatedMethods, finalMethods);
  }

  private DexEncodedMethod[] finalizeWrapperMethods(
      List<DexEncodedMethod> generatedMethods, Set<DexMethod> finalMethods) {
    if (finalMethods.isEmpty()) {
      return generatedMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
    }
    // Wrapper is invalid, no need to add the virtual methods.
    reportFinalMethodsInWrapper(finalMethods);
    return DexEncodedMethod.EMPTY_ARRAY;
  }

  private void reportFinalMethodsInWrapper(Set<DexMethod> methods) {
    String[] methodArray =
        methods.stream().map(method -> method.holder + "#" + method.name).toArray(String[]::new);
    appView
        .options()
        .reporter
        .warning(
            new StringDiagnostic(
                "Desugared library API conversion: cannot wrap final methods "
                    + Arrays.toString(methodArray)
                    + ". "
                    + methods.iterator().next().holder
                    + " is marked as invalid and will throw a runtime exception upon conversion."));
  }

  DexEncodedMethod newSynthesizedMethod(
      DexMethod methodToInstall, DexEncodedMethod template, Code code) {
    MethodAccessFlags newFlags = template.accessFlags.copy();
    assert newFlags.isPublic();
    if (code == null) {
      newFlags.setAbstract();
    } else {
      newFlags.unsetAbstract();
    }
    // TODO(b/146114533): Fix inlining in synthetic methods and remove unsetBridge.
    newFlags.unsetBridge();
    newFlags.setSynthetic();
    return new DexEncodedMethod(
        methodToInstall,
        newFlags,
        MethodTypeSignature.noSignature(),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        code,
        true);
  }

  private List<DexEncodedMethod> allImplementedMethods(DexClass libraryClass) {
    LinkedList<DexClass> workList = new LinkedList<>();
    List<DexEncodedMethod> implementedMethods = new ArrayList<>();
    workList.add(libraryClass);
    while (!workList.isEmpty()) {
      DexClass dexClass = workList.removeFirst();
      for (DexEncodedMethod virtualMethod : dexClass.virtualMethods()) {
        if (!virtualMethod.isPrivateMethod()) {
          boolean alreadyAdded = false;
          // This looks quadratic but given the size of the collections met in practice for
          // desugared libraries (Max ~15) it does not matter.
          for (DexEncodedMethod alreadyImplementedMethod : implementedMethods) {
            if (alreadyImplementedMethod.method.match(virtualMethod.method)) {
              alreadyAdded = true;
              continue;
            }
          }
          if (!alreadyAdded) {
            implementedMethods.add(virtualMethod);
          }
        }
      }
      for (DexType itf : dexClass.interfaces.values) {
        DexClass itfClass = appView.definitionFor(itf);
        // Cannot be null in program since we started from a LibraryClass.
        assert itfClass != null || appView.options().isDesugaredLibraryCompilation();
        if (itfClass != null) {
          workList.add(itfClass);
        }
      }
      if (dexClass.superType != factory.objectType) {
        DexClass superClass = appView.definitionFor(dexClass.superType);
        assert superClass != null; // Cannot be null since we started from a LibraryClass.
        workList.add(superClass);
      }
    }
    return implementedMethods;
  }

  private DexField wrappedValueField(DexType holder, DexType fieldType) {
    return factory.createField(holder, fieldType, factory.wrapperFieldName);
  }

  private DexEncodedField synthesizeWrappedValueEncodedField(DexType holder, DexType fieldType) {
    DexField field = wrappedValueField(holder, fieldType);
    // Field is package private to be accessible from convert methods without a getter.
    FieldAccessFlags fieldAccessFlags =
        FieldAccessFlags.fromCfAccessFlags(Constants.ACC_FINAL | Constants.ACC_SYNTHETIC);
    return new DexEncodedField(
        field, fieldAccessFlags, FieldTypeSignature.noSignature(), DexAnnotationSet.empty(), null);
  }

  private DexEncodedMethod synthesizeConstructor(DexField field) {
    DexMethod method =
        factory.createMethod(
            field.holder,
            factory.createProto(factory.voidType, field.type),
            factory.initMethodName);
    return newSynthesizedMethod(
        method,
        Constants.ACC_PRIVATE | Constants.ACC_SYNTHETIC,
        true,
        new APIConverterConstructorCfCodeProvider(appView, field).generateCfCode());
  }

  private DexEncodedMethod newSynthesizedMethod(
      DexMethod methodToInstall, int flags, boolean constructor, Code code) {
    MethodAccessFlags accessFlags = MethodAccessFlags.fromSharedAccessFlags(flags, constructor);
    return new DexEncodedMethod(
        methodToInstall,
        accessFlags,
        MethodTypeSignature.noSignature(),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        code,
        true);
  }

  void finalizeWrappersForL8(
      DexApplication.Builder<?> builder, IRConverter irConverter, ExecutorService executorService)
      throws ExecutionException {
    List<DexProgramClass> synthesizedWrappers = synthesizeWrappers();
    registerAndProcessWrappers(builder, irConverter, executorService, synthesizedWrappers);
  }

  private List<DexProgramClass> synthesizeWrappers() {
    DesugaredLibraryConfiguration conf = appView.options().desugaredLibraryConfiguration;
    for (DexType type : conf.getWrapperConversions()) {
      assert !conf.getCustomConversions().containsKey(type);
      getTypeWrapper(type);
    }
    Map<DexType, DexClass> synthesizedWrappers = new IdentityHashMap<>();
    List<DexProgramClass> additions = new ArrayList<>();
    int total = typeWrappers.size() + vivifiedTypeWrappers.size();
    generateWrappers(
        ClassKind.PROGRAM,
        synthesizedWrappers.keySet(),
        (type, wrapper) -> {
          synthesizedWrappers.put(type, wrapper);
          additions.add(wrapper.asProgramClass());
        });
    assert total == typeWrappers.size() + vivifiedTypeWrappers.size() : "unexpected additions";
    return additions;
  }

  void synthesizeWrappersForClasspath(
      Map<DexType, DexClasspathClass> synthesizedWrappers,
      Consumer<DexClasspathClass> synthesizedCallback) {
    generateWrappers(
        ClassKind.CLASSPATH,
        synthesizedWrappers.keySet(),
        (type, wrapper) -> {
          DexClasspathClass classpathWrapper = wrapper.asClasspathClass();
          synthesizedWrappers.put(type, classpathWrapper);
          synthesizedCallback.accept(classpathWrapper);
        });
  }

  private void generateWrappers(
      ClassKind classKind,
      Set<DexType> synthesized,
      BiConsumer<DexType, DexClass> generatedCallback) {
    while (synthesized.size() != typeWrappers.size() + vivifiedTypeWrappers.size()) {
      for (DexType type : typeWrappers.keySet()) {
        DexType typeWrapperType = typeWrappers.get(type);
        if (!synthesized.contains(typeWrapperType)) {
          DexClass wrapper =
              generateTypeWrapper(classKind, getValidClassToWrap(type), typeWrapperType);
          generatedCallback.accept(typeWrapperType, wrapper);
        }
      }
      for (DexType type : vivifiedTypeWrappers.keySet()) {
        DexType vivifiedTypeWrapperType = vivifiedTypeWrappers.get(type);
        if (!synthesized.contains(vivifiedTypeWrapperType)) {
          DexClass wrapper =
              generateVivifiedTypeWrapper(
                  classKind, getValidClassToWrap(type), vivifiedTypeWrapperType);
          generatedCallback.accept(vivifiedTypeWrapperType, wrapper);
        }
      }
    }
  }

  private void registerAndProcessWrappers(
      DexApplication.Builder<?> builder,
      IRConverter irConverter,
      ExecutorService executorService,
      Collection<DexProgramClass> wrappers)
      throws ExecutionException {
    for (DexProgramClass wrapper : wrappers) {
      builder.addSynthesizedClass(wrapper);
      appView.appInfo().addSynthesizedClass(wrapper, false);
    }
    irConverter.optimizeSynthesizedClasses(wrappers, executorService);
  }

  private DexEncodedMethod generateTypeConversion(DexType type, DexType typeWrapperType) {
    DexType reverse = vivifiedTypeWrappers.get(type);
    assert reverse != null;
    return synthesizeConversionMethod(
        typeWrapperType,
        type,
        type,
        vivifiedTypeFor(type),
        wrappedValueField(reverse, vivifiedTypeFor(type)));
  }

  private DexEncodedMethod generateVivifiedTypeConversion(
      DexType type, DexType vivifiedTypeWrapperType) {
    DexType reverse = typeWrappers.get(type);
    assert reverse != null;
    return synthesizeConversionMethod(
        vivifiedTypeWrapperType,
        type,
        vivifiedTypeFor(type),
        type,
        wrappedValueField(reverse, type));
  }

  private DexEncodedMethod synthesizeConversionMethod(
      DexType holder,
      DexType type,
      DexType argType,
      DexType returnType,
      DexField reverseField) {
    DexMethod method =
        factory.createMethod(
            holder, factory.createProto(returnType, argType), factory.convertMethodName);
    CfCode cfCode;
    if (invalidWrappers.contains(holder)) {
      cfCode =
          new APIConverterThrowRuntimeExceptionCfCodeProvider(
                  appView,
                  factory.createString(
                      "Unsupported conversion for "
                          + type
                          + ". See compilation time warnings for more details."),
                  holder)
              .generateCfCode();
    } else {
      cfCode =
          new APIConverterWrapperConversionCfCodeProvider(
                  appView,
                  argType,
                  reverseField,
                  factory.createField(holder, returnType, factory.wrapperFieldName))
              .generateCfCode();
    }
    return newSynthesizedMethod(
        method,
        Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC,
        false,
        cfCode);
  }
}
