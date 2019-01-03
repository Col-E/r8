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


  private static final class IntegerMethods extends TemplateMethodCode {
    IntegerMethods(InternalOptions options, DexMethod method, String methodName, String desc) {
      super(options, method, methodName, desc);
    }

    public static IntegerMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "hashCodeImpl", "(I)I");
    }

    public static IntegerMethods maxCode(InternalOptions options, DexMethod method) {
      return new IntegerMethods(options, method, "maxImpl", "(II)I");
    }

    public static int hashCodeImpl(int value) {
      return Integer.valueOf(value).hashCode();
    }

     public static int maxImpl(int a, int b) {
       return java.lang.Math.max(a, b);
     }
  }

  private static final class DoubleMethods extends TemplateMethodCode {
    DoubleMethods(InternalOptions options, DexMethod method, String methodName, String desc) {
      super(options, method, methodName, desc);
    }

    public static DoubleMethods hashCodeCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "hashCodeImpl", "(D)I");
    }

    public static DoubleMethods maxCode(InternalOptions options, DexMethod method) {
      return new DoubleMethods(options, method, "maxImpl", "(DD)D");
    }

    public static int hashCodeImpl(double value) {
      return Double.valueOf(value).hashCode();
    }

     public static double maxImpl(double a, double b) {
       return java.lang.Math.max(a, b);
     }
  }

  public static final class RewritableMethods {
    // Map class, method, proto to a generator for creating the code and method.
    private final Map<DexString, Map<DexString, Map<DexProto, MethodGenerator>>> rewritable;


    public RewritableMethods(DexItemFactory factory) {
      rewritable = new HashMap<>();
      // Integer
      DexString clazz = factory.boxedIntDescriptor;
      // int Integer.hashCode(int i)

      DexString method = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::hashCodeCode, clazz, method, proto));

      // int Integer.max(int a, int b)
      method = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method)
          .put(proto, new MethodGenerator(IntegerMethods::maxCode, clazz, method, proto));

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
