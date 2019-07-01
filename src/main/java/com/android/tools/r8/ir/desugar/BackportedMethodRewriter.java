// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
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
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.RewritableMethods.MethodGenerator;
import com.android.tools.r8.ir.desugar.backports.BooleanMethods;
import com.android.tools.r8.ir.desugar.backports.ByteMethods;
import com.android.tools.r8.ir.desugar.backports.CharacterMethods;
import com.android.tools.r8.ir.desugar.backports.DoubleMethods;
import com.android.tools.r8.ir.desugar.backports.FloatMethods;
import com.android.tools.r8.ir.desugar.backports.IntegerMethods;
import com.android.tools.r8.ir.desugar.backports.LongMethods;
import com.android.tools.r8.ir.desugar.backports.MathMethods;
import com.android.tools.r8.ir.desugar.backports.ObjectsMethods;
import com.android.tools.r8.ir.desugar.backports.ShortMethods;
import com.android.tools.r8.ir.desugar.backports.StringMethods;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

  private Map<DexMethod, MethodGenerator> methodGenerators = new ConcurrentHashMap<>();

  public BackportedMethodRewriter(AppView<?> appView, IRConverter converter) {
    this.appView = appView;
    this.converter = converter;
    this.factory = appView.dexItemFactory();
    this.rewritableMethods = new RewritableMethods(factory, appView.options());
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

    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeStatic()) {
        continue;
      }
      InvokeStatic invoke = instruction.asInvokeStatic();

      MethodGenerator generator = getMethodGeneratorOrNull(invoke.getInvokedMethod());
      if (generator == null) {
        continue;
      }
      iterator.replaceCurrentInstruction(
            new InvokeStatic(generator.generateMethod(factory),
                invoke.outValue(), invoke.inValues()));
      methodGenerators.putIfAbsent(generator.generateMethod(factory), generator);
      holders.add(code.method.method.holder);
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

    for (MethodGenerator generator : methodGenerators.values()) {
      DexMethod method = generator.generateMethod(factory);
      // The utility class could have been synthesized, e.g., running R8 then D8.
      if (appView.definitionFor(method.holder) != null) {
        continue;
      }
      TemplateMethodCode code = generator.generateTemplateMethod(options, method);
      DexEncodedMethod dexEncodedMethod= new DexEncodedMethod(method,
          flags, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), code);
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
      AppInfo appInfo = appView.appInfo();
      boolean addToMainDexList =
          referencingClasses.stream().anyMatch(clazz -> appInfo.isInMainDexList(clazz.type));
      appInfo.addSynthesizedClass(utilityClass);
      converter.optimizeSynthesizedClass(utilityClass, executorService);
      builder.addSynthesizedClass(utilityClass, addToMainDexList);
    }
  }

  private MethodGenerator getMethodGeneratorOrNull(DexMethod method) {
    DexMethod original = appView.graphLense().getOriginalMethodSignature(method);
    assert original != null;
    if (backportCoreLibraryMembers.containsKey(original.holder)) {
      return rewritableMethods.getGenerator(
          backportCoreLibraryMembers.get(original.holder).descriptor,
          original.name,
          original.proto);
    }
    return rewritableMethods.getGenerator(
        original.holder.descriptor, original.name, original.proto);
  }

  public static final class RewritableMethods {
    // Map class, method, proto to a generator for creating the code and method.
    private final Map<DexString, Map<DexString, Map<DexProto, MethodGenerator>>> rewritable =
        new HashMap<>();

    public RewritableMethods(DexItemFactory factory, InternalOptions options) {
      if (!options.canUseJava7CompareAndObjectsOperations()) {
        initializeJava7CompareOperations(factory);
      }
      if (!options.canUseJava8SignedOperations()) {
        initializeJava8SignedOperations(factory);
      }
      if (!options.canUseJava8UnsignedOperations()) {
        initializeJava8UnsignedOperations(factory);
      }
    }

    boolean isEmpty() {
      return rewritable.isEmpty();
    }

    private void initializeJava7CompareOperations(DexItemFactory factory) {
      // Note: Long.compare rewriting is handled by CodeRewriter since there is a dedicated
      // bytecode which supports the operation.

      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.compare(byte a, byte b)
      DexString method = factory.createString("compare");
      DexProto proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      addGenerator(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.compare(short a, short b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      addGenerator(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;
      // int Integer.compare(int a, int b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;
      // int Boolean.compare(boolean a, boolean b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.booleanType, factory.booleanType);
      addGenerator(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // Character
      clazz = factory.boxedCharDescriptor;
      // int Character.compare(char a, char b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.charType, factory.charType);
      addGenerator(new MethodGenerator(clazz, method, proto, CharacterMethods::new));

      // Objects
      clazz = factory.objectsDescriptor;

      // int Objects.compare(T a, T b, Comparator<? super T> c)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.objectType, factory.objectType,
          factory.comparatorType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.deepEquals(Object a, Object b)
      method = factory.createString("deepEquals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.equals(Object a, Object b)
      method = factory.createString("equals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.hash(Object... o)
      method = factory.createString("hash");
      proto = factory.createProto(factory.intType, factory.objectArrayType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // int Objects.hashCode(Object o)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // Note: Objects.requireNonNull(T) rewriting is handled by CodeRewriter for now.

      // T Objects.requireNonNull(T obj, String message)
      method = factory.createString("requireNonNull");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.stringType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, ObjectsMethods::new, "requireNonNullMessage"));

      // String Objects.toString(Object o)
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // String Objects.toString(Object o, String nullDefault);
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType, factory.stringType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, ObjectsMethods::new, "toStringDefault"));
    }

    private void initializeJava8SignedOperations(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.hashCode(byte i)
      DexString method = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addGenerator(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.hashCode(short i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.shortType);
      addGenerator(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.hashCode(int i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.max(int a, int b)
      method = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.min(int a, int b)
      method = factory.createString("min");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.sum(int a, int b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // Double
      clazz = factory.boxedDoubleDescriptor;

      // int Double.hashCode(double d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.doubleType);
      addGenerator(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.max(double a, double b)
      method = factory.createString("max");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addGenerator(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.min(double a, double b)
      method = factory.createString("min");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addGenerator(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // double Double.sum(double a, double b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addGenerator(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // boolean Double.isFinite(double a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.doubleType);
      addGenerator(new MethodGenerator(clazz, method, proto, DoubleMethods::new));

      // Float
      clazz = factory.boxedFloatDescriptor;

      // int Float.hashCode(float d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.floatType);
      addGenerator(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.max(float a, float b)
      method = factory.createString("max");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addGenerator(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.min(float a, float b)
      method = factory.createString("min");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addGenerator(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // float Float.sum(float a, float b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addGenerator(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // boolean Float.isFinite(float a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.floatType);
      addGenerator(new MethodGenerator(clazz, method, proto, FloatMethods::new));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;

      // int Boolean.hashCode(boolean b)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.booleanType);
      addGenerator(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalAnd(boolean a, boolean b)
      method = factory.createString("logicalAnd");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addGenerator(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalOr(boolean a, boolean b)
      method = factory.createString("logicalOr");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addGenerator(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // boolean Boolean.logicalXor(boolean a, boolean b)
      method = factory.createString("logicalXor");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addGenerator(new MethodGenerator(clazz, method, proto, BooleanMethods::new));

      // Long
      clazz = factory.boxedLongDescriptor;

      // int Long.hashCode(long i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.max(long a, long b)
      method = factory.createString("max");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.min(long a, long b)
      method = factory.createString("min");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.sum(long a, long b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // Character
      clazz = factory.boxedCharDescriptor;

      // int Character.hashCode(char i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.charType);
      addGenerator(new MethodGenerator(clazz, method, proto, CharacterMethods::new));

      // Objects
      clazz = factory.objectsDescriptor;

      // boolean Objects.isNull(Object o)
      method = factory.createString("isNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // boolean Objects.nonNull(Object a)
      method = factory.createString("nonNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addGenerator(new MethodGenerator(clazz, method, proto, ObjectsMethods::new));

      // Math & StrictMath, which have some symmetric, binary-compatible APIs
      DexString[] mathClasses = { factory.mathDescriptor, factory.strictMathDescriptor };
      for (DexString mathClass : mathClasses) {
        clazz = mathClass;

        // int {Math,StrictMath}.addExact(int, int)
        method = factory.createString("addExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "addExactInt"));

        // long {Math,StrictMath}.addExact(long, long)
        method = factory.createString("addExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "addExactLong"));

        // int {Math,StrictMath}.floorDiv(int, int)
        method = factory.createString("floorDiv");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorDivInt"));

        // long {Math,StrictMath}.floorDiv(long)
        method = factory.createString("floorDiv");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorDivLong"));

        // int {Math,StrictMath}.floorMod(int, int)
        method = factory.createString("floorMod");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorModInt"));

        // long {Math,StrictMath}.floorMod(long)
        method = factory.createString("floorMod");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "floorModLong"));

        // int {Math,StrictMath}.multiplyExact(int, int)
        method = factory.createString("multiplyExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addGenerator(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "multiplyExactInt"));

        // long {Math,StrictMath}.multiplyExact(long)
        method = factory.createString("multiplyExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addGenerator(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "multiplyExactLong"));

        // double {Math,StrictMath}.nextDown(double)
        method = factory.createString("nextDown");
        proto = factory.createProto(factory.doubleType, factory.doubleType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "nextDownDouble"));

        // float {Math,StrictMath}.nextDown(float)
        method = factory.createString("nextDown");
        proto = factory.createProto(factory.floatType, factory.floatType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "nextDownFloat"));

        // int {Math,StrictMath}.subtractExact(int, int)
        method = factory.createString("subtractExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        addGenerator(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "subtractExactInt"));

        // long {Math,StrictMath}.subtractExact(long, long)
        method = factory.createString("subtractExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        addGenerator(
            new MethodGenerator(clazz, method, proto, MathMethods::new, "subtractExactLong"));

        // int {Math,StrictMath}.toIntExact(long)
        method = factory.createString("toIntExact");
        proto = factory.createProto(factory.intType, factory.longType);
        addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new));
      }

      // Math (APIs which are not mirrored by StrictMath)
      clazz = factory.mathDescriptor;

      // int Math.decrementExact(int)
      method = factory.createString("decrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "decrementExactInt"));

      // long Math.decrementExact(long)
      method = factory.createString("decrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "decrementExactLong"));

      // int Math.incrementExact(int)
      method = factory.createString("incrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "incrementExactInt"));

      // long Math.incrementExact(long)
      method = factory.createString("incrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, MathMethods::new, "incrementExactLong"));

      // int Math.negateExact(int)
      method = factory.createString("negateExact");
      proto = factory.createProto(factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "negateExactInt"));

      // long Math.negateExact(long)
      method = factory.createString("negateExact");
      proto = factory.createProto(factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, MathMethods::new, "negateExactLong"));
    }

    private void initializeJava8UnsignedOperations(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;

      // int Byte.toUnsignedInt(byte value)
      DexString method = factory.createString("toUnsignedInt");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addGenerator(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // long Byte.toUnsignedLong(byte value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.byteType);
      addGenerator(new MethodGenerator(clazz, method, proto, ByteMethods::new));

      // Short
      clazz = factory.boxedShortDescriptor;

      // int Short.toUnsignedInt(short value)
      method = factory.createString("toUnsignedInt");
      proto = factory.createProto(factory.intType, factory.shortType);
      addGenerator(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // long Short.toUnsignedLong(short value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.shortType);
      addGenerator(new MethodGenerator(clazz, method, proto, ShortMethods::new));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.divideUnsigned(int a, int b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.remainderUnsigned(int a, int b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // int Integer.compareUnsigned(int a, int b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // long Integer.toUnsignedLong(int value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.intType);
      addGenerator(new MethodGenerator(clazz, method, proto, IntegerMethods::new));

      // Long
      clazz = factory.boxedLongDescriptor;

      // long Long.divideUnsigned(long a, long b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // long Long.remainderUnsigned(long a, long b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // int Long.compareUnsigned(long a, long b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.longType, factory.longType);
      addGenerator(new MethodGenerator(clazz, method, proto, LongMethods::new));

      // String
      clazz = factory.stringDescriptor;

      // String String.join(CharSequence, CharSequence...)
      method = factory.createString("join");
      proto = factory.createProto(factory.stringType, factory.charSequenceType,
          factory.charSequenceArrayType);
      addGenerator(new MethodGenerator(clazz, method, proto, StringMethods::new, "joinArray"));

      // String String.join(CharSequence, Iterable<? extends CharSequence>)
      method = factory.createString("join");
      proto =
          factory.createProto(factory.stringType, factory.charSequenceType, factory.iterableType);
      addGenerator(
          new MethodGenerator(clazz, method, proto, StringMethods::new, "joinIterable"));
    }

    private void addGenerator(MethodGenerator generator) {
      rewritable.computeIfAbsent(generator.clazz, k -> new HashMap<>())
          .computeIfAbsent(generator.method, k -> new HashMap<>())
          .put(generator.proto, generator);
    }

    public MethodGenerator getGenerator(DexString clazz, DexString method, DexProto proto) {
      Map<DexString, Map<DexProto, MethodGenerator>> classMap = rewritable.get(clazz);
      if (classMap != null) {
        Map<DexProto, MethodGenerator> methodMap = classMap.get(method);
        if (methodMap != null) {
          return methodMap.get(proto);
        }
      }
      return null;
    }

    public static class MethodGenerator {
      private final TemplateMethodFactory factory;
      private final String methodName;
      private final DexString clazz;
      private final DexString method;
      private final DexProto proto;
      private DexMethod dexMethod;

      public MethodGenerator(DexString clazz, DexString method, DexProto proto,
          TemplateMethodFactory factory) {
        this(clazz, method, proto, factory, method.toString());
      }

      public MethodGenerator(DexString clazz, DexString method, DexProto proto,
          TemplateMethodFactory factory, String methodName) {
        this.factory = factory;
        this.methodName = methodName;
        this.clazz = clazz;
        this.method = method;
        this.proto = proto;
      }

      public DexMethod generateMethod(DexItemFactory factory) {
        if (dexMethod != null) {
          return dexMethod;
        }
        String unqualifiedName =
            DescriptorUtils.getUnqualifiedClassNameFromDescriptor(clazz.toString());
        String descriptor =
            UTILITY_CLASS_DESCRIPTOR_PREFIX + '$' + unqualifiedName + '$' + methodName + ';';
        DexType clazz = factory.createType(descriptor);
        dexMethod = factory.createMethod(clazz, proto, method);
        return dexMethod;
      }

      public TemplateMethodCode generateTemplateMethod(InternalOptions options, DexMethod method) {
        return factory.create(options, method, methodName);
      }
    }

    private interface TemplateMethodFactory {
      TemplateMethodCode create(InternalOptions options, DexMethod method, String name);
    }
  }
}
