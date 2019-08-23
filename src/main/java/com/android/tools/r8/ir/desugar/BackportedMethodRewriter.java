// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.backports.BooleanMethods;
import com.android.tools.r8.ir.desugar.backports.ByteMethods;
import com.android.tools.r8.ir.desugar.backports.CharacterMethods;
import com.android.tools.r8.ir.desugar.backports.CollectionsMethods;
import com.android.tools.r8.ir.desugar.backports.DoubleMethods;
import com.android.tools.r8.ir.desugar.backports.FloatMethods;
import com.android.tools.r8.ir.desugar.backports.IntegerMethods;
import com.android.tools.r8.ir.desugar.backports.ListMethods;
import com.android.tools.r8.ir.desugar.backports.LongMethods;
import com.android.tools.r8.ir.desugar.backports.MathMethods;
import com.android.tools.r8.ir.desugar.backports.ObjectsMethods;
import com.android.tools.r8.ir.desugar.backports.OptionalMethods;
import com.android.tools.r8.ir.desugar.backports.SetMethods;
import com.android.tools.r8.ir.desugar.backports.ShortMethods;
import com.android.tools.r8.ir.desugar.backports.StringMethods;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class BackportedMethodRewriter {

  public static final String UTILITY_CLASS_NAME_PREFIX = "$r8$backportedMethods$utility";
  private static final String UTILITY_CLASS_DESCRIPTOR_PREFIX = "L" + UTILITY_CLASS_NAME_PREFIX;
  private final Set<DexType> holders = Sets.newConcurrentHashSet();

  private final AppView<?> appView;
  private final IRConverter converter;
  private final DexItemFactory factory;
  private final RewritableMethods rewritableMethods;
  private final Map<DexType, DexType> backportCoreLibraryMembers = new IdentityHashMap<>();

  private Map<DexMethod, MethodProvider> methodProviders = new ConcurrentHashMap<>();

  public BackportedMethodRewriter(AppView<?> appView, IRConverter converter) {
    this.appView = appView;
    this.converter = converter;
    this.factory = appView.dexItemFactory();
    this.rewritableMethods = new RewritableMethods(appView);
    for (String coreLibMember : appView.options().backportCoreLibraryMembers.keySet()) {
      DexType extraCoreLibMemberType =
          factory.createType(DescriptorUtils.javaTypeToDescriptor(coreLibMember));
      DexType coreLibMemberType =
          factory.createType(
              DescriptorUtils.javaTypeToDescriptor(
                  appView.options().backportCoreLibraryMembers.get(coreLibMember)));
      this.backportCoreLibraryMembers.put(extraCoreLibMemberType, coreLibMemberType);
    }
  }

  public void desugar(IRCode code) {
    if (rewritableMethods.isEmpty()) {
      return; // Nothing to do!
    }

    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeMethod()) {
        continue;
      }

      InvokeMethod invoke = instruction.asInvokeMethod();
      MethodProvider provider = getMethodProviderOrNull(invoke.getInvokedMethod());
      if (provider == null) {
        continue;
      }

      DexMethod newMethod = provider.provideMethod(appView);
      iterator.replaceCurrentInstruction(
          new InvokeStatic(newMethod, invoke.outValue(), invoke.inValues()));

      if (provider.requiresGenerationOfCode()) {
        methodProviders.putIfAbsent(newMethod, provider);
        holders.add(code.method.method.holder);
      }
    }
  }

  private Collection<DexProgramClass> findSynthesizedFrom(Builder<?> builder, DexType holder) {
    for (DexProgramClass synthesizedClass : builder.getSynthesizedClasses()) {
      if (holder == synthesizedClass.getType()) {
        return synthesizedClass.getSynthesizedFrom();
      }
    }
    return null;
  }

  public static boolean hasRewrittenMethodPrefix(DexType clazz) {
    return clazz.descriptor.toString().startsWith(UTILITY_CLASS_DESCRIPTOR_PREFIX);
  }

  public void synthesizeUtilityClass(
      Builder<?> builder, ExecutorService executorService, InternalOptions options)
      throws ExecutionException {
    if (holders.isEmpty()) {
      return;
    }
    // Compute referencing classes ignoring references in-between utility classes.
    Set<DexProgramClass> referencingClasses = Sets.newConcurrentHashSet();
    for (DexType holder : holders) {
      DexClass definitionFor = appView.definitionFor(holder);
      if (definitionFor == null) {
        Collection<DexProgramClass> synthesizedFrom = findSynthesizedFrom(builder, holder);
        assert synthesizedFrom != null;
        referencingClasses.addAll(synthesizedFrom);
      } else {
        referencingClasses.add(definitionFor.asProgramClass());
      }
    }

    MethodAccessFlags flags = MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC, false);
    ClassAccessFlags classAccessFlags =
        ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
    // Generate the utility classes in a loop since utility classes can require the
    // the creation of other utility classes.
    // Function multiplyExact(long int) calls multiplyExact(long long) for example.
    while (!methodProviders.isEmpty()) {
      DexMethod key = methodProviders.keySet().iterator().next();
      MethodProvider provider = methodProviders.get(key);
      methodProviders.remove(key);
      assert provider.requiresGenerationOfCode();
      DexMethod method = provider.provideMethod(appView);
      // The utility class could have been synthesized, e.g., running R8 then D8,
      // or if already processed in this while loop.
      if (appView.definitionFor(method.holder) != null) {
        continue;
      }
      TemplateMethodCode code = provider.generateTemplateMethod(options, method);
      DexEncodedMethod dexEncodedMethod =
          new DexEncodedMethod(
              method, flags, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), code);
      DexProgramClass utilityClass =
          new DexProgramClass(
              method.holder,
              null,
              new SynthesizedOrigin("java8 methods utility class", getClass()),
              classAccessFlags,
              factory.objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              new DexEncodedMethod[] {dexEncodedMethod},
              DexEncodedMethod.EMPTY_ARRAY,
              factory.getSkipNameValidationForTesting(),
              referencingClasses);
      code.setUpContext(utilityClass);
      boolean addToMainDexList =
          referencingClasses.stream()
              .anyMatch(clazz -> appView.appInfo().isInMainDexList(clazz.type));
      appView.appInfo().addSynthesizedClass(utilityClass);
      builder.addSynthesizedClass(utilityClass, addToMainDexList);
      // The following may add elements to methodsProviders.
      converter.optimizeSynthesizedClass(utilityClass, executorService);
    }
  }

  private MethodProvider getMethodProviderOrNull(DexMethod method) {
    DexMethod original = appView.graphLense().getOriginalMethodSignature(method);
    assert original != null;
    if (backportCoreLibraryMembers.containsKey(original.holder)) {
      DexType newHolder = backportCoreLibraryMembers.get(original.holder);
      MethodProvider provider =
          rewritableMethods.getProvider(
              newHolder.descriptor,
              original.name,
              original.proto);
      if (provider != null) {
        return provider;
      }
      RetargetCoreLibraryMethodProvider extraProvider =
          new RetargetCoreLibraryMethodProvider(
              newHolder,
              original.holder.descriptor,
              original.name,
              original.proto,
              true);
      // TODO(b/139788786): cache this entry, but without writing into a lock free structure.
      // rewritableMethods.addProvider(extraProvider);
      return extraProvider;
    }
    return rewritableMethods.getProvider(original.holder.descriptor, original.name, original.proto);
  }

  public static final class RewritableMethods {

    // Map class, method, proto to a provider for creating the code and method.
    private final Map<DexString, Map<DexString, Map<DexProto, MethodProvider>>> rewritable =
        new IdentityHashMap<>();

    public RewritableMethods(AppView<?> appView) {
      InternalOptions options = appView.options();
      DexItemFactory factory = appView.dexItemFactory();

      if (options.minApiLevel < AndroidApiLevel.K.getLevel()) {
        initializeAndroidKMethodProviders(factory);
      }
      if (options.minApiLevel < AndroidApiLevel.N.getLevel()) {
        initializeAndroidNMethodProviders(factory);
      }
      if (options.minApiLevel < AndroidApiLevel.O.getLevel()) {
        initializeAndroidOMethodProviders(factory);
      }

      if (options.rewritePrefix.containsKey("java.util.Optional")
          || options.minApiLevel >= AndroidApiLevel.N.getLevel()) {
        // These are currently not implemented at any API level in Android.
        // They however require the Optional class to be present, either through
        // desugared libraries or natively. If Optional class is not present,
        // we do not desugar to avoid confusion in error messages.
        initializeOptionalMethodProviders(factory);
      }

      // These are currently not implemented at any API level in Android.
      initializeJava9MethodProviders(factory);
      initializeJava11MethodProviders(factory);

      if (!options.retargetCoreLibMember.isEmpty()) {
        initializeRetargetCoreLibraryMembers(appView);
      }
    }

    boolean isEmpty() {
      return rewritable.isEmpty();
    }

    private void initializeAndroidKMethodProviders(DexItemFactory factory) {
      // Note: Long.compare rewriting is handled by CodeRewriter since there is a dedicated
      // bytecode which supports the operation.

      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.compare(byte a, byte b)
      DexString method = factory.createString("compare");
      DexProto proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      addProvider(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.compare(short a, short b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      addProvider(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;
      // int Integer.compare(int a, int b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;
      // int Boolean.compare(boolean a, boolean b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.booleanType, factory.booleanType);
      addProvider(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // Character
      clazz = factory.boxedCharDescriptor;
      // int Character.compare(char a, char b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.charType, factory.charType);
      addProvider(new MethodGenerator(clazz, method, proto, CharacterMethods::new));

      // Objects
      clazz = factory.objectsDescriptor;

      // int Objects.compare(T a, T b, Comparator<? super T> c)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.objectType, factory.objectType,
          factory.comparatorType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.deepEquals(Object a, Object b)
      method = factory.createString("deepEquals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.equals(Object a, Object b)
      method = factory.createString("equals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.hash(Object... o)
      method = factory.createString("hash");
      proto = factory.createProto(factory.intType, factory.objectArrayType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.hashCode(Object o)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // Note: Objects.requireNonNull(T) rewriting is handled by CodeRewriter for now.

      // T Objects.requireNonNull(T obj, String message)
      method = factory.createString("requireNonNull");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.stringType);
      addProvider(
          new MethodGenerator(clazz, method, proto, ObjectsMethods::new, "requireNonNullMessage"));

      // String Objects.toString(Object o)
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // String Objects.toString(Object o, String nullDefault);
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType, factory.stringType);
      addProvider(
          new MethodGenerator(clazz, method, proto, ObjectsMethods::new, "toStringDefault"));

      // Collections
      clazz = factory.collectionsDescriptor;

      // Enumeration<T> Collections.emptyEnumeration();
      method = factory.createString("emptyEnumeration");
      proto = factory.createProto(factory.enumerationType);
      addProvider(new MethodGenerator(clazz, method, proto, CollectionsMethods::new));

      // Iterator<T> Collections.emptyIterator();
      method = factory.createString("emptyIterator");
      proto = factory.createProto(factory.iteratorType);
      addProvider(new MethodGenerator(clazz, method, proto, CollectionsMethods::new));

      // ListIterator<T> Collections.emptyListIterator();
      method = factory.createString("emptyListIterator");
      proto = factory.createProto(factory.listIteratorType);
      addProvider(new MethodGenerator(clazz, method, proto, CollectionsMethods::new));
    }

    private void initializeAndroidNMethodProviders(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.hashCode(byte i)
      DexString method = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addProvider(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.hashCode(short i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.shortType);
      addProvider(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.hashCode(int i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.max(int a, int b)
      method = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.min(int a, int b)
      method = factory.createString("min");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.sum(int a, int b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // Double
      clazz = factory.boxedDoubleDescriptor;

      // int Double.hashCode(double d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.doubleType);
      addProvider(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.max(double a, double b)
      method = factory.createString("max");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addProvider(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.min(double a, double b)
      method = factory.createString("min");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addProvider(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.sum(double a, double b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addProvider(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // boolean Double.isFinite(double a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.doubleType);
      addProvider(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // Float
      clazz = factory.boxedFloatDescriptor;

      // int Float.hashCode(float d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.floatType);
      addProvider(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.max(float a, float b)
      method = factory.createString("max");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addProvider(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.min(float a, float b)
      method = factory.createString("min");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addProvider(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.sum(float a, float b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addProvider(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // boolean Float.isFinite(float a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.floatType);
      addProvider(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;

      // int Boolean.hashCode(boolean b)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.booleanType);
      addProvider(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalAnd(boolean a, boolean b)
      method = factory.createString("logicalAnd");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addProvider(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalOr(boolean a, boolean b)
      method = factory.createString("logicalOr");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addProvider(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalXor(boolean a, boolean b)
      method = factory.createString("logicalXor");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addProvider(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // Long
      clazz = factory.boxedLongDescriptor;

      // int Long.hashCode(long i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.max(long a, long b)
      method = factory.createString("max");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.min(long a, long b)
      method = factory.createString("min");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.sum(long a, long b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // Character
      clazz = factory.boxedCharDescriptor;

      // int Character.hashCode(char i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.charType);
      addProvider(new MethodGenerator(clazz, method, proto, CharacterMethods::new));

      // Objects
      clazz = factory.objectsDescriptor;

      // boolean Objects.isNull(Object o)
      method = factory.createString("isNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.nonNull(Object a)
      method = factory.createString("nonNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // Math & StrictMath, which have some symmetric, binary-compatible APIs
      DexString[] mathClasses = {factory.mathDescriptor, factory.strictMathDescriptor};
      for (DexString mathClass : mathClasses) {
        clazz = mathClass;

        // int {Math,StrictMath}.addExact(int, int)
        method = factory.createString("addExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "addExactInt"));

        // long {Math,StrictMath}.addExact(long, long)
        method = factory.createString("addExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "addExactLong"));

        // int {Math,StrictMath}.floorDiv(int, int)
        method = factory.createString("floorDiv");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorDivInt"));

        // long {Math,StrictMath}.floorDiv(long, long)
        method = factory.createString("floorDiv");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorDivLong"));

        // int {Math,StrictMath}.floorMod(int, int)
        method = factory.createString("floorMod");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorModInt"));

        // long {Math,StrictMath}.floorMod(long, long)
        method = factory.createString("floorMod");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorModLong"));

        // int {Math,StrictMath}.multiplyExact(int, int)
        method = factory.createString("multiplyExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addProvider(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "multiplyExactInt"));

        // long {Math,StrictMath}.multiplyExact(long, long)
        method = factory.createString("multiplyExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addProvider(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "multiplyExactLong"));

        // double {Math,StrictMath}.nextDown(double)
        method = factory.createString("nextDown");
        proto = factory.createProto(factory.doubleType, factory.doubleType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "nextDownDouble"));

        // float {Math,StrictMath}.nextDown(float)
        method = factory.createString("nextDown");
        proto = factory.createProto(factory.floatType, factory.floatType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "nextDownFloat"));

        // int {Math,StrictMath}.subtractExact(int, int)
        method = factory.createString("subtractExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addProvider(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "subtractExactInt"));

        // long {Math,StrictMath}.subtractExact(long, long)
        method = factory.createString("subtractExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addProvider(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "subtractExactLong"));

        // int {Math,StrictMath}.toIntExact(long)
        method = factory.createString("toIntExact");
        proto = factory.createProto(factory.intType, factory.longType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new));
      }

      // Math (APIs which are not mirrored by StrictMath)
      clazz = factory.mathDescriptor;

      // int Math.decrementExact(int)
      method = factory.createString("decrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "decrementExactInt"));

      // long Math.decrementExact(long)
      method = factory.createString("decrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addProvider(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "decrementExactLong"));

      // int Math.incrementExact(int)
      method = factory.createString("incrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "incrementExactInt"));

      // long Math.incrementExact(long)
      method = factory.createString("incrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addProvider(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "incrementExactLong"));

      // int Math.negateExact(int)
      method = factory.createString("negateExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "negateExactInt"));

      // long Math.negateExact(long)
      method = factory.createString("negateExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "negateExactLong"));
    }

    private void initializeAndroidOMethodProviders(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;

      // int Byte.toUnsignedInt(byte value)
      DexString method = factory.createString("toUnsignedInt");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addProvider(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // long Byte.toUnsignedLong(byte value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.byteType);
      addProvider(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;

      // int Short.toUnsignedInt(short value)
      method = factory.createString("toUnsignedInt");
      proto = factory.createProto(factory.intType, factory.shortType);
      addProvider(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // long Short.toUnsignedLong(short value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.shortType);
      addProvider(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.divideUnsigned(int a, int b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.remainderUnsigned(int a, int b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.compareUnsigned(int a, int b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // long Integer.toUnsignedLong(int value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.parseUnsignedInt(String value)
      method = factory.createString("parseUnsignedInt");
      proto = factory.createProto(factory.intType, factory.stringType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.parseUnsignedInt(String value, int radix)
      method = factory.createString("parseUnsignedInt");
      proto = factory.createProto(factory.intType, factory.stringType, factory.intType);
      addProvider(
          new MethodGenerator(
              clazz, method, proto, IntegerMethods::new, "parseUnsignedIntWithRadix"));

      // String Integer.toUnsignedString(int value)
      method = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // String Integer.toUnsignedString(int value, int radix)
      method = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.intType, factory.intType);
      addProvider(
          new MethodGenerator(
              clazz, method, proto, IntegerMethods::new, "toUnsignedStringWithRadix"));

      // Long
      clazz = factory.boxedLongDescriptor;

      // long Long.divideUnsigned(long a, long b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.remainderUnsigned(long a, long b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // int Long.compareUnsigned(long a, long b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.longType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.parseUnsignedLong(String value)
      method = factory.createString("parseUnsignedLong");
      proto = factory.createProto(factory.longType, factory.stringType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.parseUnsignedLong(String value, int radix)
      method = factory.createString("parseUnsignedLong");
      proto = factory.createProto(factory.longType, factory.stringType, factory.intType);
      addProvider(
          new MethodGenerator(
              clazz, method, proto, LongMethods::new, "parseUnsignedLongWithRadix"));

      // String Long.toUnsignedString(long value)
      method = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.longType);
      addProvider(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // String Long.toUnsignedString(long value, int radix)
      method = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.longType, factory.intType);
      addProvider(
          new MethodGenerator(clazz, method, proto, LongMethods::new, "toUnsignedStringWithRadix"));

      // String
      clazz = factory.stringDescriptor;

      // String String.join(CharSequence, CharSequence...)
      method = factory.createString("join");
      proto = factory.createProto(factory.stringType, factory.charSequenceType,
          factory.charSequenceArrayType);
      addProvider(new MethodGenerator(clazz, method, proto, StringMethods::new, "joinArray"));

      // String String.join(CharSequence, Iterable<? extends CharSequence>)
      method = factory.createString("join");
      proto =
          factory.createProto(factory.stringType, factory.charSequenceType, factory.iterableType);
      addProvider(new MethodGenerator(clazz, method, proto, StringMethods::new, "joinIterable"));
    }

    private void initializeJava9MethodProviders(DexItemFactory factory) {
      // Math & StrictMath, which have some symmetric, binary-compatible APIs
      DexString[] mathClasses = {factory.mathDescriptor, factory.strictMathDescriptor};
      for (DexString mathClass : mathClasses) {
        DexString clazz = mathClass;

        // long {Math,StrictMath}.multiplyExact(long, int)
        DexString method = factory.createString("multiplyExact");
        DexProto proto = factory.createProto(factory.longType, factory.longType, factory.intType);
        addProvider(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "multiplyExactLongInt"));

        // long {Math,StrictMath}.floorDiv(long, int)
        method = factory.createString("floorDiv");
        proto = factory.createProto(factory.longType, factory.longType, factory.intType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorDivLongInt"));

        // int {Math,StrictMath}.floorMod(long, int)
        method = factory.createString("floorMod");
        proto = factory.createProto(factory.intType, factory.longType, factory.intType);
        addProvider(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorModLongInt"));
      }

      // Byte
      DexString clazz = factory.boxedByteDescriptor;

      // int Byte.compareUnsigned(byte, byte)
      DexString method = factory.createString("compareUnsigned");
      DexProto proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      addProvider(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;

      // int Short.compareUnsigned(short, short)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      addProvider(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Objects
      clazz = factory.objectsDescriptor;

      // T Objects.requireNonNullElse(T, T)
      method = factory.createString("requireNonNullElse");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.objectType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // T Objects.requireNonNullElseGet(T, Supplier<? extends T>)
      method = factory.createString("requireNonNullElseGet");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.supplierType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.checkIndex(int, int)
      method = factory.createString("checkIndex");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.checkFromToIndex(int, int, int)
      method = factory.createString("checkFromToIndex");
      proto =
          factory.createProto(factory.intType, factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.checkFromIndexSize(int, int, int)
      method = factory.createString("checkFromIndexSize");
      proto =
          factory.createProto(factory.intType, factory.intType, factory.intType, factory.intType);
      addProvider(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // List<E> List.of(<args>) for 0 to 10 arguments
      // Set<E> Set.of(<args>) for 0 to 10 arguments
      method = factory.createString("of");
      ArrayList<DexType> parameters = new ArrayList<>();
      for (int i = 0; i <= 10; i++) {
        clazz = factory.listDescriptor;
        proto = factory.createProto(factory.listType, parameters);
        addProvider(new MethodGenerator(clazz, method, proto, ListMethods::new));

        clazz = factory.setDescriptor;
        proto = factory.createProto(factory.setType, parameters);
        addProvider(new MethodGenerator(clazz, method, proto, SetMethods::new));

        parameters.add(factory.objectType);
      }

      // List<E> List.of(E...)
      clazz = factory.listDescriptor;
      method = factory.createString("of");
      proto = factory.createProto(factory.listType, factory.objectArrayType);
      addProvider(new MethodGenerator(clazz, method, proto, ListMethods::new, "ofVarargs"));

      // Set<E> Set.of(E...)
      clazz = factory.setDescriptor;
      method = factory.createString("of");
      proto = factory.createProto(factory.setType, factory.objectArrayType);
      addProvider(new MethodGenerator(clazz, method, proto, SetMethods::new, "ofVarargs"));
    }

    private void initializeJava11MethodProviders(DexItemFactory factory) {
      // Character
      DexString clazz = factory.boxedCharDescriptor;

      // String Character.toString(int)
      DexString method = factory.createString("toString");
      DexProto proto = factory.createProto(factory.stringType, factory.intType);
      addProvider(
          new MethodGenerator(clazz, method, proto, CharacterMethods::new, "toStringCodepoint"));
    }

    private void initializeOptionalMethodProviders(DexItemFactory factory) {
      // Optional
      DexString clazz = factory.createString("Ljava/util/Optional;");
      DexType optionalType = factory.createType(clazz);

      // Optional.or(supplier)
      DexString method = factory.createString("or");
      DexProto proto = factory.createProto(optionalType, factory.supplierType);
      addProvider(
          new StatifyingMethodGenerator(
              clazz, method, proto, OptionalMethods::new, "or", optionalType));

      // Optional.stream()
      method = factory.createString("stream");
      proto = factory.createProto(factory.createType("Ljava/util/stream/Stream;"));
      addProvider(
          new StatifyingMethodGenerator(
              clazz, method, proto, OptionalMethods::new, "stream", optionalType));

      // Optional{void,Int,Long,Double}.ifPresentOrElse(consumer,runnable)
      DexString[] optionalClasses =
          new DexString[] {
            clazz,
            factory.createString("Ljava/util/OptionalDouble;"),
            factory.createString("Ljava/util/OptionalLong;"),
            factory.createString("Ljava/util/OptionalInt;")
          };
      DexType[] consumerClasses =
          new DexType[] {
            factory.consumerType,
            factory.createType("Ljava/util/function/DoubleConsumer;"),
            factory.createType("Ljava/util/function/LongConsumer;"),
            factory.createType("Ljava/util/function/IntConsumer;")
          };
      for (int i = 0; i < optionalClasses.length; i++) {
        clazz = optionalClasses[i];
        DexType consumer = consumerClasses[i];
        method = factory.createString("ifPresentOrElse");
        proto = factory.createProto(factory.voidType, consumer, factory.runnableType);
        addProvider(
            new StatifyingMethodGenerator(
                clazz,
                method,
                proto,
                OptionalMethods::new,
                "ifPresentOrElse",
                factory.createType(clazz)));
      }
    }

    private void warnMissingRetargetCoreLibraryMember(DexType type, AppView<?> appView) {
      StringDiagnostic warning =
          new StringDiagnostic(
              "Cannot retarget core library member "
                  + type.getName()
                  + " because the class is missing.");
      appView.options().reporter.warning(warning);
    }

    private void initializeRetargetCoreLibraryMembers(AppView<?> appView) {
      Map<DexString, Map<DexType, DexType>> retargetCoreLibMember = new IdentityHashMap<>();
      appView
          .options()
          .populateRetargetCoreLibMember(appView.dexItemFactory(), retargetCoreLibMember);
      for (DexString methodName : retargetCoreLibMember.keySet()) {
        for (DexType inType : retargetCoreLibMember.get(methodName).keySet()) {
          DexClass typeClass = appView.definitionFor(inType);
          if (typeClass == null) {
            warnMissingRetargetCoreLibraryMember(inType, appView);
          } else {
            DexType newHolder = retargetCoreLibMember.get(methodName).get(inType);
            List<DexEncodedMethod> found = findDexEncodedMethodsWithName(methodName, typeClass);
            for (DexEncodedMethod encodedMethod : found) {
              DexProto proto = encodedMethod.method.proto;
              addProvider(
                  new RetargetCoreLibraryMethodProvider(
                      newHolder, inType.descriptor, methodName, proto, encodedMethod.isStatic()));
            }
          }
        }
      }
    }

    private List<DexEncodedMethod> findDexEncodedMethodsWithName(
        DexString methodName, DexClass clazz) {
      List<DexEncodedMethod> found = new ArrayList<>();
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        if (encodedMethod.method.name == methodName) {
          found.add(encodedMethod);
        }
      }
      assert found.size() > 0 : "Should have found a method (library specifications).";
      return found;
    }

    private void addProvider(MethodProvider generator) {
      rewritable
          .computeIfAbsent(generator.clazz, k -> new IdentityHashMap<>())
          .computeIfAbsent(generator.method, k -> new IdentityHashMap<>())
          .put(generator.proto, generator);
    }

    public MethodProvider getProvider(DexString clazz, DexString method, DexProto proto) {
      Map<DexString, Map<DexProto, MethodProvider>> classMap = rewritable.get(clazz);
      if (classMap != null) {
        Map<DexProto, MethodProvider> methodMap = classMap.get(method);
        if (methodMap != null) {
          return methodMap.get(proto);
        }
      }
      return null;
    }
  }

  public abstract static class MethodProvider {

    final DexString clazz;
    final DexString method;
    final DexProto proto;
    DexMethod dexMethod;

    public MethodProvider(DexString clazz, DexString method, DexProto proto) {
      this.clazz = clazz;
      this.method = method;
      this.proto = proto;
    }

    public abstract DexMethod provideMethod(AppView<?> appView);

    public abstract TemplateMethodCode generateTemplateMethod(
        InternalOptions options, DexMethod method);

    public abstract boolean requiresGenerationOfCode();
  }

  public static class RetargetCoreLibraryMethodProvider extends MethodProvider {

    private final DexType newHolder;
    private DexMethod dexMethod;
    private boolean isStatic;

    public RetargetCoreLibraryMethodProvider(
        DexType newHolder, DexString clazz, DexString method, DexProto proto, boolean isStatic) {
      super(clazz, method, proto);
      this.newHolder = newHolder;
      this.isStatic = isStatic;
    }

    @Override
    public DexMethod provideMethod(AppView<?> appView) {
      if (dexMethod != null) {
        return dexMethod;
      }
      DexItemFactory factory = appView.dexItemFactory();
      DexProto newProto =
          isStatic ? proto : factory.prependTypeToProto(factory.createType(clazz), proto);
      return dexMethod = factory.createMethod(newHolder, newProto, method);
    }

    @Override
    public TemplateMethodCode generateTemplateMethod(InternalOptions options, DexMethod method) {
      throw new Unreachable("Does not generate any method.");
    }

    @Override
    public boolean requiresGenerationOfCode() {
      return false;
    }
  }

  public static class MethodGenerator extends MethodProvider {

    private final TemplateMethodFactory factory;
    final String methodName;

    public MethodGenerator(
        DexString clazz, DexString method, DexProto proto, TemplateMethodFactory factory) {
      this(clazz, method, proto, factory, method.toString());
    }

    public MethodGenerator(
        DexString clazz,
        DexString method,
        DexProto proto,
        TemplateMethodFactory factory,
        String methodName) {
      super(clazz, method, proto);
      this.factory = factory;
      this.methodName = methodName;
    }

    @Override
    public DexMethod provideMethod(AppView<?> appView) {
      if (dexMethod != null) {
        return dexMethod;
      }
      DexItemFactory factory = appView.dexItemFactory();
      String unqualifiedName =
          DescriptorUtils.getUnqualifiedClassNameFromDescriptor(clazz.toString());
      // Avoid duplicate class names between core lib dex file and program dex files.
      String coreLibUtilitySuffix = appView.options().coreLibraryCompilation ? "$corelib" : "";
      String descriptor =
          UTILITY_CLASS_DESCRIPTOR_PREFIX
              + '$'
              + unqualifiedName
              + '$'
              + proto.parameters.size()
              + coreLibUtilitySuffix
              + '$'
              + methodName
              + ';';
      DexType clazz = factory.createType(descriptor);
      dexMethod = factory.createMethod(clazz, proto, method);
      return dexMethod;
    }

    @Override
    public TemplateMethodCode generateTemplateMethod(InternalOptions options, DexMethod method) {
      return factory.create(options, method, methodName);
    }

    @Override
    public boolean requiresGenerationOfCode() {
      return true;
    }
  }

  // Specific subclass to transform virtual methods into static desugared methods.
  // To be correct, the method has to be on a final class, and be implemented directly
  // on the class (no overrides).
  public static class StatifyingMethodGenerator extends MethodGenerator {

    private final DexType receiverType;

    public StatifyingMethodGenerator(
        DexString clazz,
        DexString method,
        DexProto proto,
        TemplateMethodFactory factory,
        String methodName,
        DexType receiverType) {
      super(clazz, method, proto, factory, methodName);
      this.receiverType = receiverType;
    }

    @Override
    public DexMethod provideMethod(AppView<?> appView) {
      if (dexMethod != null) {
        return dexMethod;
      }
      super.provideMethod(appView);
      DexProto newProto = appView.dexItemFactory().prependTypeToProto(receiverType, proto);
      dexMethod = appView.dexItemFactory().createMethod(dexMethod.holder, newProto, method);
      return dexMethod;
    }
  }

  private interface TemplateMethodFactory {

    TemplateMethodCode create(InternalOptions options, DexMethod method, String name);
  }
}

