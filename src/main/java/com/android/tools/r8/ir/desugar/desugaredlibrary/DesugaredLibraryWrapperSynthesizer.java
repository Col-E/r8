// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterConstructorCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterThrowRuntimeExceptionCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterVivifiedWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperConversionCfCodeProvider;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

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

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final ConcurrentHashMap<DexType, List<DexEncodedMethod>> allImplementedMethodsCache =
      new ConcurrentHashMap<>();

  DesugaredLibraryWrapperSynthesizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public boolean isSyntheticWrapper(DexType type) {
    return appView.getSyntheticItems().isSyntheticOfKind(type, SyntheticKind.WRAPPER)
        || appView.getSyntheticItems().isSyntheticOfKind(type, SyntheticKind.VIVIFIED_WRAPPER);
  }

  public boolean shouldConvert(DexType type, DexMethod method) {
    if (!appView.rewritePrefix.hasRewrittenType(type, appView)) {
      return false;
    }
    if (canConvert(type)) {
      return true;
    }
    reportInvalidInvoke(type, method);
    return false;
  }

  public DexMethod ensureConversionMethod(
      DexType type,
      DexType srcType,
      DexType destType,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    // ConversionType holds the methods "rewrittenType convert(type)" and the other way around.
    // But everything is going to be rewritten, so we need to use vivifiedType and type".
    DexType conversionHolder =
        appView.options().desugaredLibraryConfiguration.getCustomConversions().get(type);
    if (conversionHolder == null) {
      conversionHolder =
          type == srcType
              ? ensureTypeWrapper(type, eventConsumer)
              : ensureVivifiedTypeWrapper(type, eventConsumer);
    }
    assert conversionHolder != null;
    return factory.createMethod(
        conversionHolder, factory.createProto(destType, srcType), factory.convertMethodName);
  }

  private boolean canConvert(DexType type) {
    return appView.options().desugaredLibraryConfiguration.getCustomConversions().containsKey(type)
        || canGenerateWrapper(type);
  }

  private void reportInvalidInvoke(DexType type, DexMethod invokedMethod) {
    DexType desugaredType = appView.rewritePrefix.rewrittenType(type, appView);
    StringDiagnostic diagnostic =
        new StringDiagnostic(
            "Invoke to "
                + invokedMethod.holder
                + "#"
                + invokedMethod.name
                + " may not work correctly at runtime (Cannot convert type "
                + desugaredType
                + ").");
    if (appView.options().isDesugaredLibraryCompilation()) {
      throw appView.options().reporter.fatalError(diagnostic);
    } else {
      appView.options().reporter.info(diagnostic);
    }
  }

  private boolean canGenerateWrapper(DexType type) {
    return appView.options().desugaredLibraryConfiguration.getWrapperConversions().contains(type);
  }

  private DexType ensureTypeWrapper(
      DexType type, DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    return ensureWrappers(type, eventConsumer).getWrapper().type;
  }

  private DexType ensureVivifiedTypeWrapper(
      DexType type, DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    return ensureWrappers(type, eventConsumer).getVivifiedWrapper().type;
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

  static class Wrappers {
    private final DexClass wrapper;
    private final DexClass vivifiedWrapper;

    Wrappers(DexClass wrapper, DexClass vivifiedWrapper) {
      this.wrapper = wrapper;
      this.vivifiedWrapper = vivifiedWrapper;
    }

    public DexClass getWrapper() {
      return wrapper;
    }

    public DexClass getVivifiedWrapper() {
      return vivifiedWrapper;
    }
  }

  private Wrappers ensureWrappers(
      DexType type, DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    assert canGenerateWrapper(type) : type;
    DexClass dexClass = getValidClassToWrap(type);
    return ensureWrappers(dexClass, ignored -> {}, eventConsumer);
  }

  private Wrappers ensureWrappers(
      DexClass context,
      Consumer<DexClasspathClass> creationCallback,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    DexType type = context.type;
    DexClass wrapper;
    DexClass vivifiedWrapper;
    if (context.isProgramClass()) {
      assert appView.options().isDesugaredLibraryCompilation();
      DexProgramClass programContext = context.asProgramClass();
      wrapper =
          ensureProgramWrapper(
              SyntheticKind.WRAPPER,
              vivifiedTypeFor(type),
              type,
              programContext,
              eventConsumer,
              wrapperField ->
                  synthesizeVirtualMethodsForTypeWrapper(
                      programContext, wrapperField, eventConsumer));
      vivifiedWrapper =
          ensureProgramWrapper(
              SyntheticKind.VIVIFIED_WRAPPER,
              type,
              vivifiedTypeFor(type),
              programContext,
              eventConsumer,
              wrapperField ->
                  synthesizeVirtualMethodsForVivifiedTypeWrapper(
                      programContext, wrapperField, eventConsumer));
      DexField wrapperField = getWrapperUniqueField(wrapper);
      DexField vivifiedWrapperField = getWrapperUniqueField(vivifiedWrapper);
      ensureProgramConversionMethod(
          SyntheticKind.WRAPPER, programContext, wrapperField, vivifiedWrapperField);
      ensureProgramConversionMethod(
          SyntheticKind.VIVIFIED_WRAPPER, programContext, vivifiedWrapperField, wrapperField);
    } else {
      assert context.isNotProgramClass();
      ClasspathOrLibraryClass classpathOrLibraryContext = context.asClasspathOrLibraryClass();
      wrapper =
          ensureClasspathWrapper(
              SyntheticKind.WRAPPER,
              vivifiedTypeFor(type),
              type,
              classpathOrLibraryContext,
              creationCallback,
              eventConsumer,
              wrapperField ->
                  synthesizeVirtualMethodsForTypeWrapper(context, wrapperField, eventConsumer));
      vivifiedWrapper =
          ensureClasspathWrapper(
              SyntheticKind.VIVIFIED_WRAPPER,
              type,
              vivifiedTypeFor(type),
              classpathOrLibraryContext,
              creationCallback,
              eventConsumer,
              wrapperField ->
                  synthesizeVirtualMethodsForVivifiedTypeWrapper(
                      context, wrapperField, eventConsumer));
      DexField wrapperField = getWrapperUniqueField(wrapper);
      DexField vivifiedWrapperField = getWrapperUniqueField(vivifiedWrapper);
      ensureClasspathConversionMethod(
          SyntheticKind.WRAPPER, classpathOrLibraryContext, wrapperField, vivifiedWrapperField);
      ensureClasspathConversionMethod(
          SyntheticKind.VIVIFIED_WRAPPER,
          classpathOrLibraryContext,
          vivifiedWrapperField,
          wrapperField);
    }
    return new Wrappers(wrapper, vivifiedWrapper);
  }

  private DexEncodedField getWrapperUniqueEncodedField(DexClass wrapper) {
    assert wrapper.instanceFields().size() == 1;
    return wrapper.instanceFields().get(0);
  }

  private DexField getWrapperUniqueField(DexClass wrapper) {
    return getWrapperUniqueEncodedField(wrapper).getReference();
  }

  private DexProgramClass ensureProgramWrapper(
      SyntheticKind kind,
      DexType wrappingType,
      DexType wrappedType,
      DexProgramClass programContext,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer,
      Function<DexEncodedField, DexEncodedMethod[]> virtualMethodProvider) {
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            kind,
            programContext,
            appView,
            builder -> buildWrapper(wrappingType, wrappedType, programContext, builder),
            // The creation of virtual methods may require new wrappers, this needs to happen
            // once the wrapper is created to avoid infinite recursion.
            wrapper -> {
              if (eventConsumer != null) {
                eventConsumer.acceptWrapperProgramClass(wrapper);
              }
              wrapper.setVirtualMethods(
                  virtualMethodProvider.apply(getWrapperUniqueEncodedField(wrapper)));
            });
  }

  private DexClasspathClass ensureClasspathWrapper(
      SyntheticKind kind,
      DexType wrappingType,
      DexType wrappedType,
      ClasspathOrLibraryClass classpathOrLibraryContext,
      Consumer<DexClasspathClass> creationCallback,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer,
      Function<DexEncodedField, DexEncodedMethod[]> virtualMethodProvider) {
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            kind,
            classpathOrLibraryContext,
            appView,
            builder ->
                buildWrapper(
                    wrappingType, wrappedType, classpathOrLibraryContext.asDexClass(), builder),
            // The creation of virtual methods may require new wrappers, this needs to happen
            // once the wrapper is created to avoid infinite recursion.
            wrapper -> {
              if (eventConsumer != null) {
                eventConsumer.acceptWrapperClasspathClass(wrapper);
              }
              wrapper.setVirtualMethods(
                  virtualMethodProvider.apply(getWrapperUniqueEncodedField(wrapper)));
              creationCallback.accept(wrapper);
            });
  }

  private ProgramMethod ensureProgramConversionMethod(
      SyntheticKind kind,
      DexProgramClass context,
      DexField wrapperField,
      DexField reverseWrapperField) {
    return appView
        .getSyntheticItems()
        .ensureFixedClassMethod(
            factory.convertMethodName,
            factory.createProto(reverseWrapperField.type, wrapperField.type),
            kind,
            context,
            appView,
            ignored -> {},
            methodBuilder ->
                buildConversionMethod(methodBuilder, wrapperField, reverseWrapperField, context));
  }

  private DexClassAndMethod ensureClasspathConversionMethod(
      SyntheticKind kind,
      ClasspathOrLibraryClass context,
      DexField wrapperField,
      DexField reverseWrapperField) {
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClassMethod(
            factory.convertMethodName,
            factory.createProto(reverseWrapperField.type, wrapperField.type),
            kind,
            context,
            appView,
            ignored -> {},
            methodBuilder ->
                buildConversionMethod(
                    methodBuilder, wrapperField, reverseWrapperField, context.asDexClass()));
  }

  private boolean isInvalidWrapper(DexClass clazz) {
    return Iterables.any(allImplementedMethods(clazz), DexEncodedMethod::isFinal);
  }

  private void buildConversionMethod(
      SyntheticMethodBuilder methodBuilder,
      DexField wrapperField,
      DexField reverseWrapperField,
      DexClass context) {
    CfCode cfCode;
    if (isInvalidWrapper(context)) {
      cfCode =
          new APIConverterThrowRuntimeExceptionCfCodeProvider(
                  appView,
                  factory.createString(
                      "Unsupported conversion for "
                          + context.type
                          + ". See compilation time warnings for more details."),
                  wrapperField.holder)
              .generateCfCode();
    } else {
      cfCode =
          new APIConverterWrapperConversionCfCodeProvider(
                  appView, reverseWrapperField, wrapperField)
              .generateCfCode();
    }
    methodBuilder
        .setAccessFlags(
            MethodAccessFlags.fromCfAccessFlags(
                Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC, false))
        .setCode(methodSignature -> cfCode);
  }

  private void buildWrapper(
      DexType wrappingType,
      DexType wrappedType,
      DexClass clazz,
      SyntheticClassBuilder<?, ?> builder) {
    boolean isItf = clazz.isInterface();
    DexType superType = isItf ? factory.objectType : wrappingType;
    List<DexType> interfaces =
        isItf ? Collections.singletonList(wrappingType) : Collections.emptyList();
    DexEncodedField wrapperField =
        synthesizeWrappedValueEncodedField(builder.getType(), wrappedType);
    builder
        .setInterfaces(interfaces)
        .setSuperType(superType)
        .setInstanceFields(Collections.singletonList(wrapperField))
        .addMethod(methodBuilder -> buildWrapperConstructor(wrapperField, methodBuilder));
  }

  private void buildWrapperConstructor(
      DexEncodedField wrappedValueField, SyntheticMethodBuilder methodBuilder) {
    methodBuilder
        .setName(factory.constructorMethodName)
        .setProto(factory.createProto(factory.voidType, wrappedValueField.getType()))
        .setAccessFlags(
            MethodAccessFlags.fromCfAccessFlags(
                Constants.ACC_PRIVATE | Constants.ACC_SYNTHETIC, true))
        .setCode(
            codeSynthesizor ->
                new APIConverterConstructorCfCodeProvider(appView, wrappedValueField.getReference())
                    .generateCfCode());
  }

  private DexEncodedMethod[] synthesizeVirtualMethodsForVivifiedTypeWrapper(
      DexClass dexClass,
      DexEncodedField wrapperField,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
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
      DexClass holderClass = appView.definitionFor(dexEncodedMethod.getHolderType());
      boolean isInterface;
      if (holderClass == null) {
        assert appView
            .options()
            .desugaredLibraryConfiguration
            .getEmulateLibraryInterface()
            .containsValue(dexEncodedMethod.getHolderType());
        isInterface = true;
      } else {
        isInterface = holderClass.isInterface();
      }
      DexMethod methodToInstall =
          factory.createMethod(
              wrapperField.getHolderType(),
              dexEncodedMethod.getReference().proto,
              dexEncodedMethod.getReference().name);
      CfCode cfCode;
      if (dexEncodedMethod.isFinal()) {
        finalMethods.add(dexEncodedMethod.getReference());
        continue;
      } else {
        cfCode =
            new APIConverterVivifiedWrapperCfCodeProvider(
                    appView,
                    methodToInstall,
                    wrapperField.getReference(),
                    this,
                    isInterface,
                    eventConsumer)
                .generateCfCode();
      }
      DexEncodedMethod newDexEncodedMethod =
          newSynthesizedMethod(methodToInstall, dexEncodedMethod, cfCode);
      generatedMethods.add(newDexEncodedMethod);
    }
    return finalizeWrapperMethods(generatedMethods, finalMethods);
  }

  private DexEncodedMethod[] synthesizeVirtualMethodsForTypeWrapper(
      DexClass dexClass,
      DexEncodedField wrapperField,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
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
      DexClass holderClass = appView.definitionFor(dexEncodedMethod.getHolderType());
      assert holderClass != null || appView.options().isDesugaredLibraryCompilation();
      boolean isInterface = holderClass == null || holderClass.isInterface();
      DexMethod methodToInstall =
          DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature(
              dexEncodedMethod.getReference(), wrapperField.getHolderType(), appView);
      CfCode cfCode;
      if (dexEncodedMethod.isFinal()) {
        finalMethods.add(dexEncodedMethod.getReference());
        continue;
      } else {
        cfCode =
            new APIConverterWrapperCfCodeProvider(
                    appView,
                    dexEncodedMethod.getReference(),
                    wrapperField.getReference(),
                    this,
                    isInterface,
                    eventConsumer)
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

  private List<DexEncodedMethod> allImplementedMethods(DexClass clazz) {
    return allImplementedMethodsCache.computeIfAbsent(
        clazz.type, type -> internalAllImplementedMethods(clazz));
  }

  private List<DexEncodedMethod> internalAllImplementedMethods(DexClass libraryClass) {
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
            if (alreadyImplementedMethod.getReference().match(virtualMethod.getReference())) {
              alreadyAdded = true;
              break;
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

  void ensureWrappersForL8(DesugaredLibraryAPIConverterEventConsumer eventConsumer) {
    DesugaredLibraryConfiguration conf = appView.options().desugaredLibraryConfiguration;
    for (DexType type : conf.getWrapperConversions()) {
      assert !conf.getCustomConversions().containsKey(type);
      DexClass validClassToWrap = getValidClassToWrap(type);
      // In broken set-ups we can end up having a json files containing wrappers of non desugared
      // classes. Such wrappers are not required since the class won't be rewritten.
      if (validClassToWrap.isProgramClass()) {
        ensureWrappers(validClassToWrap, ignored -> {}, eventConsumer);
      }
    }
  }
}
