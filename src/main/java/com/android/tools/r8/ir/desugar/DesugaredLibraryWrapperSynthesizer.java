// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.CfDesugaredLibraryAPIConversionSourceCodeProvider.CfAPIConverterConstructorCodeProvider;
import com.android.tools.r8.ir.synthetic.CfDesugaredLibraryAPIConversionSourceCodeProvider.CfAPIConverterVivifiedWrapperCodeProvider;
import com.android.tools.r8.ir.synthetic.CfDesugaredLibraryAPIConversionSourceCodeProvider.CfAPIConverterWrapperCodeProvider;
import com.android.tools.r8.ir.synthetic.CfDesugaredLibraryAPIConversionSourceCodeProvider.CfAPIConverterWrapperConversionCodeProvider;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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

  public static final String TYPE_WRAPPER_SUFFIX = "$-WRP";
  public static final String VIVIFIED_TYPE_WRAPPER_SUFFIX = "$-V-WRP";

  private final AppView<?> appView;
  private final Map<DexType, Pair<DexType, DexProgramClass>> typeWrappers =
      new ConcurrentHashMap<>();
  private final Map<DexType, Pair<DexType, DexProgramClass>> vivifiedTypeWrappers =
      new ConcurrentHashMap<>();
  private final Set<DexType> generatedWrappers = Sets.newConcurrentHashSet();
  private final DexItemFactory factory;
  private final DesugaredLibraryAPIConverter converter;

  public DesugaredLibraryWrapperSynthesizer(
      AppView<?> appView, DesugaredLibraryAPIConverter converter) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.converter = converter;
  }

  public boolean hasSynthesized(DexType type) {
    return generatedWrappers.contains(type);
  }

  // Wrapper initial generation section.
  // 1. Generate wrappers without conversion methods.
  // 2. Compute wrapper types.

  public boolean canGenerateWrapper(DexType type) {
    DexClass dexClass = appView.definitionFor(type);
    if (dexClass == null) {
      return false;
    }
    // TODO(b/134732760): Support Abstract class for clock, maybe concrete class for Optional.
    return dexClass.isLibraryClass() && dexClass.isInterface();
  }

  public DexType getTypeWrapper(DexType type) {
    return getWrapper(type, TYPE_WRAPPER_SUFFIX, typeWrappers, this::generateTypeWrapper);
  }

  public DexType getVivifiedTypeWrapper(DexType type) {
    return getWrapper(
        type,
        VIVIFIED_TYPE_WRAPPER_SUFFIX,
        vivifiedTypeWrappers,
        this::generateVivifiedTypeWrapper);
  }

  private DexType getWrapper(
      DexType type,
      String suffix,
      Map<DexType, Pair<DexType, DexProgramClass>> wrappers,
      BiFunction<DexClass, DexType, DexProgramClass> wrapperGenerator) {
    // Answers the DexType of the wrapper. Generate the wrapper DexProgramClass if not already done,
    // except the conversions methods. Conversion method generation is postponed to know if the
    // reverse wrapper is present at generation time.
    // We generate the type while locking the concurrent hash map, but we release the lock before
    // generating the actual class to avoid locking for too long (hence the Pair).
    assert !type.toString().startsWith(DesugaredLibraryAPIConverter.VIVIFIED_PREFIX);
    Box<Boolean> toGenerate = new Box<>(false);
    Pair<DexType, DexProgramClass> pair =
        wrappers.computeIfAbsent(
            type,
            t -> {
              toGenerate.set(true);
              DexType wrapperType =
                  factory.createType(
                      DescriptorUtils.javaTypeToDescriptor(type.toString() + suffix));
              generatedWrappers.add(wrapperType);
              return new Pair<>(wrapperType, null);
            });
    if (toGenerate.get()) {
      assert pair.getSecond() == null;
      DexClass dexClass = appView.definitionFor(type);
      // The dexClass should be a library class, so it cannot be null.
      assert dexClass != null && dexClass.isLibraryClass();
      pair.setSecond(wrapperGenerator.apply(dexClass, pair.getFirst()));
    }
    return pair.getFirst();
  }

  public DexProgramClass generateTypeWrapper(DexClass dexClass, DexType typeWrapperType) {
    DexType type = dexClass.type;
    DexEncodedField wrapperField = synthetizeWrappedValueField(typeWrapperType, type);
    return synthesizeWrapper(
        converter.vivifiedTypeFor(type),
        dexClass.sourceFile,
        synthetiseVirtualMethodsForTypeWrapper(dexClass.asLibraryClass(), wrapperField),
        wrapperField);
  }

  public DexProgramClass generateVivifiedTypeWrapper(
      DexClass dexClass, DexType vivifiedTypeWrapperType) {
    DexType type = dexClass.type;
    DexEncodedField wrapperField =
        synthetizeWrappedValueField(vivifiedTypeWrapperType, converter.vivifiedTypeFor(type));
    return synthesizeWrapper(
        type,
        dexClass.sourceFile,
        synthetiseVirtualMethodsForVivifiedTypeWrapper(dexClass.asLibraryClass(), wrapperField),
        wrapperField);
  }

  private DexProgramClass synthesizeWrapper(
      DexType wrappingType,
      DexString sourceFile,
      DexEncodedMethod[] virtualMethods,
      DexEncodedField wrapperField) {
    // TODO(b/134732760): support abstract class in addition to interfaces.
    return new DexProgramClass(
        wrapperField.field.holder,
        null,
        new SynthesizedOrigin("Desugared library API Converter", getClass()),
        ClassAccessFlags.fromSharedAccessFlags(
            Constants.ACC_FINAL | Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC),
        factory.objectType,
        new DexTypeList(new DexType[] {wrappingType}),
        sourceFile,
        null,
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY, // No static fields.
        new DexEncodedField[] {wrapperField},
        new DexEncodedMethod[] {
          synthetizeConstructor(wrapperField.field)
        }, // Conversions methods will be added later.
        virtualMethods,
        factory.getSkipNameValidationForTesting(),
        Collections.emptyList());
  }

  private DexEncodedMethod[] synthetiseVirtualMethodsForVivifiedTypeWrapper(
      DexLibraryClass dexClass, DexEncodedField wrapperField) {
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
    for (DexEncodedMethod dexEncodedMethod : dexMethods) {
      DexMethod methodToInstall =
          factory.createMethod(
              wrapperField.field.holder,
              dexEncodedMethod.method.proto,
              dexEncodedMethod.method.name);
      DexEncodedMethod newDexEncodedMethod =
          newSynthesizedMethod(methodToInstall, dexEncodedMethod);
      CfCode cfCode =
          new CfAPIConverterVivifiedWrapperCodeProvider(
                  newDexEncodedMethod,
                  newDexEncodedMethod.method,
                  appView,
                  newDexEncodedMethod.method,
                  wrapperField.field,
                  converter)
              .getCfCode();
      newDexEncodedMethod.setCode(cfCode, appView);
      generatedMethods.add(newDexEncodedMethod);
    }
    return generatedMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private DexEncodedMethod[] synthetiseVirtualMethodsForTypeWrapper(
      DexLibraryClass dexClass, DexEncodedField wrapperField) {
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
    for (DexEncodedMethod dexEncodedMethod : dexMethods) {

      DexMethod methodToInstall =
          methodWithVivifiedTypeInSignature(dexEncodedMethod.method, wrapperField.field.holder);
      DexEncodedMethod newDexEncodedMethod =
          newSynthesizedMethod(methodToInstall, dexEncodedMethod);
      CfCode cfCode =
          new CfAPIConverterWrapperCodeProvider(
                  newDexEncodedMethod,
                  newDexEncodedMethod.method,
                  appView,
                  dexEncodedMethod.method,
                  wrapperField.field,
                  converter)
              .getCfCode();
      newDexEncodedMethod.setCode(cfCode, appView);
      generatedMethods.add(newDexEncodedMethod);
    }
    return generatedMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private DexMethod methodWithVivifiedTypeInSignature(DexMethod originalMethod, DexType holder) {
    DexType[] newParameters = originalMethod.proto.parameters.values.clone();
    int index = 0;
    for (DexType param : originalMethod.proto.parameters.values) {
      if (appView.rewritePrefix.hasRewrittenType(param)) {
        newParameters[index] = converter.vivifiedTypeFor(param);
      }
      index++;
    }
    DexType returnType = originalMethod.proto.returnType;
    DexType newReturnType =
        appView.rewritePrefix.hasRewrittenType(returnType)
            ? converter.vivifiedTypeFor(returnType)
            : returnType;
    DexProto newProto = factory.createProto(newReturnType, newParameters);
    return factory.createMethod(holder, newProto, originalMethod.name);
  }

  private DexEncodedMethod newSynthesizedMethod(
      DexMethod methodToInstall, DexEncodedMethod template) {
    MethodAccessFlags newFlags = template.accessFlags.copy();
    assert newFlags.isPublic();
    newFlags.unsetAbstract();
    newFlags.setSynthetic();
    return new DexEncodedMethod(
        methodToInstall,
        newFlags,
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        null);
  }

  private List<DexEncodedMethod> allImplementedMethods(DexLibraryClass libraryClass) {
    // TODO(b/134732760): Deal with Abstract class for clock and class for Optional.
    assert libraryClass.isInterface();
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
            if (alreadyImplementedMethod.method.proto == virtualMethod.method.proto
                && alreadyImplementedMethod.method.name == virtualMethod.method.name) {
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
        assert itfClass != null; // Cannot be null since we started from a LibraryClass.
        workList.add(itfClass);
      }
      if (dexClass.superType != factory.objectType) {
        DexClass superClass = appView.definitionFor(dexClass.superType);
        assert superClass != null; // Cannot be null since we started from a LibraryClass.
        workList.add(superClass);
      }
    }
    return implementedMethods;
  }

  private DexEncodedField synthetizeWrappedValueField(DexType holder, DexType fieldType) {
    DexField field = factory.createField(holder, fieldType, factory.wrapperFieldName);
    FieldAccessFlags fieldAccessFlags =
        FieldAccessFlags.fromCfAccessFlags(Constants.ACC_FINAL | Constants.ACC_PRIVATE);
    return new DexEncodedField(field, fieldAccessFlags, DexAnnotationSet.empty(), null);
  }

  private DexEncodedMethod synthetizeConstructor(DexField field) {
    DexMethod method =
        factory.createMethod(
            field.holder,
            factory.createProto(factory.voidType, field.type),
            factory.initMethodName);
    DexEncodedMethod dexEncodedMethod =
        newSynthesizedMethod(method, Constants.ACC_PRIVATE | Constants.ACC_SYNTHETIC, true);
    dexEncodedMethod.setCode(
        new CfAPIConverterConstructorCodeProvider(dexEncodedMethod, method, appView, field)
            .getCfCode(),
        appView);
    return dexEncodedMethod;
  }

  private DexEncodedMethod newSynthesizedMethod(
      DexMethod methodToInstall, int flags, boolean constructor) {
    MethodAccessFlags accessFlags = MethodAccessFlags.fromSharedAccessFlags(flags, constructor);
    return new DexEncodedMethod(
        methodToInstall,
        accessFlags,
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        null);
  }

  // Wrapper finalization section.
  // 1. Generate conversions methods (convert(type)).
  // 2. Add the synthesized classes.
  // 3. Process all methods.

  public void finalizeWrappers(
      DexApplication.Builder<?> builder, IRConverter irConverter, ExecutorService executorService)
      throws ExecutionException {
    finalizeWrappers(
        builder, irConverter, executorService, typeWrappers, this::generateTypeConversions);
    finalizeWrappers(
        builder,
        irConverter,
        executorService,
        vivifiedTypeWrappers,
        this::generateVivifiedTypeConversions);
  }

  private void finalizeWrappers(
      DexApplication.Builder<?> builder,
      IRConverter irConverter,
      ExecutorService executorService,
      Map<DexType, Pair<DexType, DexProgramClass>> wrappers,
      BiConsumer<DexType, DexProgramClass> generateConversions)
      throws ExecutionException {
    for (DexType type : wrappers.keySet()) {
      DexProgramClass pgrmClass = wrappers.get(type).getSecond();
      assert pgrmClass != null;
      generateConversions.accept(type, pgrmClass);
      registerSynthesizedClass(pgrmClass, builder);
      irConverter.optimizeSynthesizedClass(pgrmClass, executorService);
    }
  }

  private void registerSynthesizedClass(
      DexProgramClass synthesizedClass, DexApplication.Builder<?> builder) {
    builder.addSynthesizedClass(synthesizedClass, false);
    appView.appInfo().addSynthesizedClass(synthesizedClass);
  }

  private void generateTypeConversions(DexType type, DexProgramClass synthesizedClass) {
    synthesizedClass.addDirectMethod(
        synthetizeConversionMethod(
            synthesizedClass.type,
            type,
            converter.vivifiedTypeFor(type),
            typeWrappers.get(type).getFirst()));
  }

  private void generateVivifiedTypeConversions(DexType type, DexProgramClass synthesizedClass) {
    synthesizedClass.addDirectMethod(
        synthetizeConversionMethod(
            synthesizedClass.type,
            converter.vivifiedTypeFor(type),
            type,
            vivifiedTypeWrappers.get(type).getFirst()));
  }

  private DexEncodedMethod synthetizeConversionMethod(
      DexType holder, DexType argType, DexType returnType, DexType reverseWrapperType) {
    DexMethod method =
        factory.createMethod(
            holder, factory.createProto(returnType, argType), factory.convertMethodName);
    DexEncodedMethod dexEncodedMethod =
        newSynthesizedMethod(
            method, Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC, false);
    dexEncodedMethod.setCode(
        new CfAPIConverterWrapperConversionCodeProvider(
                dexEncodedMethod,
                method,
                appView,
                argType,
                reverseWrapperType,
                factory.createField(holder, returnType, factory.wrapperFieldName))
            .getCfCode(),
        appView);
    return dexEncodedMethod;
  }
}
