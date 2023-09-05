// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.ThrowingCharIterator;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import java.io.UTFDataFormatException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AssertionsRewriter {

  private static class ConfigurationEntryWithDexString {

    private final AssertionsConfiguration entry;
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

    public boolean isCompileTimeEnabled() {
      return entry.isCompileTimeEnabled();
    }

    public boolean isCompileTimeDisabled() {
      return entry.isCompileTimeDisabled();
    }

    public boolean isPassthrough() {
      return entry.isPassthrough();
    }

    public boolean isAssertionHandler() {
      return entry.isAssertionHandler();
    }

    public MethodReference getAssertionHandler() {
      assert isAssertionHandler();
      return entry.getAssertionHandler();
    }
  }

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final ConfigurationEntryWithDexString defaultConfiguration;
  private final List<ConfigurationEntryWithDexString> configuration;
  private final ConfigurationEntryWithDexString kotlinTransformation;
  private final boolean enabled;

  public AssertionsRewriter(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.enabled = isEnabled(appView.options());
    if (!enabled) {
      defaultConfiguration = null;
      configuration = null;
      kotlinTransformation = null;
      return;
    }
    // Convert the assertion configuration to the representation used for this rewriter.
    this.defaultConfiguration =
        new ConfigurationEntryWithDexString(
            appView.options().assertionsConfiguration.defaultConfiguration, dexItemFactory);
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

  private ConfigurationEntryWithDexString getTransformationForMethod(DexEncodedMethod method) {
    return getTransformationForType(method.getHolderType());
  }

  private ConfigurationEntryWithDexString getTransformationForType(DexType type) {
    ConfigurationEntryWithDexString result = defaultConfiguration;
    for (ConfigurationEntryWithDexString entry : configuration) {
      switch (entry.entry.getScope()) {
        case ALL:
          result = entry;
          break;
        case PACKAGE:
          if (entry.value.size == 0) {
            if (!type.descriptor.contains(dexItemFactory.descriptorSeparator)) {
              result = entry;
            }
          } else if (type.descriptor.startsWith(entry.value)) {
            result = entry;
          }
          break;
        case CLASS:
          if (type.descriptor.equals(entry.value)) {
            result = entry;
          }
          if (isDescriptorForClassOrInnerClass(entry.value, type.descriptor)) {
            result = entry;
          }
          break;
        default:
          throw new Unreachable();
      }
    }
    assert result != null;
    return result;
  }

  @SuppressWarnings("ReferenceEquality")
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
   *
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
   * With the rewriting below and AssertionTransformation.DISABLE (and other rewritings) the
   * resulting code is:
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
   *
   * NOTE: that in Kotlin the assertion condition is always calculated. So it is still present in
   * the code and even for AssertionTransformation.DISABLE.
   */
  public void run(
      DexEncodedMethod method, IRCode code, DeadCodeRemover deadCodeRemover, Timing timing) {
    if (enabled) {
      timing.begin("Rewrite assertions");
      boolean needsDeadCodeRemoval = runInternal(method, code);
      code.removeRedundantBlocks();
      if (needsDeadCodeRemoval) {
        deadCodeRemover.run(code, timing);
      }
      assert code.isConsistentSSA(appView);
      timing.end();
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean runInternal(DexEncodedMethod method, IRCode code) {
    ConfigurationEntryWithDexString configuration = getTransformationForMethod(method);
    if (configuration.isPassthrough()) {
      return false;
    }
    DexEncodedMethod clinit;
    // If the <clinit> of this class did not have have code to turn on assertions don't try to
    // remove assertion code from the method (including <clinit> itself.
    if (method.isClassInitializer()) {
      clinit = method;
    } else {
      DexClass clazz = appView.definitionFor(method.getHolderType());
      if (clazz == null) {
        return false;
      }
      clinit = clazz.getClassInitializer();
    }
    // For the transformation to rewrite the throw with a callback collect information on the
    // blocks covered by the if (!$assertionsDisabled or ENABLED) condition together with weather
    // the assertion handling is on the true or false branch.
    Map<If, Boolean> assertionEntryIfs = new Reference2BooleanOpenHashMap<>();
    Map<Throw, BasicBlock> throwSuccessorAfterHandler = new IdentityHashMap<>();
    Set<BasicBlock> assertionBlocks = Sets.newIdentityHashSet();
    Map<If, Boolean> additionalAssertionsEnabledIfs = new Reference2BooleanOpenHashMap<>();
    if (configuration.isAssertionHandler()) {
      LazyBox<DominatorTree> dominatorTree = new LazyBox<>(() -> new DominatorTree(code));
      code.getBlocks()
          .forEach(
              basicBlock -> {
                if (assertionBlocks.contains(basicBlock)) {
                  return;
                }
                If theIf = isCheckAssertionsEnabledBlock(basicBlock);
                if (theIf != null) {
                  // All blocks dominated by the if is the assertion code. For Java it is on the
                  // false branch and for Kotlin on the true branch (for Java it is negated
                  // $assertionsDisabled field and for Kotlin it is the ENABLED field).
                  boolean conditionForAssertionBlock =
                      !isUsingJavaAssertionsDisabledField(
                          theIf.lhs().getDefinition().asStaticGet());
                  BasicBlock assertionBlockEntry =
                      theIf.targetFromBoolean(conditionForAssertionBlock);
                  List<BasicBlock> dominatedBlocks =
                      dominatorTree.computeIfAbsent().dominatedBlocks(assertionBlockEntry);
                  Throw throwInstruction =
                      dominatedBlocksHasSingleThrow(assertionBlockEntry, dominatedBlocks);
                  if (throwInstruction != null) {
                    assertionEntryIfs.put(theIf, conditionForAssertionBlock);
                    throwSuccessorAfterHandler.put(
                        throwInstruction, theIf.targetFromBoolean(!conditionForAssertionBlock));
                    // Collect any additional assertions enabled checks dominated by the current
                    // assertions entry check.
                    dominatedBlocks.forEach(
                        block -> {
                          If additionalAssertionsEnabledIf = isCheckAssertionsEnabledBlock(block);
                          if (additionalAssertionsEnabledIf != null) {
                            additionalAssertionsEnabledIfs.put(
                                additionalAssertionsEnabledIf,
                                !isUsingJavaAssertionsDisabledField(
                                    additionalAssertionsEnabledIf
                                        .lhs()
                                        .getDefinition()
                                        .asStaticGet()));
                          }
                        });
                    assertionBlocks.addAll(dominatedBlocks);
                  }
                }
              });
    }
    assert assertionEntryIfs.size() == throwSuccessorAfterHandler.size();
    // For javac generated code it is assumed that the code in <clinit> will tell if the code
    // in other methods of the class can have assertion checks.
    boolean isInitializerEnablingJavaVmAssertions =
        clinit != null && clinit.getOptimizationInfo().isInitializerEnablingJavaVmAssertions();
    // This code will process the assertion code in all methods including <clinit>.
    InstructionListIterator iterator = code.instructionListIterator();
    boolean needsDeadCodeRemoval = false;
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isInvokeMethod()) {
        InvokeMethod invoke = current.asInvokeMethod();
        if (invoke.getInvokedMethod() == dexItemFactory.classMethods.desiredAssertionStatus) {
          if (method.getHolderType() == dexItemFactory.kotlin.assertions.type) {
            rewriteKotlinAssertionEnable(code, configuration, iterator, invoke);
          } else {
            iterator.replaceCurrentInstruction(code.createIntConstant(0, current.getLocalInfo()));
          }
        }
      } else if (current.isStaticPut()) {
        StaticPut staticPut = current.asStaticPut();
        if (isInitializerEnablingJavaVmAssertions
            && isUsingJavaAssertionsDisabledField(staticPut)) {
          iterator.remove();
        }
      } else if (current.isStaticGet()) {
        StaticGet staticGet = current.asStaticGet();
        // Rewrite $assertionsDisabled getter (only if the initializer enabled assertions).
        if (isInitializerEnablingJavaVmAssertions
            && isUsingJavaAssertionsDisabledField(staticGet)) {
          // For assertion handler rewrite just leave the static get, as it will become dead code.
          if (!configuration.isAssertionHandler()) {
            iterator.replaceCurrentInstruction(
                code.createIntConstant(
                    configuration.isCompileTimeDisabled() ? 1 : 0, current.getLocalInfo()));
          }
        }
        // Rewrite kotlin._Assertions.ENABLED getter.
        if (staticGet.getField() == dexItemFactory.kotlin.assertions.enabledField) {
          // For assertion handler rewrite just leave the static get, as it will become dead code.
          if (!configuration.isAssertionHandler()) {
            iterator.replaceCurrentInstruction(
                code.createIntConstant(
                    kotlinTransformation.isCompileTimeDisabled() ? 0 : 1, current.getLocalInfo()));
          }
        }
      }

      // Rewriting of if and throw to replace throw with invoke of the assertion handler.
      if (configuration.isAssertionHandler()) {
        if (current.isIf()) {
          If ifInstruction = current.asIf();
          if (assertionEntryIfs.containsKey(ifInstruction)) {
            forceAssertionsEnabled(ifInstruction, assertionEntryIfs, iterator);
            needsDeadCodeRemoval = true;
          }
          if (additionalAssertionsEnabledIfs.containsKey(ifInstruction)) {
            forceAssertionsEnabled(ifInstruction, additionalAssertionsEnabledIfs, iterator);
            needsDeadCodeRemoval = true;
          }
        } else if (current.isThrow()) {
          Throw throwInstruction = current.asThrow();
          if (throwSuccessorAfterHandler.containsKey(throwInstruction)) {
            BasicBlock throwingBlock = throwInstruction.getBlock();
            iterator.replaceCurrentInstruction(
                new InvokeStatic(
                    dexItemFactory.createMethod(configuration.getAssertionHandler()),
                    null,
                    ImmutableList.of(throwInstruction.exception())));
            Goto gotoBlockAfterAssertion = new Goto(throwingBlock);
            gotoBlockAfterAssertion.setPosition(throwInstruction.getPosition());
            throwingBlock.link(throwSuccessorAfterHandler.get(throwInstruction));
            iterator.add(gotoBlockAfterAssertion);
          }
        }
      }
    }
    return needsDeadCodeRemoval;
  }

  @SuppressWarnings("ReferenceEquality")
  private void rewriteKotlinAssertionEnable(
      IRCode code,
      ConfigurationEntryWithDexString configuration,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    if (iterator.hasNext() && configuration.isCompileTimeDisabled()) {
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
          code.createIntConstant(configuration.isCompileTimeEnabled() ? 1 : 0));
    }
  }

  private boolean isUsingAssertionsControlField(FieldInstruction instruction) {
    return isUsingJavaAssertionsDisabledField(instruction)
        || isUsingKotlinAssertionsEnabledField(instruction);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isUsingJavaAssertionsDisabledField(FieldInstruction instruction) {
    // This does not check the holder, as for inner classe the field is read from the outer class
    // and not the class itself.
    return instruction.getField().getName() == dexItemFactory.assertionsDisabled
        && instruction.getField().getType() == dexItemFactory.booleanType;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isUsingKotlinAssertionsEnabledField(FieldInstruction instruction) {
    return instruction.getField() == dexItemFactory.kotlin.assertions.enabledField;
  }

  private If isCheckAssertionsEnabledBlock(BasicBlock basicBlock) {
    if (!basicBlock.exit().isIf()) {
      return null;
    }
    If theIf = basicBlock.exit().asIf();
    if (!theIf.isZeroTest()
        || !theIf.lhs().isDefinedByInstructionSatisfying(Instruction::isStaticGet)) {
      return null;
    }
    StaticGet staticGet = theIf.lhs().getDefinition().asStaticGet();
    return isUsingAssertionsControlField(staticGet)
            && staticGet.value().hasSingleUniqueUser()
            && !staticGet.value().hasPhiUsers()
        ? theIf
        : null;
  }

  private Throw dominatedBlocksHasSingleThrow(BasicBlock block, List<BasicBlock> dominatedBlocks) {
    Throw theThrow = null;
    for (BasicBlock current : dominatedBlocks) {
      if (current.exit().isReturn()) {
        return null;
      }
      if (current.exit().isThrow()) {
        if (theThrow != null) {
          return null;
        }
        theThrow = current.exit().asThrow();
      }
    }
    return theThrow;
  }

  private void forceAssertionsEnabled(
      If ifInstruction, Map<If, Boolean> targetMap, InstructionListIterator iterator) {
    ifInstruction
        .targetFromBoolean(!targetMap.get(ifInstruction))
        .unlinkSinglePredecessorSiblingsAllowed();
    ifInstruction.lhs().removeUser(ifInstruction);
    iterator.replaceCurrentInstruction(new Goto());
  }
}
