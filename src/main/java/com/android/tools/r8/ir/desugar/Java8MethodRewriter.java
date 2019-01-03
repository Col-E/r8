// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
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
import com.android.tools.r8.ir.desugar.Java8MethodRewriter.RewritableMethods.MethodGenerator;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class Java8MethodRewriter {
  private static final String UTILITY_CLASS_DESCRIPTOR_PREFIX = "L$r8$java8methods$utility";
  private final Set<DexType> holders = Sets.newConcurrentHashSet();
  private final IRConverter converter;
  private final DexItemFactory factory;
  private final RewritableMethods rewritableMethods;

  private Map<DexMethod, MethodGenerator> methodGenerators = new ConcurrentHashMap<>();

  public Java8MethodRewriter(IRConverter converter) {
    this.converter = converter;
    this.factory = converter.appInfo.dexItemFactory;
    this.rewritableMethods = new RewritableMethods(factory);
  }

  public void desugar(IRCode code) {
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeStatic()) {
        continue;
      }
      InvokeStatic invoke = instruction.asInvokeStatic();

      MethodGenerator generator = getMethodGeneratorOrNull(converter, invoke.getInvokedMethod());
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

  public void synthesizeUtilityClass(Builder<?> builder, InternalOptions options) {
    if (holders.isEmpty()) {
      return;
    }
    Set<DexProgramClass> referencingClasses = Sets.newConcurrentHashSet();
    for (DexType holder : holders) {
      DexClass definitionFor = converter.appInfo.definitionFor(holder);
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
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              new DexEncodedMethod[]{dexEncodedMethod},
              DexEncodedMethod.EMPTY_ARRAY,
              factory.getSkipNameValidationForTesting(),
              referencingClasses);
      code.setUpContext(utilityClass);
      boolean addToMainDexList = referencingClasses.stream()
          .anyMatch(clazz -> converter.appInfo.isInMainDexList(clazz.type));
      converter.optimizeSynthesizedClass(utilityClass);
      builder.addSynthesizedClass(utilityClass, addToMainDexList);
    }
  }

  private MethodGenerator getMethodGeneratorOrNull(IRConverter converter, DexMethod method) {
    DexMethod original = converter.graphLense().getOriginalMethodSignature(method);
    assert original != null;
    return rewritableMethods.getGenerator(
        original.holder.descriptor, original.name, original.proto);
  }

  private static final class ByteMethods extends TemplateMethodCode {
    ByteMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static ByteMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new ByteMethods(options, method, "hashCodeImpl");
    }

    public static int hashCodeImpl(byte i) {
      return Byte.valueOf(i).hashCode();
    }
  }


  private static final class ShortMethods extends TemplateMethodCode {
    ShortMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static ShortMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new ShortMethods(options, method, "hashCodeImpl");
    }

    public static int hashCodeImpl(short i) {
      return Short.valueOf(i).hashCode();
    }
  }

  private static final class IntegerMethods extends TemplateMethodCode {
    IntegerMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static IntegerMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "hashCodeImpl");
    }

    public static IntegerMethods maxCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "maxImpl");
    }

    public static IntegerMethods minCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "minImpl");
    }

    public static IntegerMethods sumCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "sumImpl");
    }

    public static int hashCodeImpl(int i) {
      return Integer.valueOf(i).hashCode();
    }

    public static int maxImpl(int a, int b) {
      return java.lang.Math.max(a, b);
    }

    public static int minImpl(int a, int b) {
      return java.lang.Math.min(a, b);
    }

    public static int sumImpl(int a, int b) {
      return a + b;
    }
  }

  private static final class DoubleMethods extends TemplateMethodCode {
    DoubleMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static DoubleMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "hashCodeImpl");
    }

    public static DoubleMethods maxCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "maxImpl");
    }

    public static DoubleMethods minCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "minImpl");
    }

    public static DoubleMethods sumCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "sumImpl");
    }

    public static DoubleMethods isFiniteCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "isFiniteImpl");
    }

    public static int hashCodeImpl(double d) {
      return Double.valueOf(d).hashCode();
    }

    public static double maxImpl(double a, double b) {
      return java.lang.Math.max(a, b);
    }

    public static double minImpl(double a, double b) {
      return java.lang.Math.min(a, b);
    }

    public static double sumImpl(double a, double b) {
      return a + b;
    }

    public static boolean isFiniteImpl(double d) {
      Double boxed = Double.valueOf(d);
      return !boxed.isInfinite() && !boxed.isNaN();
    }
  }

  private static final class FloatMethods extends TemplateMethodCode {
    FloatMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static FloatMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new FloatMethods(options, method, "hashCodeImpl");
    }

    public static FloatMethods maxCode(InternalOptions options, DexMethod method) {
      return new FloatMethods(options, method, "maxImpl");
    }

    public static FloatMethods minCode(InternalOptions options, DexMethod method) {
      return new FloatMethods(options, method, "minImpl");
    }

    public static FloatMethods sumCode(InternalOptions options, DexMethod method) {
      return new FloatMethods(options, method, "sumImpl");
    }

    public static FloatMethods isFiniteCode(InternalOptions options, DexMethod method) {
      return new FloatMethods(options, method, "isFiniteImpl");
    }

    public static int hashCodeImpl(float d) {
      return Float.valueOf(d).hashCode();
    }

    public static float maxImpl(float a, float b) {
      return java.lang.Math.max(a, b);
    }

    public static float minImpl(float a, float b) {
      return java.lang.Math.min(a, b);
    }

    public static float sumImpl(float a, float b) {
      return a + b;
    }

    public static boolean isFiniteImpl(float d) {
      Float boxed = Float.valueOf(d);
      return !boxed.isInfinite() && !boxed.isNaN();
    }
  }

  private static final class BooleanMethods extends TemplateMethodCode {
    BooleanMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static BooleanMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new BooleanMethods(options, method, "hashCodeImpl");
    }

    public static BooleanMethods logicalAndCode(InternalOptions options, DexMethod method) {
      return new BooleanMethods(options, method, "logicalAndImpl");
    }

    public static BooleanMethods logicalOrCode(InternalOptions options, DexMethod method) {
      return new BooleanMethods(options, method, "logicalOrImpl");
    }

    public static BooleanMethods logicalXorCode(InternalOptions options, DexMethod method) {
      return new BooleanMethods(options, method, "logicalXorImpl");
    }

    public static int hashCodeImpl(boolean b) {
      return Boolean.valueOf(b).hashCode();
    }

    public static boolean logicalAndImpl(boolean a, boolean b) {
      return a && b;
    }

    public static boolean logicalOrImpl(boolean a, boolean b) {
      return a || b;
    }

    public static boolean logicalXorImpl(boolean a, boolean b) {
      return a ^ b;
    }
  }

  public static final class RewritableMethods {
    // Map class, method, proto to a generator for creating the code and method.
    private final Map<DexString, Map<DexString, Map<DexProto, MethodGenerator>>> rewritable;


    public RewritableMethods(DexItemFactory factory) {
      rewritable = new HashMap<>();
      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.hashCode(byte i)
      DexString method = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(ByteMethods::hashCodeCode, clazz, method, proto));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.hashCode(short i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.shortType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(ShortMethods::hashCodeCode, clazz, method, proto));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.hashCode(int i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::hashCodeCode, clazz, method, proto));

      // int Integer.max(int a, int b)
      method = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::maxCode, clazz, method, proto));

      // int Integer.min(int a, int b)
      method = factory.createString("min");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::minCode, clazz, method, proto));

      // int Integer.sum(int a, int b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::sumCode, clazz, method, proto));

      // Double
      clazz = factory.boxedDoubleDescriptor;

      // int Double.hashCode(double d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.doubleType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(DoubleMethods::hashCodeCode, clazz, method, proto));

      // double Double.max(double a, double b)
      method = factory.createString("max");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(DoubleMethods::maxCode, clazz, method, proto));

      // double Double.min(double a, double b)
      method = factory.createString("min");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(DoubleMethods::minCode, clazz, method, proto));

      // double Double.sum(double a, double b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(DoubleMethods::sumCode, clazz, method, proto));

      // boolean Double.isFinite(double a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.doubleType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(DoubleMethods::isFiniteCode, clazz, method, proto));

      // Float
      clazz = factory.boxedFloatDescriptor;

      // int Float.hashCode(float d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.floatType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(FloatMethods::hashCodeCode, clazz, method, proto));

      // float Float.max(float a, float b)
      method = factory.createString("max");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(FloatMethods::maxCode, clazz, method, proto));

      // float Float.min(float a, float b)
      method = factory.createString("min");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(FloatMethods::minCode, clazz, method, proto));

      // float Float.sum(float a, float b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(FloatMethods::sumCode, clazz, method, proto));

      // boolean Float.isFinite(float a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.floatType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(FloatMethods::isFiniteCode, clazz, method, proto));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;

      // int Boolean.hashCode(boolean b)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.booleanType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(BooleanMethods::hashCodeCode, clazz, method, proto));

      // boolean Boolean.logicalAnd(boolean a, boolean b)
      method = factory.createString("logicalAnd");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(BooleanMethods::logicalAndCode, clazz, method, proto));

      // boolean Boolean.logicalOr(boolean a, boolean b)
      method = factory.createString("logicalOr");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(BooleanMethods::logicalOrCode, clazz, method, proto));

      // boolean Boolean.logicalXor(boolean a, boolean b)
      method = factory.createString("logicalXor");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(BooleanMethods::logicalXorCode, clazz, method, proto));
    }

    private Map<DexString, Map<DexProto, MethodGenerator>> addOrGetClass(DexString clazz) {
      return rewritable.computeIfAbsent(clazz, k -> new HashMap<>());
    }

    private Map<DexProto, MethodGenerator> addOrGetMethod(
        DexString clazz, DexString method) {
      return addOrGetClass(clazz).computeIfAbsent(method, k -> new HashMap<>());
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
      private final BiFunction<InternalOptions, DexMethod, TemplateMethodCode> generator;
      private final DexString clazz;
      private final DexString method;
      private final DexProto proto;
      private DexMethod dexMethod;

      public MethodGenerator(
          BiFunction<InternalOptions, DexMethod, TemplateMethodCode> generator,
          DexString clazz, DexString method, DexProto proto) {
        this.generator = generator;
        this.clazz = clazz;
        this.method = method;
        this.proto = proto;
      }

      public DexMethod generateMethod(DexItemFactory factory) {
        if (dexMethod != null) {
          return dexMethod;
        }
        String clazzDescriptor = DescriptorUtils.getSimpleClassNameFromDescriptor(clazz.toString());
        String postFix = "$" + clazzDescriptor + "$" + method + "$" + proto.shorty.toString();
        DexType clazz = factory.createType(UTILITY_CLASS_DESCRIPTOR_PREFIX + postFix + ";");
        dexMethod = factory.createMethod(clazz, proto, method);
        return dexMethod;
      }

      public TemplateMethodCode generateTemplateMethod(InternalOptions options, DexMethod method) {
        return generator.apply(options, method);
      }
    }
  }
}
