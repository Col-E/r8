// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.KeyedDexItem;
import com.android.tools.r8.graph.PresortedComparable;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class TreePruner {

  private final DexApplication application;
  private final AppInfoWithLiveness appInfo;
  private final InternalOptions options;
  private final UsagePrinter usagePrinter;
  private final Set<DexType> prunedTypes = Sets.newIdentityHashSet();

  public TreePruner(
      DexApplication application, AppInfoWithLiveness appInfo, InternalOptions options) {
    this.application = application;
    this.appInfo = appInfo;
    this.options = options;
    this.usagePrinter =
        options.getProguardConfiguration() != null
            && options.getProguardConfiguration().isPrintUsage()
        ? new UsagePrinter() : UsagePrinter.DONT_PRINT;
  }

  public DexApplication run() {
    application.timing.begin("Pruning application...");
    if (options.debugKeepRules && options.enableMinification) {
      options.reporter.info(
          new StringDiagnostic(
              "Debugging keep rules on a minified build might yield broken builds, as "
                  + "minification also depends on the used keep rules. We recommend using "
                  + "--skip-minification."));
    }
    DexApplication result;
    try {
      result = removeUnused(application).appendDeadCode(usagePrinter.toStringContent()).build();
    } finally {
      application.timing.end();
    }
    return result;
  }

  private DexApplication.Builder<?> removeUnused(DexApplication application) {
    return application.builder()
        .replaceProgramClasses(getNewProgramClasses(application.classes()));
  }

  private List<DexProgramClass> getNewProgramClasses(List<DexProgramClass> classes) {
    List<DexProgramClass> newClasses = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      if (!appInfo.liveTypes.contains(clazz.type)) {
        // The class is completely unused and we can remove it.
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing class: " + clazz);
        }
        prunedTypes.add(clazz.type);
        usagePrinter.printUnusedClass(clazz);
      } else {
        newClasses.add(clazz);
        if (!appInfo.instantiatedTypes.contains(clazz.type)
            && !options.forceProguardCompatibility
            && (!options.debugKeepRules || !clazz.hasDefaultInitializer())) {
          // The class is only needed as a type but never instantiated. Make it abstract to reflect
          // this.
          if (clazz.accessFlags.isFinal()) {
            // We cannot mark this class abstract, as it is final (not supported on Android).
            // However, this might extend an abstract class and we might have removed the
            // corresponding methods in this class. This might happen if we only keep this
            // class around for its constants.
            // For now, we remove the final flag to still be able to mark it abstract.
            clazz.accessFlags.unsetFinal();
          }
          clazz.accessFlags.setAbstract();
        }
        // The class is used and must be kept. Remove the unused fields and methods from
        // the class.
        usagePrinter.visiting(clazz);
        clazz.setDirectMethods(reachableMethods(clazz.directMethods(), clazz));
        clazz.setVirtualMethods(reachableMethods(clazz.virtualMethods(), clazz));
        clazz.setInstanceFields(reachableFields(clazz.instanceFields()));
        clazz.setStaticFields(reachableFields(clazz.staticFields()));
        clazz.removeInnerClasses(this::isAttributeReferencingPrunedType);
        clazz.removeEnclosingMethod(this::isAttributeReferencingPrunedItem);
        usagePrinter.visited();
      }
    }
    return newClasses;
  }

  private boolean isAttributeReferencingPrunedItem(EnclosingMethodAttribute attr) {
    return
        (attr.getEnclosingClass() != null
            && !appInfo.liveTypes.contains(attr.getEnclosingClass()))
        || (attr.getEnclosingMethod() != null
            && !appInfo.liveMethods.contains(attr.getEnclosingMethod()));
  }

  private boolean isAttributeReferencingPrunedType(InnerClassAttribute attr) {
    if (!appInfo.liveTypes.contains(attr.getInner())) {
      return true;
    }
    DexType context = attr.getOuter();
    if (context == null) {
      DexClass inner = appInfo.definitionFor(attr.getInner());
      if (inner != null && inner.getEnclosingMethod() != null) {
        EnclosingMethodAttribute enclosingMethodAttribute = inner.getEnclosingMethod();
        if (enclosingMethodAttribute.getEnclosingClass() != null) {
          context = enclosingMethodAttribute.getEnclosingClass();
        } else {
          DexMethod enclosingMethod = enclosingMethodAttribute.getEnclosingMethod();
          if (!appInfo.liveMethods.contains(enclosingMethod)) {
            // EnclosingMethodAttribute will be pruned as it references the pruned method.
            // Hence, removal of the current InnerClassAttribute too.
            return true;
          }
          context = enclosingMethod.getHolder();
        }
      }
    }
    return context == null || !appInfo.liveTypes.contains(context);
  }

  private <S extends PresortedComparable<S>, T extends KeyedDexItem<S>> int firstUnreachableIndex(
      T[] items, Predicate<S> live) {
    for (int i = 0; i < items.length; i++) {
      if (!live.test(items[i].getKey())) {
        return i;
      }
    }
    return -1;
  }

  private boolean isDefaultConstructor(DexEncodedMethod method) {
    return method.isInstanceInitializer()
        && method.method.proto.parameters.isEmpty();
  }

  private DexEncodedMethod[] reachableMethods(DexEncodedMethod[] methods, DexClass clazz) {
    int firstUnreachable = firstUnreachableIndex(methods, appInfo.liveMethods::contains);
    // Return the original array if all methods are used.
    if (firstUnreachable == -1) {
      return methods;
    }
    ArrayList<DexEncodedMethod> reachableMethods = new ArrayList<>(methods.length);
    for (int i = 0; i < firstUnreachable; i++) {
      reachableMethods.add(methods[i]);
    }
    for (int i = firstUnreachable; i < methods.length; i++) {
      DexEncodedMethod method = methods[i];
      if (appInfo.liveMethods.contains(method.getKey())) {
        reachableMethods.add(method);
      } else if (options.debugKeepRules && isDefaultConstructor(method)) {
        // Keep the method but rewrite its body, if it has one.
        reachableMethods.add(
            method.shouldNotHaveCode() && !method.hasCode()
                ? method
                : method.toMethodThatLogsError(application.dexItemFactory));
      } else if (appInfo.targetedMethods.contains(method.getKey())) {
        // If the method is already abstract, and doesn't have code, let it be.
        if (method.shouldNotHaveCode() && !method.hasCode()) {
          reachableMethods.add(method);
          continue;
        }
        if (Log.ENABLED) {
          Log.debug(getClass(), "Making method %s abstract.", method.method);
        }
        // Final classes cannot be abstract, so we have to keep the method in that case.
        // Also some other kinds of methods cannot be abstract, so keep them around.
        boolean allowAbstract = clazz.accessFlags.isAbstract()
            && !method.accessFlags.isFinal()
            && !method.accessFlags.isNative()
            && !method.accessFlags.isStrict()
            && !method.accessFlags.isSynchronized()
            && !method.accessFlags.isPrivate();
        // By construction, static methods cannot be reachable but non-live. For private methods
        // this can only happen as the result of an invalid invoke. They will not actually be
        // called at runtime but we have to keep them as non-abstract (see above) to produce the
        // same failure mode.
        reachableMethods.add(
            allowAbstract
                ? method.toAbstractMethod()
                : (options.isGeneratingClassFiles()
                    ? method.toEmptyThrowingMethodCf()
                    : method.toEmptyThrowingMethodDex()));
      } else {
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing method %s.", method.method);
        }
        usagePrinter.printUnusedMethod(method);
      }
    }
    return reachableMethods.toArray(new DexEncodedMethod[reachableMethods.size()]);
  }

  private DexEncodedField[] reachableFields(DexEncodedField[] fields) {
    Predicate<DexField> isReachableOrReferencedField =
        field ->
            appInfo.liveFields.contains(field)
                || appInfo.fieldsRead.contains(field)
                || appInfo.fieldsWritten.contains(field);
    int firstUnreachable = firstUnreachableIndex(fields, isReachableOrReferencedField);
    // Return the original array if all fields are used.
    if (firstUnreachable == -1) {
      return fields;
    }
    if (Log.ENABLED) {
      Log.debug(getClass(), "Removing field: " + fields[firstUnreachable]);
    }
    usagePrinter.printUnusedField(fields[firstUnreachable]);
    ArrayList<DexEncodedField> reachableOrReferencedFields = new ArrayList<>(fields.length);
    for (int i = 0; i < firstUnreachable; i++) {
      reachableOrReferencedFields.add(fields[i]);
    }
    for (int i = firstUnreachable + 1; i < fields.length; i++) {
      DexEncodedField field = fields[i];
      if (isReachableOrReferencedField.test(field.field)) {
        reachableOrReferencedFields.add(field);
      } else {
        if (Log.ENABLED) {
          Log.debug(getClass(), "Removing field: " + field);
        }
        usagePrinter.printUnusedField(field);
      }
    }
    return reachableOrReferencedFields.isEmpty()
        ? DexEncodedField.EMPTY_ARRAY
        : reachableOrReferencedFields.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  public Collection<DexType> getRemovedClasses() {
    return Collections.unmodifiableCollection(prunedTypes);
  }
}
