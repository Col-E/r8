// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory.ServiceLoaderMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.desugar.ServiceLoaderSourceCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

  private final AppView<AppInfoWithLiveness> appView;
  private final AndroidApiLevelCompute apiLevelCompute;
  private final Reporter reporter;
  private final ServiceLoaderMethods serviceLoaderMethods;

  private final List<ProgramMethod> synthesizedServiceLoadMethods = new ArrayList<>();

  public ServiceLoaderRewriter(
      AppView<AppInfoWithLiveness> appView, AndroidApiLevelCompute apiLevelCompute) {
    this.appView = appView;
    this.apiLevelCompute = apiLevelCompute;
    serviceLoaderMethods = appView.dexItemFactory().serviceLoaderMethods;
    reporter = shouldReportWhyAreYouNotInliningServiceLoaderLoad() ? appView.reporter() : null;
  }

  private boolean shouldReportWhyAreYouNotInliningServiceLoaderLoad() {
    AppInfoWithLiveness appInfo = appView.appInfo();
    return appInfo.isWhyAreYouNotInliningMethod(serviceLoaderMethods.load)
        || appInfo.isWhyAreYouNotInliningMethod(serviceLoaderMethods.loadWithClassLoader);
  }

  @SuppressWarnings("ReferenceEquality")
  public void rewrite(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    assert !methodProcessor.isPostMethodProcessor();
    InstructionListIterator instructionIterator = code.instructionListIterator();
    // Create a map from service type to loader methods local to this context since two
    // service loader calls to the same type in different methods and in the same wave can race.
    Map<DexType, DexEncodedMethod> synthesizedServiceLoaders = new IdentityHashMap<>();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();

      // Check if instruction is an invoke static on the desired form of ServiceLoader.load.
      if (!instruction.isInvokeStatic()) {
        continue;
      }

      InvokeStatic serviceLoaderLoad = instruction.asInvokeStatic();
      DexMethod invokedMethod = serviceLoaderLoad.getInvokedMethod();
      if (!serviceLoaderMethods.isLoadMethod(invokedMethod)) {
        continue;
      }

      // Check that the first argument is a const class.
      Value argument = serviceLoaderLoad.inValues().get(0).getAliasedValue();
      if (argument.isPhi() || !argument.definition.isConstClass()) {
        report(code.origin, null, "The service loader type could not be determined");
        continue;
      }

      ConstClass constClass = argument.getConstInstruction().asConstClass();

      if (invokedMethod != serviceLoaderMethods.loadWithClassLoader) {
        report(
            code.origin,
            constClass.getType(),
            "Inlining is only support for `java.util.ServiceLoader.load(java.lang.Class,"
                + " java.lang.ClassLoader)`");
        continue;
      }

      String invalidUserMessage =
          "The returned ServiceLoader instance must only be used in a call to `java.util.Iterator"
              + " java.lang.ServiceLoader.iterator()`";
      Value serviceLoaderLoadOut = serviceLoaderLoad.outValue();
      if (serviceLoaderLoadOut.numberOfAllUsers() != 1 || serviceLoaderLoadOut.hasPhiUsers()) {
        report(code.origin, constClass.getType(), invalidUserMessage);
        continue;
      }

      // Check that the only user is a call to iterator().
      if (!serviceLoaderLoadOut.singleUniqueUser().isInvokeVirtual()
          || serviceLoaderLoadOut.singleUniqueUser().asInvokeVirtual().getInvokedMethod()
              != serviceLoaderMethods.iterator) {
        report(code.origin, constClass.getType(), invalidUserMessage + ", but found other usages");
        continue;
      }

      // Check that the service is not kept.
      if (appView.appInfo().isPinnedWithDefinitionLookup(constClass.getValue())) {
        report(code.origin, constClass.getType(), "The service loader type is kept");
        continue;
      }

      // Check that the service is configured in the META-INF/services.
      AppServices appServices = appView.appServices();
      if (!appServices.allServiceTypes().contains(constClass.getValue())) {
        // Error already reported in the Enqueuer.
        continue;
      }

      // Check that we are not service loading anything from a feature into base.
      if (appServices.hasServiceImplementationsInFeature(appView, constClass.getValue())) {
        report(
            code.origin,
            constClass.getType(),
            "The service loader type has implementations in a feature split");
        continue;
      }

      // Check that ClassLoader used is the ClassLoader defined for the service configuration
      // that we are instantiating or NULL.
      if (serviceLoaderLoad.inValues().get(1).isPhi()) {
        report(
            code.origin,
            constClass.getType(),
            "The java.lang.ClassLoader argument must be defined locally as null or "
                + constClass.getType()
                + ".class.getClassLoader()");
        continue;
      }
      InvokeVirtual classLoaderInvoke =
          serviceLoaderLoad.inValues().get(1).definition.asInvokeVirtual();
      boolean isGetClassLoaderOnConstClassOrNull =
          serviceLoaderLoad.inValues().get(1).getType().isNullType()
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
        report(
            code.origin,
            constClass.getType(),
            "The java.lang.ClassLoader argument must be defined locally as null or "
                + constClass.getType()
                + ".class.getClassLoader()");
        continue;
      }

      List<DexType> dexTypes = appServices.serviceImplementationsFor(constClass.getValue());
      List<DexClass> classes = new ArrayList<>(dexTypes.size());
      boolean seenNull = false;
      for (DexType serviceImpl : dexTypes) {
        DexClass serviceImplementation = appView.definitionFor(serviceImpl);
        if (serviceImplementation == null) {
          report(
              code.origin,
              constClass.getType(),
              "Unable to find definition for service implementation " + serviceImpl.getTypeName());
          seenNull = true;
        }
        classes.add(serviceImplementation);
      }

      if (seenNull) {
        continue;
      }

      // We can perform the rewrite of the ServiceLoader.load call.
      DexEncodedMethod synthesizedMethod =
          synthesizedServiceLoaders.computeIfAbsent(
              constClass.getValue(),
              service -> {
                DexEncodedMethod addedMethod =
                    createSynthesizedMethod(
                        service, classes, methodProcessor, methodProcessingContext);
                if (appView.options().isGeneratingClassFiles()) {
                  addedMethod.upgradeClassFileVersion(
                      code.context().getDefinition().getClassFileVersion());
                }
                return addedMethod;
              });

      new Rewriter(code, instructionIterator, serviceLoaderLoad)
          .perform(classLoaderInvoke, synthesizedMethod.getReference());
    }
    assert code.isConsistentSSA(appView);
  }

  public void onLastWaveDone(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    postMethodProcessorBuilder.addAll(synthesizedServiceLoadMethods, appView.graphLens());
  }

  private void report(Origin origin, DexType serviceLoaderType, String message) {
    if (reporter != null) {
      reporter.info(
          new ServiceLoaderRewriterDiagnostic(
              origin,
              "Could not inline ServiceLoader.load"
                  + (serviceLoaderType == null
                      ? ""
                      : (" of type " + serviceLoaderType.getTypeName()))
                  + ": "
                  + message));
    }
  }

  private DexEncodedMethod createSynthesizedMethod(
      DexType serviceType,
      List<DexClass> classes,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexProto proto = appView.dexItemFactory().createProto(appView.dexItemFactory().iteratorType);
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.SERVICE_LOADER,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(proto)
                        .setApiLevelForDefinition(appView.computedMinApiLevel())
                        .setApiLevelForCode(
                            apiLevelCompute.computeApiLevelForDefinition(
                                ListUtils.map(classes, clazz -> clazz.type)))
                        .setCode(
                            m ->
                                ServiceLoaderSourceCode.generate(
                                    serviceType, classes, appView.dexItemFactory())));
    synchronized (synthesizedServiceLoadMethods) {
      synthesizedServiceLoadMethods.add(method);
    }
    methodProcessor
        .getEventConsumer()
        .acceptServiceLoaderLoadUtilityMethod(method, methodProcessingContext.getMethodContext());
    return method.getDefinition();
  }

  /**
   * Rewriter assumes that the code is of the form:
   *
   * <pre>
   * ConstClass         v1 <- X
   * ConstClass         v2 <- X or NULL
   * Invoke-Virtual     v3 <- v2; method: java.lang.ClassLoader java.lang.Class.getClassLoader()
   * Invoke-Static      v4 <- v1, v3; method: java.util.ServiceLoader java.util.ServiceLoader
   *     .load(java.lang.Class, java.lang.ClassLoader)
   * Invoke-Virtual     v5 <- v4; method: java.util.Iterator java.util.ServiceLoader.iterator()
   * </pre>
   *
   * and rewrites it to:
   *
   * <pre>
   * Invoke-Static      v5 <- ; method: java.util.Iterator syn(X)()
   * </pre>
   *
   * where syn(X) is the synthesized method generated for the service class.
   *
   * <p>We rely on the DeadCodeRemover to remove the ConstClasses and any aliased values no longer
   * used.
   */
  private static class Rewriter {

    private final IRCode code;
    private final InvokeStatic serviceLoaderLoad;

    private final InstructionListIterator iterator;

    Rewriter(IRCode code, InstructionListIterator iterator, InvokeStatic serviceLoaderLoad) {
      this.iterator = iterator;
      this.code = code;
      this.serviceLoaderLoad = serviceLoaderLoad;
    }

    public void perform(InvokeVirtual classLoaderInvoke, DexMethod method) {
      // Remove the ClassLoader call since this can throw and will not be removed otherwise.
      if (classLoaderInvoke != null) {
        BooleanBox allClassLoaderUsersAreServiceLoaders =
            new BooleanBox(!classLoaderInvoke.outValue().hasPhiUsers());
        classLoaderInvoke
            .outValue()
            .uniqueUsers()
            .forEach(
                user -> {
                  assert !user.isAssume();
                  allClassLoaderUsersAreServiceLoaders.and(user == serviceLoaderLoad);
                });
        if (allClassLoaderUsersAreServiceLoaders.get()) {
          clearGetClassLoader(classLoaderInvoke);
          iterator.nextUntil(i -> i == serviceLoaderLoad);
        }
      }

      // Remove the ServiceLoader.load call.
      InvokeVirtual serviceLoaderIterator =
          serviceLoaderLoad.outValue().singleUniqueUser().asInvokeVirtual();
      iterator.replaceCurrentInstruction(code.createConstNull());

      // Find the iterator instruction and replace it.
      iterator.nextUntil(x -> x == serviceLoaderIterator);
      InvokeStatic synthesizedInvoke =
          new InvokeStatic(method, serviceLoaderIterator.outValue(), ImmutableList.of());
      iterator.replaceCurrentInstruction(synthesizedInvoke);
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
