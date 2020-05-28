// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingCharIterator;
import com.android.tools.r8.utils.Timing;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.stream.Collectors;

public class AssertionsRewriter {

  private static class ConfigurationEntryWithDexString {

    private AssertionsConfiguration entry;
    private final DexString value;

    private ConfigurationEntryWithDexString(
        AssertionsConfiguration configuration, DexItemFactory dexItemFactory) {
      this.entry = configuration;
      switch (configuration.getScope()) {
        case PACKAGE:
          if (configuration.getValue().length() == 0) {
            value = dexItemFactory.createString("");
          } else {
            value =
                dexItemFactory.createString(
                    "L"
                        + configuration
                            .getValue()
                            .replace(
                                DescriptorUtils.JAVA_PACKAGE_SEPARATOR,
                                DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR)
                        + "/");
          }
          break;
        case CLASS:
          value =
              dexItemFactory.createString(
                  "L"
                      + configuration
                          .getValue()
                          .replace(
                              DescriptorUtils.JAVA_PACKAGE_SEPARATOR,
                              DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR)
                      + ";");
          break;
        case ALL:
          value = null;
          break;
        default:
          throw new Unreachable();
      }
    }
  }

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final AssertionTransformation defaultTransformation;
  private final List<ConfigurationEntryWithDexString> configuration;
  private final AssertionsConfiguration.AssertionTransformation kotlinTransformation;
  private final boolean enabled;

  public AssertionsRewriter(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.enabled = isEnabled(appView.options());
    if (!enabled) {
      defaultTransformation = null;
      configuration = null;
      kotlinTransformation = null;
      return;
    }
    // Convert the assertion transformation to the representation used for this rewriter.
    this.defaultTransformation = appView.options().assertionsConfiguration.defautlTransformation;
    this.configuration =
        appView.options().assertionsConfiguration.assertionsConfigurations.stream()
            .map(entry -> new ConfigurationEntryWithDexString(entry, appView.dexItemFactory()))
            .collect(Collectors.toList());
    kotlinTransformation =
        getTransformationForType(appView.dexItemFactory().kotlin.assertions.type);
  }

  // Static method used by other analyses to see if additional analysis is required to support
  // this rewriting.
  public static boolean isEnabled(InternalOptions options) {
    AssertionConfigurationWithDefault configuration = options.assertionsConfiguration;
    return configuration != null && !configuration.isPassthroughAll();
  }

  private AssertionTransformation getTransformationForMethod(DexEncodedMethod method) {
    return getTransformationForType(method.holder());
  }

  private AssertionTransformation getTransformationForType(DexType type) {
    AssertionTransformation transformation = defaultTransformation;
    for (ConfigurationEntryWithDexString entry : configuration) {
      switch (entry.entry.getScope()) {
        case ALL:
          transformation = entry.entry.getTransformation();
          break;
        case PACKAGE:
          if (entry.value.size == 0) {
            if (!type.descriptor.contains(dexItemFactory.descriptorSeparator)) {
              transformation = entry.entry.getTransformation();
            }
          } else if (type.descriptor.startsWith(entry.value)) {
            transformation = entry.entry.getTransformation();
          }
          break;
        case CLASS:
          if (type.descriptor.equals(entry.value)) {
            transformation = entry.entry.getTransformation();
          }
          if (isDescriptorForClassOrInnerClass(entry.value, type.descriptor)) {
            transformation = entry.entry.getTransformation();
          }
          break;
        default:
          throw new Unreachable();
      }
    }
    assert transformation != null;
    return transformation;
  }

  private boolean isDescriptorForClassOrInnerClass(
      DexString classDescriptor, DexString classOrInnerClassDescriptor) {
    // Same string same class.
    if (classOrInnerClassDescriptor == classDescriptor) {
      return true;
    }

    // Check for inner class name by checking if the prefix is the class descriptor,
    // where ';' is replaced whit '$' and no '/' after that.
    if (classOrInnerClassDescriptor.size < classDescriptor.size) {
      return false;
    }
    ThrowingCharIterator<UTFDataFormatException> i1 = classDescriptor.iterator();
    ThrowingCharIterator<UTFDataFormatException> i2 = classOrInnerClassDescriptor.iterator();
    try {
      while (i1.hasNext()) {
        char c1 = i1.nextChar();
        char c2 = i2.nextChar();
        // The Java VM behaviour is including all inner classes as well when a class is specified.
        if (c1 == ';' && c2 == DescriptorUtils.INNER_CLASS_SEPARATOR) {
          // If there is a '/' after the '$' this is not an inner class after all.
          while (i2.hasNext()) {
            if (i2.nextChar() == DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR) {
              return false;
            }
          }
          return true;
        }
        if (c1 != c2) {
          return false;
        }
      }
      assert i2.hasNext();
      return false;
    } catch (UTFDataFormatException e) {
      return false;
    }
  }

  /**
   * For supporting assert javac adds the static field $assertionsDisabled to all classes which have
   * methods with assertions. This is used to support the Java VM -ea flag.
   *
   * <p>The class:
   *
   * <pre>
   * class A {
   *   void m() {
   *     assert xxx;
   *   }
   * }
   * </pre>
   *
   * Is compiled into:
   *
   * <pre>
   * class A {
   *   static boolean $assertionsDisabled;
   *   static {
   *     $assertionsDisabled = A.class.desiredAssertionStatus();
   *   }
   *
   *   // method with "assert xxx";
   *   void m() {
   *     if (!$assertionsDisabled) {
   *       if (!xxx) {
   *         throw new AssertionError(...);
   *       }
   *     }
   *   }
   * }
   * </pre>
   *
   * With the rewriting below and AssertionTransformation.DISABLE (and other rewritings) the
   * resulting code is:
   *
   * <pre>
   * class A {
   *   void m() {
   *   }
   * }
   * </pre>
   *
   * With AssertionTransformation.ENABLE (and other rewritings) the resulting code is:
   *
   * <pre>
   * class A {
   *   static boolean $assertionsDisabled;
   *   void m() {
   *     if (!xxx) {
   *       throw new AssertionError(...);
   *     }
   *   }
   * }
   * </pre>
   * For Kotlin the Class instance method desiredAssertionStatus() is only called for the class
   * kotlin._Assertions, where kotlin._Assertions.class.desiredAssertionStatus() is read into the
   * static field kotlin._Assertions.ENABLED.
   *
   * <pre>
   * class _Assertions {
   *   public static boolean ENABLED = _Assertions.class.desiredAssertionStatus();
   * }
   * </pre>
   *
   * <p>(actual code
   * https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/jvm/src/kotlin/util/AssertionsJVM.kt)
   *
   * <p>The class:
   *
   * <pre>
   * class A {
   *   void m() {
   *     assert(xxx)
   *   }
   * }
   * </pre>
   *
   * Is compiled into:
   *
   * <pre>
   * class A {
   *   void m() {
   *     if (!xxx) {
   *       if (kotlin._Assertions.ENABLED) {
   *         throw new AssertionError("Assertion failed")
   *       }
   *     }
   *   }
   * }
   * </pre>
   *
   * With the rewriting below and AssertionTransformation.DISABLE (and other rewritings) the resulting code is:
   *
   * <pre>
   * class A {
   *   void m() {
   *     if (!xxx) {}
   *   }
   * }
   * </pre>
   *
   * With AssertionTransformation.ENABLE (and other rewritings) the resulting code is:
   *
   * <pre>
   * class A {
   *   void m() {
   *     if (!xxx) {
   *       throw new AssertionError("Assertion failed")
   *     }
   *   }
   * }
   * </pre>

   * NOTE: that in Kotlin the assertion condition is always calculated. So it is still present in
   * the code and even for AssertionTransformation.DISABLE.
   */
  public void run(DexEncodedMethod method, IRCode code, Timing timing) {
    if (enabled) {
      timing.begin("Rewrite assertions");
      runInternal(method, code);
      timing.end();
    }
  }

  private void runInternal(DexEncodedMethod method, IRCode code) {
    AssertionTransformation transformation = getTransformationForMethod(method);
    if (transformation == AssertionTransformation.PASSTHROUGH) {
      return;
    }
    DexEncodedMethod clinit;
    // If the <clinit> of this class did not have have code to turn on assertions don't try to
    // remove assertion code from the method (including <clinit> itself.
    if (method.isClassInitializer()) {
      clinit = method;
    } else {
      DexClass clazz = appView.definitionFor(method.holder());
      if (clazz == null) {
        return;
      }
      clinit = clazz.getClassInitializer();
    }
    // For javac generated code it is assumed that the code in <clinit> will tell if the code
    // in other methods of the class can have assertion checks.
    boolean isInitializerEnablingJavaVmAssertions =
        clinit != null && clinit.getOptimizationInfo().isInitializerEnablingJavaVmAssertions();
    // This code will process the assertion code in all methods including <clinit>.
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        if (invoke.getInvokedMethod() == dexItemFactory.classMethods.desiredAssertionStatus) {
          if (method.holder() == dexItemFactory.kotlin.assertions.type) {
            rewriteKotlinAssertionEnable(code, transformation, iterator, invoke);
          } else {
            iterator.replaceCurrentInstruction(code.createIntConstant(0, current.getLocalInfo()));
          }
        }
      } else if (current.isStaticPut()) {
        StaticPut staticPut = current.asStaticPut();
        if (isInitializerEnablingJavaVmAssertions
            && staticPut.getField().name == dexItemFactory.assertionsDisabled) {
          iterator.remove();
        }
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        // Rewrite $assertionsDisabled getter (only if the initializer enabled assertions).
        if (isInitializerEnablingJavaVmAssertions
            && staticGet.getField().name == dexItemFactory.assertionsDisabled) {
          iterator.replaceCurrentInstruction(
              code.createIntConstant(
                  transformation == AssertionTransformation.DISABLE ? 1 : 0,
                  current.getLocalInfo()));
        }
        // Rewrite kotlin._Assertions.ENABLED getter.
        if (staticGet.getField() == dexItemFactory.kotlin.assertions.enabledField) {
          iterator.replaceCurrentInstruction(
              code.createIntConstant(
                  kotlinTransformation == AssertionTransformation.DISABLE ? 0 : 1,
                  current.getLocalInfo()));
        }
      }
    }
  }

  private void rewriteKotlinAssertionEnable(
      IRCode code,
      AssertionTransformation transformation,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    if (iterator.hasNext() && transformation == AssertionTransformation.DISABLE) {
      // Check if the invocation of Class.desiredAssertionStatus() is followed by a static
      // put to kotlin._Assertions.ENABLED, and if so remove both instructions.
      // See comment in ClassInitializerAssertionEnablingAnalysis for the expected instruction
      // sequence.
      Instruction nextInstruction = iterator.next();
      if (nextInstruction.isStaticPut()
          && nextInstruction.asStaticPut().getField().holder
              == dexItemFactory.kotlin.assertions.type
          && nextInstruction.asStaticPut().getField().name == dexItemFactory.enabledFieldName
          && invoke.outValue().numberOfUsers() == 1
          && invoke.outValue().numberOfPhiUsers() == 0
          && invoke.outValue().singleUniqueUser() == nextInstruction) {
        iterator.removeOrReplaceByDebugLocalRead();
        Instruction prevInstruction = iterator.previous();
        assert prevInstruction == invoke;
        iterator.removeOrReplaceByDebugLocalRead();
      } else {
        Instruction instruction = iterator.previous();
        assert instruction == nextInstruction;
        instruction = iterator.previous();
        assert instruction == invoke;
        instruction = iterator.next();
        assert instruction == invoke;
        iterator.replaceCurrentInstruction(code.createIntConstant(0));
      }
    } else {
      iterator.replaceCurrentInstruction(
          code.createIntConstant(transformation == AssertionTransformation.ENABLE ? 1 : 0));
    }
  }
}
