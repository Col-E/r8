// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.AssertionsConfiguration.ConfigurationEntry;
import com.android.tools.r8.AssertionsConfiguration.ConfigurationType;
import com.android.tools.r8.AssertionsConfiguration.InternalAssertionConfiguration;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThrowingCharIterator;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.stream.Collectors;

public class AssertionsRewriter {

  private static class ConfigurationEntryWithDexString {

    private ConfigurationEntry entry;
    private final DexString value;

    private ConfigurationEntryWithDexString(
        ConfigurationEntry entry, DexItemFactory dexItemFactory) {
      this.entry = entry;
      switch (entry.getType()) {
        case PACKAGE:
          if (entry.getValue().length() == 0) {
            value = dexItemFactory.createString("");
          } else {
            value =
                dexItemFactory.createString(
                    "L"
                        + entry
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
                      + entry
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
  private final List<ConfigurationEntryWithDexString> configuration;
  private final boolean enabled;

  public AssertionsRewriter(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    if (appView.options().assertionsConfiguration == null) {
      this.configuration = null;
      this.enabled = false;
    } else {
      List<ConfigurationEntry> configuration =
          InternalAssertionConfiguration
              .getConfiguration(appView.options().assertionsConfiguration);
      this.configuration =
          configuration.stream()
              .map(entry -> new ConfigurationEntryWithDexString(entry, appView.dexItemFactory()))
              .collect(Collectors.toList());
      this.enabled = !isPassthroughAll(appView.options().assertionsConfiguration);
    }
  }

  public static boolean isPassthroughAll(AssertionsConfiguration assertionsConfiguration) {
    List<ConfigurationEntry> configuration =
        InternalAssertionConfiguration.getConfiguration(assertionsConfiguration);
    return configuration.size() == 1
        && configuration.get(0).getTransformation() == AssertionTransformation.PASSTHROUGH
        && configuration.get(0).getType() == ConfigurationType.ALL;
  }

  private AssertionTransformation getTransformationForMethod(DexEncodedMethod method) {
    AssertionTransformation transformation = null;
    for (ConfigurationEntryWithDexString entry : configuration) {
      switch (entry.entry.getType()) {
        case ALL:
          transformation = entry.entry.getTransformation();
          break;
        case PACKAGE:
          if (entry.value.size == 0) {
            if (!method.method.holder.descriptor.contains(dexItemFactory.descriptorSeparator)) {
              transformation = entry.entry.getTransformation();
            }
          } else if (method.method.holder.descriptor.startsWith(entry.value)) {
            transformation = entry.entry.getTransformation();
          }
          break;
        case CLASS:
          if (method.method.holder.descriptor.equals(entry.value)) {
            transformation = entry.entry.getTransformation();
          }
          if (isDescriptorForClassOrInnerClass(entry.value, method.method.holder.descriptor)) {
            transformation = entry.entry.getTransformation();
          }
          break;
        default:
          throw new Unreachable();
      }
    }
    assert transformation != null; // Default transformation are always added.
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
   *       if (xxx) {
   *         throw new AssertionError(...);
   *       }
   *     }
   *   }
   * }
   * </pre>
   *
   * With the rewriting below (and other rewritings) the resulting code is:
   *
   * <pre>
   * class A {
   *   void m() {
   *   }
   * }
   * </pre>
   */
  public void run(DexEncodedMethod method, IRCode code) {
    if (!enabled) {
      return;
    }
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
      DexClass clazz = appView.definitionFor(method.method.holder);
      if (clazz == null) {
        return;
      }
      clinit = clazz.getClassInitializer();
    }
    if (clinit == null || !clinit.getOptimizationInfo().isInitializerEnablingJavaAssertions()) {
      return;
    }

    // This code will process the assertion code in all methods including <clinit>.
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        if (invoke.getInvokedMethod() == dexItemFactory.classMethods.desiredAssertionStatus) {
          iterator.replaceCurrentInstruction(code.createIntConstant(0));
        }
      } else if (current.isStaticPut()) {
        StaticPut staticPut = current.asStaticPut();
        if (staticPut.getField().name == dexItemFactory.assertionsDisabled) {
          iterator.remove();
        }
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        if (staticGet.getField().name == dexItemFactory.assertionsDisabled) {
          iterator.replaceCurrentInstruction(
              code.createIntConstant(transformation == AssertionTransformation.DISABLE ? 1 : 0));
        }
      }
    }
  }
}
