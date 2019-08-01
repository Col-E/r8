// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ServiceLoaderRewriter will attempt to rewrite calls on the form of: ServiceLoader.load(X.class,
 * X.class.getClassLoader()).iterator() ... to Arrays.asList(new X[] { new Y(), ..., new Z()
 * }).iterator() for classes Y..Z specified in the META-INF/services/X.
 *
 * <p>The reason for this optimization is to not have the ServiceLoader.load on the distributed R8
 * in AGP, since this can potentially conflict with debug versions added to a build.gradle file as:
 * classpath 'com.android.tools:r8:a.b.c' Additionally, it might also result in improved performance
 * because ServiceLoader.load is really slow on Android because it has to do a reflective lookup.
 *
 * <p>A call to ServiceLoader.load(X.class) is implicitly the same as ServiceLoader.load(X.class,
 * Thread.getContextClassLoader()) which can have different behavior in Android if a process host
 * multiple applications:
 *
 * <pre>
 * See <a href="https://stackoverflow.com/questions/13407006/android-class-loader-may-fail-for-
 * processes-that-host-multiple-applications">https://stackoverflow.com/questions/13407006/
 * android-class-loader-may-fail-for-processes-that-host-multiple-applications</a>
 * </pre>
 *
 * We therefore only conservatively rewrite if the invoke is on is on the form
 * ServiceLoader.load(X.class, X.class.getClassLoader()) or ServiceLoader.load(X.class, null).
 *
 * Android Nougat do not use ClassLoader.getSystemClassLoader() when passing null and will
 * almost certainly fail when trying to find the service. It seems unlikely that programs rely on
 * this behaviour.
 */
public class ServiceLoaderRewriter {

  public static void rewrite(IRCode code, AppView<? extends AppInfoWithLiveness> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    InstructionListIterator instructionIterator = code.instructionListIterator();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();

      // Check if instruction is an invoke static on the desired form of ServiceLoader.load.
      if (!instruction.isInvokeStatic()
          || instruction.asInvokeStatic().getInvokedMethod()
              != factory.serviceLoaderMethods.loadWithClassLoader) {
        continue;
      }

      InvokeStatic serviceLoaderLoad = instruction.asInvokeStatic();
      Value serviceLoaderLoadOut = serviceLoaderLoad.outValue();
      if (serviceLoaderLoadOut.numberOfAllUsers() != 1
          || serviceLoaderLoadOut.numberOfPhiUsers() != 0) {
        continue;
      }

      // Check that the only user is a call to iterator().
      if (!serviceLoaderLoadOut.singleUniqueUser().isInvokeVirtual()
          || serviceLoaderLoadOut.singleUniqueUser().asInvokeVirtual().getInvokedMethod()
              != factory.serviceLoaderMethods.iterator) {
        continue;
      }

      // Check that the first argument is a const class.
      Value argument = serviceLoaderLoad.inValues().get(0).getAliasedValue();
      if (argument.isPhi() || !argument.definition.isConstClass()) {
        continue;
      }

      ConstClass constClass = argument.getConstInstruction().asConstClass();

      // Check that the service is not kept.
      if (appView.appInfo().isPinned(constClass.getValue())) {
        continue;
      }

      // Check that the service is configured in the META-INF/services.
      if (!appView.appServices().allServiceTypes().contains(constClass.getValue())) {
        // Error already reported in the Enqueuer.
        continue;
      }

      // Check that ClassLoader used is the ClassLoader defined for the the service configuration
      // that we are instantiating or NULL.
      InvokeVirtual classLoaderInvoke =
          serviceLoaderLoad.inValues().get(1).definition.asInvokeVirtual();
      boolean isGetClassLoaderOnConstClassOrNull =
          serviceLoaderLoad.inValues().get(1).getTypeLattice().isNullType()
              || (classLoaderInvoke != null
                  && classLoaderInvoke.inValues().size() == 1
                  && classLoaderInvoke.getReceiver().getAliasedValue().isConstClass()
                  && classLoaderInvoke
                          .getReceiver()
                          .getAliasedValue()
                          .getConstInstruction()
                          .asConstClass()
                          .getValue()
                      == constClass.getValue());
      if (!isGetClassLoaderOnConstClassOrNull) {
        continue;
      }

      List<DexType> dexTypes =
          appView.appServices().serviceImplementationsFor(constClass.getValue());
      List<DexClass> classes = new ArrayList<>(dexTypes.size());
      boolean seenNull = false;
      for (DexType serviceImpl : dexTypes) {
        DexClass serviceImplClazz = appView.definitionFor(serviceImpl);
        if (serviceImplClazz == null) {
          seenNull = true;
        }
        classes.add(serviceImplClazz);
      }

      if (seenNull) {
        continue;
      }

      // We can perform the rewrite of the ServiceLoader.load call.
      new Rewriter(appView, code, instructionIterator, serviceLoaderLoad)
          .perform(classLoaderInvoke, constClass.getValue(), classes);
    }
  }

  /**
   * Rewriter will look assume that code is on the form:
   *
   * <pre>
   * ConstClass         v1 <- X
   * ConstClass         v2 <- X
   * Invoke-Virtual     v3 <- v2; method: java.lang.ClassLoader java.lang.Class.getClassLoader()
   * Invoke-Static      v4 <- v1, v3; method: java.util.ServiceLoader java.util.ServiceLoader
   *     .load(java.lang.Class, java.lang.ClassLoader)
   * Invoke-Virtual     v5 <- v4; method: java.util.Iterator java.util.ServiceLoader.iterator()
   * </pre>
   *
   * and rewrites it to for classes impl(X) defined in META-INF/services/X:
   *
   * <pre>
   * ConstClass         v1 <- X
   * ConstClass         v2 <- X
   * ConstNumber        va <-  impl(X).size() (INT)
   * NewArrayEmpty      vb <- va X[]
   * for i = 0 to C - 1:
   *   ConstNumber        vc(i) <-  i (INT)
   *   NewInstance        vd <-  impl(X).get(i)
   *   Invoke-Direct      vd; method: void impl(X).get(i).<init>()
   *   ArrayPut           vb, vc(i), vd
   * end for
   * Invoke-Static      ve <- vb; method: java.util.List java.util.Arrays.asList(java.lang.Object[])
   * Invoke-Interface   v5 <- ve; method: java.util.Iterator java.util.List.iterator()
   * </pre>
   *
   * We rely on the DeadCodeRemover to remove the ConstClasses and any aliased values no longer
   * used.
   */
  private static class Rewriter {

    private final AppView appView;
    private final DexItemFactory factory;
    private final IRCode code;
    private final InvokeStatic serviceLoaderLoad;

    private InstructionListIterator iterator;
    private MemberType memberType;
    private Value valueArray;
    private int index = 0;

    public Rewriter(
        AppView appView,
        IRCode code,
        InstructionListIterator iterator,
        InvokeStatic serviceLoaderLoad) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.iterator = iterator;
      this.code = code;
      this.serviceLoaderLoad = serviceLoaderLoad;
    }

    public void perform(InvokeVirtual classLoaderInvoke, DexType dexType, List<DexClass> classes) {
      assert valueArray == null;
      assert memberType == null;

      // Remove the ClassLoader call since this can throw and will not be removed otherwise.
      if (classLoaderInvoke != null) {
        clearGetClassLoader(classLoaderInvoke);
        iterator.nextUntil(i -> i == serviceLoaderLoad);
      }

      // Remove the ServiceLoader.load call.
      InvokeVirtual serviceLoaderIterator =
          serviceLoaderLoad.outValue().singleUniqueUser().asInvokeVirtual();
      iterator.replaceCurrentInstruction(code.createConstNull());

      // Build the array for the "loaded" classes.
      ConstNumber arrayLength = code.createIntConstant(classes.size());
      arrayLength.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(arrayLength);

      DexType arrayType = factory.createArrayType(1, dexType);
      TypeLatticeElement arrayLatticeElement =
          TypeLatticeElement.fromDexType(arrayType, definitelyNotNull(), appView);
      valueArray = code.createValue(arrayLatticeElement);
      NewArrayEmpty newArrayEmpty =
          new NewArrayEmpty(valueArray, arrayLength.outValue(), arrayType);
      newArrayEmpty.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(newArrayEmpty);

      this.memberType = MemberType.fromDexType(dexType);

      // Add all new instances to the array.
      classes.forEach(this::addNewServiceAndPutInArray);

      // Build the Arrays.asList(...) instruction.
      Value vArrayAsList =
          code.createValue(
              TypeLatticeElement.fromDexType(factory.listType, definitelyNotNull(), appView));
      InvokeStatic arraysAsList =
          new InvokeStatic(
              factory.utilArraysMethods.asList, vArrayAsList, ImmutableList.of(valueArray));
      arraysAsList.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(arraysAsList);

      // Find the iterator instruction and replace it.
      iterator.nextUntil(x -> x == serviceLoaderIterator);

      DexMethod method =
          factory.createMethod(
              factory.listType, factory.createProto(factory.iteratorType), "iterator");
      InvokeInterface arrayIterator =
          new InvokeInterface(
              method, serviceLoaderIterator.outValue(), ImmutableList.of(vArrayAsList));
      iterator.replaceCurrentInstruction(arrayIterator);
    }

    private void addNewServiceAndPutInArray(DexClass clazz) {
      ConstNumber indexInArray = code.createIntConstant(index++);
      indexInArray.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(indexInArray);

      TypeLatticeElement clazzLatticeElement =
          TypeLatticeElement.fromDexType(clazz.type, definitelyNotNull(), appView);
      Value vInstance = code.createValue(clazzLatticeElement);
      NewInstance newInstance = new NewInstance(clazz.type, vInstance);
      newInstance.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(newInstance);

      DexMethod method = clazz.getDefaultInitializer().method;
      assert method.getArity() == 0;
      InvokeDirect invokeDirect =
          new InvokeDirect(method, null, Collections.singletonList(vInstance));
      invokeDirect.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(invokeDirect);

      ArrayPut put = new ArrayPut(memberType, valueArray, indexInArray.outValue(), vInstance);
      put.setPosition(serviceLoaderLoad.getPosition());
      iterator.add(put);
    }

    private void clearGetClassLoader(InvokeVirtual classLoaderInvoke) {
      while (iterator.hasPrevious()) {
        Instruction instruction = iterator.previous();
        if (instruction == classLoaderInvoke) {
          iterator.replaceCurrentInstruction(code.createConstNull());
          break;
        }
      }
    }
  }
}
