// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterConstructorCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterVivifiedWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperCfCodeProvider;
import com.android.tools.r8.ir.synthetic.DesugaredLibraryAPIConversionCfCodeProvider.APIConverterWrapperConversionCfCodeProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.synthesis.SyntheticClassBuilder;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
public class DesugaredLibraryWrapperSynthesizer implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final ConcurrentHashMap<DexType, List<DexEncodedMethod>> allImplementedMethodsCache =
      new ConcurrentHashMap<>();
  private final DesugaredLibraryEnumConversionSynthesizer enumConverter;

  public DesugaredLibraryWrapperSynthesizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enumConverter = new DesugaredLibraryEnumConversionSynthesizer(appView);
  }

  public boolean isSyntheticWrapper(DexType type) {
    return appView.getSyntheticItems().isSyntheticOfKind(type, SyntheticKind.WRAPPER)
        || appView.getSyntheticItems().isSyntheticOfKind(type, SyntheticKind.VIVIFIED_WRAPPER);
  }

  public boolean shouldConvert(DexType type, DexMethod method) {
    return shouldConvert(type, method, null);
  }

  public boolean shouldConvert(DexType type, DexMethod method, ProgramMethod context) {
    if (type.isArrayType()) {
      return shouldConvert(type.toBaseType(appView.dexItemFactory()), method, context);
    }
    if (!appView.rewritePrefix.hasRewrittenType(type, appView)) {
      return false;
    }
    if (canConvert(type)) {
      return true;
    }
    reportInvalidInvoke(type, method, context);
    return false;
  }

  public DexMethod ensureConversionMethod(
      DexType type,
      DexType srcType,
      DexType destType,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    DexMethod customConversion = getCustomConversion(type, srcType, destType);
    if (customConversion != null) {
      return customConversion;
    }
    DexClass clazz = getValidClassToWrap(type);
    if (clazz.isEnum()) {
      return enumConverter.ensureEnumConversionMethod(clazz, srcType, destType, eventConsumer);
    }
    assert canGenerateWrapper(type) : type;
    WrapperConversions wrapperConversions = ensureWrappers(clazz, eventConsumer);
    DexMethod conversion =
        type == srcType
            ? wrapperConversions.getConversion()
            : wrapperConversions.getVivifiedConversion();
    assert srcType == conversion.getArgumentType(0, true);
    assert destType == conversion.getReturnType();
    return conversion;
  }

  public DexMethod getExistingProgramConversionMethod(
      DexType type, DexType srcType, DexType destType) {
    DexMethod customConversion = getCustomConversion(type, srcType, destType);
    if (customConversion != null) {
      return customConversion;
    }
    DexClass clazz = getValidClassToWrap(type);
    if (clazz.isEnum()) {
      return enumConverter.getExistingProgramEnumConversionMethod(clazz, srcType, destType);
    }
    WrapperConversions wrapperConversions = getExistingProgramWrapperConversions(clazz);
    DexMethod conversion =
        type == srcType
            ? wrapperConversions.getConversion()
            : wrapperConversions.getVivifiedConversion();
    assert srcType == conversion.getArgumentType(0, true);
    return conversion;
  }

  private DexMethod getCustomConversion(DexType type, DexType srcType, DexType destType) {
    // ConversionType holds the methods "rewrittenType convert(type)" and the other way around.
    // But everything is going to be rewritten, so we need to use vivifiedType and type".
    DexType conversionHolder =
        appView.options().desugaredLibrarySpecification.getCustomConversions().get(type);
    if (conversionHolder != null) {
      return factory.createMethod(
          conversionHolder, factory.createProto(destType, srcType), factory.convertMethodName);
    }
    return null;
  }

  private boolean canConvert(DexType type) {
    return appView.options().desugaredLibrarySpecification.getCustomConversions().containsKey(type)
        || canGenerateWrapper(type);
  }

  private void reportInvalidInvoke(DexType type, DexMethod invokedMethod, ProgramMethod context) {
    DexType desugaredType = appView.rewritePrefix.rewrittenType(type, appView);
    Origin origin = context != null ? context.getOrigin() : Origin.unknown();
    Position position =
        context != null ? new MethodPosition(context.getMethodReference()) : Position.UNKNOWN;
    StringDiagnostic diagnostic =
        new StringDiagnostic(
            "Invoke to "
                + invokedMethod.holder
                + "#"
                + invokedMethod.name
                + " may not work correctly at runtime (Cannot convert type "
                + desugaredType
                + ").",
            origin,
            position);
    if (appView.options().isDesugaredLibraryCompilation()) {
      throw appView.options().reporter.fatalError(diagnostic);
    } else {
      appView.options().reporter.info(diagnostic);
    }
  }

  private boolean canGenerateWrapper(DexType type) {
    return appView.options().desugaredLibrarySpecification.getWrapperConversions().contains(type);
  }

  private DexClass getValidClassToWrap(DexType type) {
    if (type.isArrayType()) {
      return getValidClassToWrap(type.toBaseType(factory));
    }
    DexClass dexClass = appView.definitionFor(type);
    // The dexClass should be a library class, so it cannot be null.
    assert dexClass != null;
    assert dexClass.isLibraryClass() || appView.options().isDesugaredLibraryCompilation();
    assert !dexClass.accessFlags.isFinal() || dexClass.isEnum();
    return dexClass;
  }

  private DexType vivifiedTypeFor(DexType type) {
    return DesugaredLibraryAPIConverter.vivifiedTypeFor(type, appView);
  }

  static class WrapperConversions {

    private final DexMethod conversion;
    private final DexMethod vivifiedConversion;

    WrapperConversions(DexMethod conversion, DexMethod vivifiedConversion) {
      this.conversion = conversion;
      this.vivifiedConversion = vivifiedConversion;
    }

    public DexMethod getConversion() {
      return conversion;
    }

    public DexMethod getVivifiedConversion() {
      return vivifiedConversion;
    }
  }

  private WrapperConversions ensureWrappers(
      DexClass context, DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
    assert eventConsumer != null;
    if (context.isProgramClass()) {
      return getExistingProgramWrapperConversions(context);
    }
    assert context.isNotProgramClass();
    ClasspathOrLibraryClass classpathOrLibraryContext = context.asClasspathOrLibraryClass();
    DexType type = context.type;
    DexType vivifiedType = vivifiedTypeFor(type);
    DexClass wrapper =
        ensureClasspathWrapper(
            SyntheticKind.WRAPPER,
            vivifiedType,
            type,
            classpathOrLibraryContext,
            eventConsumer,
            wrapperField -> synthesizeVirtualMethodsForTypeWrapper(context, wrapperField));
    DexClass vivifiedWrapper =
        ensureClasspathWrapper(
            SyntheticKind.VIVIFIED_WRAPPER,
            type,
            vivifiedType,
            classpathOrLibraryContext,
            eventConsumer,
            wrapperField -> synthesizeVirtualMethodsForVivifiedTypeWrapper(context, wrapperField));
    return new WrapperConversions(
        getConversion(wrapper, vivifiedType, type),
        getConversion(vivifiedWrapper, type, vivifiedType));
  }

  private WrapperConversions getExistingProgramWrapperConversions(DexClass context) {
    DexClass vivifiedWrapper;
    DexClass wrapper;
    assert appView.options().isDesugaredLibraryCompilation();
    wrapper = getExistingProgramWrapper(context, SyntheticKind.WRAPPER);
    vivifiedWrapper = getExistingProgramWrapper(context, SyntheticKind.VIVIFIED_WRAPPER);
    DexField wrapperField = getWrapperUniqueField(wrapper);
    DexField vivifiedWrapperField = getWrapperUniqueField(vivifiedWrapper);
    return new WrapperConversions(
        getConversion(wrapper, vivifiedWrapperField.type, wrapperField.type),
        getConversion(vivifiedWrapper, wrapperField.type, vivifiedWrapperField.type));
  }

  private DexProgramClass getExistingProgramWrapper(DexClass context, SyntheticKind kind) {
    return appView.getSyntheticItems().getExistingFixedClass(kind, context, appView);
  }

  private DexMethod getConversion(DexClass wrapper, DexType returnType, DexType argType) {
    DexMethod convertMethod =
        factory.createMethod(
            wrapper.type, factory.createProto(returnType, argType), factory.convertMethodName);
    return wrapper.lookupDirectMethod(convertMethod).getReference();
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
      DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer) {
    assert appView.options().isDesugaredLibraryCompilation();
    assert eventConsumer != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClass(
            kind,
            programContext,
            appView,
            builder -> buildWrapper(wrappingType, wrappedType, programContext, builder),
            // The creation of virtual methods may require new wrappers, this needs to happen
            // once the wrapper is created to avoid infinite recursion.
            eventConsumer::acceptWrapperProgramClass);
  }

  private DexClasspathClass ensureClasspathWrapper(
      SyntheticKind kind,
      DexType wrappingType,
      DexType wrappedType,
      ClasspathOrLibraryClass classpathOrLibraryContext,
      DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer,
      Function<DexEncodedField, Collection<DexEncodedMethod>> virtualMethodProvider) {
    assert eventConsumer != null;
    return appView
        .getSyntheticItems()
        .ensureFixedClasspathClass(
            kind,
            classpathOrLibraryContext,
            appView,
            builder -> {
              DexEncodedField wrapperField =
                  buildWrapper(
                      wrappingType, wrappedType, classpathOrLibraryContext.asDexClass(), builder);
              builder.addMethod(
                  methodBuilder ->
                      buildConversionMethod(
                          methodBuilder, factory.createProto(wrappingType, wrappedType), null));
              builder.setVirtualMethods(virtualMethodProvider.apply(wrapperField));
            },
            eventConsumer::acceptWrapperClasspathClass);
  }

  private void getExistingProgramConversionMethod(
      SyntheticKind kind, DexProgramClass context, DexClass wrapper, DexClass reverseWrapper) {
    DexField wrapperField = getWrapperUniqueField(wrapper);
    DexField reverseWrapperField = getWrapperUniqueField(reverseWrapper);
    DexProto proto = factory.createProto(reverseWrapperField.type, wrapperField.type);
    appView
        .getSyntheticItems()
        .ensureFixedClassMethod(
            factory.convertMethodName,
            proto,
            kind,
            context,
            appView,
            ignored -> {},
            methodBuilder ->
                buildConversionMethod(
                    methodBuilder,
                    proto,
                    computeProgramConversionMethodCode(
                        wrapperField, reverseWrapperField, context)));
  }

  private CfCode computeProgramConversionMethodCode(
      DexField wrapperField, DexField reverseWrapperField, DexClass context) {
    assert context.isProgramClass();
    return new APIConverterWrapperConversionCfCodeProvider(
            appView, reverseWrapperField, wrapperField)
        .generateCfCode();
  }

  private void buildConversionMethod(
      SyntheticMethodBuilder methodBuilder, DexProto proto, CfCode cfCode) {
    methodBuilder
        .setName(factory.convertMethodName)
        .setProto(proto)
        .setAccessFlags(
            MethodAccessFlags.fromCfAccessFlags(
                Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC, false))
        // Will be traced by the enqueuer.
        .disableAndroidApiLevelCheck()
        .setCode(methodSignature -> cfCode);
  }

  private DexEncodedField buildWrapper(
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
    return wrapperField;
  }

  private void buildWrapperConstructor(
      DexEncodedField wrappedValueField, SyntheticMethodBuilder methodBuilder) {
    methodBuilder
        .setName(factory.constructorMethodName)
        .setProto(factory.createProto(factory.voidType, wrappedValueField.getType()))
        .setAccessFlags(
            MethodAccessFlags.fromCfAccessFlags(
                Constants.ACC_PRIVATE | Constants.ACC_SYNTHETIC, true))
        // Will be traced by the enqueuer.
        .disableAndroidApiLevelCheck()
        .setCode(
            codeSynthesizor ->
                new APIConverterConstructorCfCodeProvider(appView, wrappedValueField.getReference())
                    .generateCfCode());
  }

  private Collection<DexEncodedMethod> synthesizeVirtualMethodsForVivifiedTypeWrapper(
      DexClass dexClass, DexEncodedField wrapperField) {
    Iterable<DexMethod> allImplementedMethods = allImplementedMethods(dexClass);
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
    for (DexMethod method : allImplementedMethods) {
      DexClass holderClass = appView.definitionFor(method.getHolderType());
      boolean isInterface;
      if (holderClass == null) {
        assert appView
            .options()
            .desugaredLibrarySpecification
            .getEmulateLibraryInterface()
            .containsValue(method.getHolderType());
        isInterface = true;
      } else {
        isInterface = holderClass.isInterface();
      }
      DexMethod methodToInstall =
          factory.createMethod(wrapperField.getHolderType(), method.proto, method.name);
      CfCode cfCode;
      if (dexClass.isProgramClass()) {
        cfCode =
            new APIConverterVivifiedWrapperCfCodeProvider(
                    appView, methodToInstall, wrapperField.getReference(), this, isInterface)
                .generateCfCode();
      } else {
        cfCode = null;
      }
      DexEncodedMethod newDexEncodedMethod = newSynthesizedMethod(methodToInstall, cfCode);
      generatedMethods.add(newDexEncodedMethod);
    }
    return generatedMethods;
  }

  private Collection<DexEncodedMethod> synthesizeVirtualMethodsForTypeWrapper(
      DexClass dexClass, DexEncodedField wrapperField) {
    Iterable<DexMethod> dexMethods = allImplementedMethods(dexClass);
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
    for (DexMethod method : dexMethods) {
      DexClass holderClass = appView.definitionFor(method.getHolderType());
      assert holderClass != null || appView.options().isDesugaredLibraryCompilation();
      boolean isInterface = holderClass == null || holderClass.isInterface();
      DexMethod methodToInstall =
          DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature(
              method, wrapperField.getHolderType(), appView);
      CfCode cfCode;
      if (dexClass.isProgramClass()) {
        cfCode =
            new APIConverterWrapperCfCodeProvider(
                    appView, method, wrapperField.getReference(), this, isInterface)
                .generateCfCode();
      } else {
        cfCode = null;
      }
      DexEncodedMethod newDexEncodedMethod = newSynthesizedMethod(methodToInstall, cfCode);
      generatedMethods.add(newDexEncodedMethod);
    }
    return generatedMethods;
  }

  DexEncodedMethod newSynthesizedMethod(DexMethod methodToInstall, Code code) {
    MethodAccessFlags newFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, false);
    ComputedApiLevel apiLevelForDefinition =
        appView.enableWholeProgramOptimizations()
            ? ComputedApiLevel.notSet()
            : appView
                .apiLevelCompute()
                .computeApiLevelForDefinition(methodToInstall, factory, ComputedApiLevel.unknown());
    // Since the method is a forwarding method, the api level for code is the same as the
    // definition.
    ComputedApiLevel apiLevelForCode = apiLevelForDefinition;
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(methodToInstall)
        .setAccessFlags(newFlags)
        .setCode(code)
        .setApiLevelForDefinition(apiLevelForDefinition)
        .setApiLevelForCode(code == null ? ComputedApiLevel.notSet() : apiLevelForCode)
        .build();
  }

  private Iterable<DexMethod> allImplementedMethods(DexClass clazz) {
    if (appView.options().testing.machineDesugaredLibrarySpecification != null) {
      return appView
          .options()
          .testing
          .machineDesugaredLibrarySpecification
          .getRewritingFlags()
          .getWrappers()
          .get(clazz.type);
    }
    List<DexEncodedMethod> dexEncodedMethods =
        allImplementedMethodsCache.computeIfAbsent(
            clazz.type, type -> internalAllImplementedMethods(clazz));
    return Iterables.transform(dexEncodedMethods, m -> m.getReference());
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
    assert !Iterables.any(implementedMethods, DexEncodedMethod::isFinal);
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
    return DexEncodedField.syntheticBuilder()
        .setField(field)
        .setAccessFlags(fieldAccessFlags)
        // The api level is computed when tracing.
        .disableAndroidApiLevelCheck()
        .build();
  }

  // Program wrappers are harder to deal with than classpath wrapper because generating a method's
  // code may require other wrappers. To keep it simple (This is L8 specific), we generate first
  // the wrappers with the conversion methods only, then the virtual methods assuming the
  // conversion methods are present.
  @Override
  public void synthesizeClasses(CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    LegacyDesugaredLibrarySpecification spec = appView.options().desugaredLibrarySpecification;
    List<DexProgramClass> validClassesToWrap = new ArrayList<>();
    for (DexType type : spec.getWrapperConversions()) {
      assert !spec.getCustomConversions().containsKey(type);
      DexClass validClassToWrap = getValidClassToWrap(type);
      // In broken set-ups we can end up having a json files containing wrappers of non desugared
      // classes. Such wrappers are not required since the class won't be rewritten.
      if (validClassToWrap.isProgramClass()) {
        if (validClassToWrap.isEnum()) {
          enumConverter.ensureProgramEnumConversionClass(validClassToWrap, eventConsumer);
        } else {
          validClassesToWrap.add(validClassToWrap.asProgramClass());
          ensureProgramWrappersWithoutVirtualMethods(validClassToWrap, eventConsumer);
        }
      }
    }
    for (DexProgramClass validClassToWrap : validClassesToWrap) {
      ensureProgramWrappersVirtualMethods(validClassToWrap);
    }
  }

  // We generate first the two wrappers with the constructor method and the fields, then we
  // the two conversion methods which requires the wrappers to know both fields.
  private void ensureProgramWrappersWithoutVirtualMethods(
      DexClass context, DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer) {
    assert eventConsumer != null;
    assert context.isProgramClass();
    DexType type = context.type;
    assert appView.options().isDesugaredLibraryCompilation();
    DexProgramClass programContext = context.asProgramClass();
    DexClass wrapper =
        ensureProgramWrapper(
            SyntheticKind.WRAPPER, vivifiedTypeFor(type), type, programContext, eventConsumer);
    DexClass vivifiedWrapper =
        ensureProgramWrapper(
            SyntheticKind.VIVIFIED_WRAPPER,
            type,
            vivifiedTypeFor(type),
            programContext,
            eventConsumer);
    getExistingProgramConversionMethod(
        SyntheticKind.WRAPPER, programContext, wrapper, vivifiedWrapper);
    getExistingProgramConversionMethod(
        SyntheticKind.VIVIFIED_WRAPPER, programContext, vivifiedWrapper, wrapper);
  }

  private void ensureProgramWrappersVirtualMethods(DexClass context) {
    assert context.isProgramClass();
    DexProgramClass wrapper = getExistingProgramWrapper(context, SyntheticKind.WRAPPER);
    wrapper.addVirtualMethods(
        synthesizeVirtualMethodsForTypeWrapper(context, getWrapperUniqueEncodedField(wrapper)));
    DexProgramClass vivifiedWrapper =
        getExistingProgramWrapper(context, SyntheticKind.VIVIFIED_WRAPPER);
    vivifiedWrapper.addVirtualMethods(
        synthesizeVirtualMethodsForVivifiedTypeWrapper(
            context, getWrapperUniqueEncodedField(vivifiedWrapper)));
  }
}
