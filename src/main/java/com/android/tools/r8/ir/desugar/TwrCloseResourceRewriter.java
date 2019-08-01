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
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

// Try with resources outlining processor. Handles $closeResource methods
// synthesized by java 9 compiler.
//
// Works in two phases:
//   a. during method code processing finds all references to $closeResource(...) synthesized
//      by java compiler, and replaces them with references to a special utility class method.
//   b. after all methods are processed and if there was at least one method referencing
//      $closeResource(...), it synthesizes utility class with appropriate methods.
//
// Note that we don't remove $closeResource(...) synthesized by java compiler, relying on
// tree shaking to remove them since now they should not be referenced.
//
public final class TwrCloseResourceRewriter {

  public static final String UTILITY_CLASS_NAME = "$r8$twr$utility";
  public static final String UTILITY_CLASS_DESCRIPTOR = "L$r8$twr$utility;";

  private final AppView<?> appView;
  private final IRConverter converter;

  private final DexMethod twrCloseResourceMethod;

  private final Set<DexProgramClass> referencingClasses = Sets.newConcurrentHashSet();

  public TwrCloseResourceRewriter(AppView<?> appView, IRConverter converter) {
    this.appView = appView;
    this.converter = converter;

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType twrUtilityClass = dexItemFactory.createType(UTILITY_CLASS_DESCRIPTOR);
    DexProto twrCloseResourceProto =
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.throwableType, dexItemFactory.objectType);
    this.twrCloseResourceMethod =
        dexItemFactory.createMethod(
            twrUtilityClass, twrCloseResourceProto, dexItemFactory.twrCloseResourceMethodName);
  }

  // Rewrites calls to $closeResource() method. Can be invoked concurrently.
  public void rewriteMethodCode(IRCode code) {
    InstructionListIterator iterator = code.instructionListIterator();
    AppInfo appInfo = appView.appInfo();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeStatic()) {
        continue;
      }
      InvokeStatic invoke = instruction.asInvokeStatic();
      if (!isSynthesizedCloseResourceMethod(invoke.getInvokedMethod(), appView)) {
        continue;
      }

      // Replace with a call to a synthetic utility with the only
      // implementation of the method $closeResource.
      assert invoke.outValue() == null;
      assert invoke.inValues().size() == 2;
      iterator.replaceCurrentInstruction(
          new InvokeStatic(twrCloseResourceMethod, null, invoke.inValues()));

      // Mark as a class referencing utility class.
      referencingClasses.add(appInfo.definitionFor(code.method.method.holder).asProgramClass());
    }
  }

  public static boolean isSynthesizedCloseResourceMethod(DexMethod method, AppView<?> appView) {
    DexMethod original = appView.graphLense().getOriginalMethodSignature(method);
    assert original != null;
    // We consider all methods of *any* class with expected name and signature
    // to be synthesized by java 9 compiler for try-with-resources, reasoning:
    //
    //  * we need to look to all potential classes because the calls might be
    //    moved by inlining.
    //  * theoretically we could check appropriate encoded method for having
    //    right attributes, but it still does not guarantee much since we also
    //    need to look into code and doing this seems an overkill
    //
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return original.name == dexItemFactory.twrCloseResourceMethodName
        && original.proto == dexItemFactory.twrCloseResourceMethodProto;
  }

  public void synthesizeUtilityClass(
      Builder<?> builder, ExecutorService executorService, InternalOptions options)
      throws ExecutionException {
    if (referencingClasses.isEmpty()) {
      return;
    }

    // The only encoded method.
    TemplateMethodCode code = new CloseResourceMethodCode(options, twrCloseResourceMethod);
    MethodAccessFlags flags = MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC, false);
    DexEncodedMethod method = new DexEncodedMethod(twrCloseResourceMethod,
        flags, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), code);

    // Create utility class.
    DexProgramClass utilityClass =
        new DexProgramClass(
            twrCloseResourceMethod.holder,
            null,
            new SynthesizedOrigin("twr utility class", getClass()),
            ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC),
            appView.dexItemFactory().objectType,
            DexTypeList.empty(),
            null,
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            new DexEncodedMethod[] {method},
            DexEncodedMethod.EMPTY_ARRAY,
            appView.dexItemFactory().getSkipNameValidationForTesting(),
            referencingClasses);

    code.setUpContext(utilityClass);

    // Process created class and method.
    AppInfo appInfo = appView.appInfo();
    boolean addToMainDexList =
        referencingClasses.stream().anyMatch(clazz -> appInfo.isInMainDexList(clazz.type));
    appInfo.addSynthesizedClass(utilityClass);
    converter.optimizeSynthesizedClass(utilityClass, executorService);
    builder.addSynthesizedClass(utilityClass, addToMainDexList);
  }

  private static final class CloseResourceMethodCode extends TemplateMethodCode {
    private static final String TEMPLATE_METHOD_NAME = "closeResourceImpl";
    private static final String TEMPLATE_METHOD_DESC = "(Ljava/lang/Throwable;Ljava/lang/Object;)V";

    CloseResourceMethodCode(InternalOptions options, DexMethod method) {
      super(options, method, TEMPLATE_METHOD_NAME, TEMPLATE_METHOD_DESC);
    }

    // The following method defines the code of
    //
    //     public static void $closeResource(Throwable throwable, Object resource)
    //
    // method to be inserted into utility class, and will be used instead
    // of the following implementation added by java 9 compiler:
    //
    //     private static void $closeResource(Throwable, AutoCloseable);
    //        0: aload_0
    //        1: ifnull        22
    //        4: aload_1
    //        5: invokeinterface #1,  1  // java/lang/AutoCloseable.close:()V
    //       10: goto          28
    //       13: astore_2
    //       14: aload_0
    //       15: aload_2
    //       16: invokevirtual #3 // java/lang/Throwable.addSuppressed:(Ljava/lang/Throwable;)V
    //       19: goto          28
    //       22: aload_1
    //       23: invokeinterface #1,  1  // java/lang/AutoCloseable.close:()V
    //       28: return
    //
    public static void closeResourceImpl(Throwable throwable, Object resource) throws Throwable {
      try {
        if (resource instanceof AutoCloseable) {
          ((AutoCloseable) resource).close();
        } else {
          try {
            Method method = resource.getClass().getMethod("close");
            method.invoke(resource);
          } catch (NoSuchMethodException | SecurityException e) {
            throw new AssertionError(resource.getClass() + " does not have a close() method.", e);
          } catch (IllegalAccessException
              | IllegalArgumentException | ExceptionInInitializerError e) {
            throw new AssertionError("Fail to call close() on " + resource.getClass(), e);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        }
      } catch (Throwable e) {
        // NOTE: we don't call addSuppressed(...) since the call will be removed
        // by try-with-resource desugar anyways.
        throw throwable != null ? throwable : e;
      }
    }
  }
}
