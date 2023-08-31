// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_OF;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.NumberFromIntervalValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.LongInterval;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Value implements Comparable<Value> {

  public void constrainType(
      ValueTypeConstraint constraint, DexMethod method, Origin origin, Reporter reporter) {
    TypeElement constrainedType = constrainedType(constraint);
    if (constrainedType == null) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Cannot constrain type: "
                  + type
                  + " for value: "
                  + this
                  + " by constraint: "
                  + constraint,
              origin,
              new MethodPosition(method.asMethodReference())));
    } else if (constrainedType != type) {
      setType(constrainedType);
    }
  }

  public TypeElement constrainedType(ValueTypeConstraint constraint) {
    if (constraint == ValueTypeConstraint.INT_OR_FLOAT_OR_OBJECT && !type.isWidePrimitive()) {
      return type;
    }
    switch (constraint) {
      case OBJECT:
        if (type.isTop()) {
          if (definition != null && definition.isConstNumber()) {
            assert definition.asConstNumber().isZero();
            return TypeElement.getNull();
          } else {
            return TypeElement.getBottom();
          }
        }
        if (type.isReferenceType()) {
          return type;
        }
        if (type.isBottom()) {
          // Only a few instructions may propagate a bottom input to a bottom output.
          assert isPhi()
              || definition.isDebugLocalWrite()
              || (definition.isArrayGet()
                  && definition.asArrayGet().getMemberType() == MemberType.OBJECT);
          return type;
        }
        break;
      case INT:
        if (type.isTop() || (type.isSinglePrimitive() && !type.isFloat())) {
          return TypeElement.getInt();
        }
        break;
      case FLOAT:
        if (type.isTop() || (type.isSinglePrimitive() && !type.isInt())) {
          return TypeElement.getFloat();
        }
        break;
      case INT_OR_FLOAT:
        if (type.isTop()) {
          return TypeElement.getSingle();
        }
        if (type.isSinglePrimitive()) {
          return type;
        }
        break;
      case LONG:
        if (type.isWidePrimitive() && !type.isDouble()) {
          return TypeElement.getLong();
        }
        break;
      case DOUBLE:
        if (type.isWidePrimitive() && !type.isLong()) {
          return TypeElement.getDouble();
        }
        break;
      case LONG_OR_DOUBLE:
        if (type.isWidePrimitive()) {
          return type;
        }
        break;
      default:
        throw new Unreachable("Unexpected constraint: " + constraint);
    }
    return null;
  }

  public boolean verifyCompatible(ValueType valueType) {
    return verifyCompatible(ValueTypeConstraint.fromValueType(valueType));
  }

  public boolean verifyCompatible(ValueTypeConstraint constraint) {
    assert constrainedType(constraint) != null;
    return true;
  }

  public void markNonDebugLocalRead() {
    assert !isPhi();
  }

  // Lazily allocated internal data for the debug information of locals.
  // This is wrapped in a class to avoid multiple pointers in the value structure.
  private static class DebugData {

    final DebugLocalInfo local;
    Set<Instruction> users = Sets.newIdentityHashSet();

    DebugData(DebugLocalInfo local) {
      this.local = local;
    }
  }

  public static final int UNDEFINED_NUMBER = -1;

  public static final Value UNDEFINED = new Value(UNDEFINED_NUMBER, TypeElement.getBottom(), null);

  protected final int number;
  public Instruction definition = null;

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private LinkedList<Instruction> users = new LinkedList<>();

  private Set<Instruction> uniqueUsers = null;

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private LinkedList<Phi> phiUsers = new LinkedList<>();

  private Set<Phi> uniquePhiUsers = null;
  private Value nextConsecutive = null;
  private Value previousConsecutive = null;
  private LiveIntervals liveIntervals;
  private int needsRegister = -1;
  private boolean isThis = false;
  private LongInterval valueRange;
  private DebugData debugData;
  protected TypeElement type;

  public Value(int number, TypeElement type, DebugLocalInfo local) {
    assert type != null;
    this.number = number;
    this.debugData = local == null ? null : new DebugData(local);
    this.type = type;
  }

  public boolean isFixedRegisterValue() {
    return false;
  }

  public FixedRegisterValue asFixedRegisterValue() {
    return null;
  }

  public Instruction getDefinition() {
    assert !isPhi();
    return definition;
  }

  public boolean hasAliasedValue() {
    return getAliasedValue() != this;
  }

  /**
   * If this value is defined by an instruction that defines an alias of another value, such as the
   * {@link Assume} instruction, then the incoming value to the {@link Assume} instruction is
   * returned (if the incoming value is not itself defined by an instruction that introduces an
   * alias).
   *
   * <p>If a phi value is found, then that phi value is returned.
   *
   * <p>This method is useful to find the "true" definition of a value inside the current method.
   */
  public Value getAliasedValue() {
    return getAliasedValue(
        DefaultAliasedValueConfiguration.getInstance(), Predicates.alwaysFalse());
  }

  public Value getAliasedValue(AliasedValueConfiguration configuration) {
    return getAliasedValue(configuration, Predicates.alwaysFalse());
  }

  public Value getAliasedValue(
      AliasedValueConfiguration configuration, Predicate<Value> stoppingCriterion) {
    assert stoppingCriterion != null;
    Set<Value> visited = Sets.newIdentityHashSet();
    Value lastAliasedValue;
    Value aliasedValue = this;
    do {
      lastAliasedValue = aliasedValue;
      if (aliasedValue.isPhi()) {
        return aliasedValue;
      }
      if (stoppingCriterion.test(aliasedValue)) {
        return aliasedValue;
      }
      Instruction definitionOfAliasedValue = aliasedValue.definition;
      if (configuration.isIntroducingAnAlias(definitionOfAliasedValue)) {
        aliasedValue = configuration.getAliasForOutValue(definitionOfAliasedValue);

        // There shouldn't be a cycle.
        assert visited.add(aliasedValue);
      }
    } while (aliasedValue != lastAliasedValue);
    assert aliasedValue.isPhi() || !aliasedValue.definition.isAssume();
    return aliasedValue;
  }

  public Value getSpecificAliasedValue(Predicate<Value> stoppingCriterion) {
    Value aliasedValue =
        getAliasedValue(DefaultAliasedValueConfiguration.getInstance(), stoppingCriterion);
    return stoppingCriterion.test(aliasedValue) ? aliasedValue : null;
  }

  public int getNumber() {
    return number;
  }

  public int requiredRegisters() {
    return type.requiredRegisters();
  }

  public DebugLocalInfo getLocalInfo() {
    return debugData == null ? null : debugData.local;
  }

  public boolean hasLocalInfo() {
    return getLocalInfo() != null;
  }

  public void setLocalInfo(DebugLocalInfo local) {
    assert local != null;
    assert debugData == null;
    debugData = new DebugData(local);
  }

  public Set<Instruction> getDebugLocalEnds() {
    return debugData == null
        ? Collections.emptySet()
        : Collections.unmodifiableSet(debugData.users);
  }

  public void addDebugLocalEnd(Instruction end) {
    assert end != null;
    debugData.users.add(end);
    end.addDebugValue(this);
  }

  public void linkTo(Value other) {
    assert nextConsecutive == null || nextConsecutive == other;
    assert other.previousConsecutive == null || other.previousConsecutive == this;
    other.previousConsecutive = this;
    nextConsecutive = other;
  }

  public void replaceLink(Value newArgument) {
    assert isLinked();
    if (previousConsecutive != null) {
      previousConsecutive.nextConsecutive = newArgument;
      newArgument.previousConsecutive = previousConsecutive;
      previousConsecutive = null;
    }
    if (nextConsecutive != null) {
      nextConsecutive.previousConsecutive = newArgument;
      newArgument.nextConsecutive = nextConsecutive;
      nextConsecutive = null;
    }
  }

  public boolean isLinked() {
    return nextConsecutive != null || previousConsecutive != null;
  }

  public Value getStartOfConsecutive() {
    Value current = this;
    while (current.getPreviousConsecutive() != null) {
      current = current.getPreviousConsecutive();
    }
    return current;
  }

  public Value getNextConsecutive() {
    return nextConsecutive;
  }

  public Value getPreviousConsecutive() {
    return previousConsecutive;
  }

  public boolean onlyUsedInBlock(BasicBlock block) {
    if (hasPhiUsers() || hasDebugUsers()) {
      return false;
    }
    for (Instruction user : uniqueUsers()) {
      if (user.getBlock() != block) {
        return false;
      }
    }
    return true;
  }

  public Set<Instruction> uniqueUsers() {
    if (uniqueUsers != null) {
      return uniqueUsers;
    }
    return uniqueUsers = ImmutableSet.copyOf(users);
  }

  public <T extends Instruction> Iterable<T> uniqueUsers(Predicate<? super Instruction> predicate) {
    return IterableUtils.filter(uniqueUsers(), predicate);
  }

  public boolean hasSingleUniqueUser() {
    return uniqueUsers().size() == 1;
  }

  public Instruction singleUniqueUser() {
    assert ImmutableSet.copyOf(users).size() == 1;
    return users.getFirst();
  }

  public Set<Instruction> aliasedUsers() {
    return aliasedUsers(DefaultAliasedValueConfiguration.getInstance());
  }

  public Set<Instruction> aliasedUsers(AliasedValueConfiguration configuration) {
    Set<Instruction> users = SetUtils.newIdentityHashSet(uniqueUsers());
    Set<Instruction> visited = Sets.newIdentityHashSet();
    collectAliasedUsersViaAssume(configuration, visited, uniqueUsers(), users);
    return users;
  }

  private static void collectAliasedUsersViaAssume(
      AliasedValueConfiguration configuration,
      Set<Instruction> visited,
      Set<Instruction> usersToTest,
      Set<Instruction> collectedUsers) {
    for (Instruction user : usersToTest) {
      if (!visited.add(user)) {
        continue;
      }
      if (configuration.isIntroducingAnAlias(user)) {
        collectedUsers.addAll(user.outValue().uniqueUsers());
        collectAliasedUsersViaAssume(
            configuration, visited, user.outValue().uniqueUsers(), collectedUsers);
      }
    }
  }

  public Phi firstPhiUser() {
    assert !phiUsers.isEmpty();
    return phiUsers.getFirst();
  }

  public Set<Phi> uniquePhiUsers() {
    if (uniquePhiUsers != null) {
      return uniquePhiUsers;
    }
    return uniquePhiUsers = ImmutableSet.copyOf(phiUsers);
  }

  public Set<Instruction> debugUsers() {
    return debugData == null ? null : Collections.unmodifiableSet(debugData.users);
  }

  public boolean hasAnyUsers() {
    return hasUsers() || hasPhiUsers() || hasDebugUsers();
  }

  public boolean hasDebugUsers() {
    return debugData != null && !debugData.users.isEmpty();
  }

  public boolean hasNonDebugUsers() {
    return hasUsers() || hasPhiUsers();
  }

  public boolean hasPhiUsers() {
    return !phiUsers.isEmpty();
  }

  public boolean hasUsers() {
    return !users.isEmpty();
  }

  public boolean hasUserThatMatches(Predicate<Instruction> predicate) {
    for (Instruction user : uniqueUsers()) {
      if (predicate.test(user)) {
        return true;
      }
    }
    return false;
  }

  public int numberOfUsers() {
    int size = users.size();
    if (size <= 1) {
      return size;
    }
    return uniqueUsers().size();
  }

  public int numberOfPhiUsers() {
    int size = phiUsers.size();
    if (size <= 1) {
      return size;
    }
    return uniquePhiUsers().size();
  }

  public int numberOfAllNonDebugUsers() {
    return numberOfUsers() + numberOfPhiUsers();
  }

  public int numberOfDebugUsers() {
    return debugData == null ? 0 : debugData.users.size();
  }

  public int numberOfAllUsers() {
    return numberOfAllNonDebugUsers() + numberOfDebugUsers();
  }

  public boolean isUsed() {
    return !users.isEmpty() || !phiUsers.isEmpty() || numberOfDebugUsers() > 0;
  }

  public boolean isUnused() {
    return !isUsed();
  }

  public boolean isAlwaysNull(AppView<?> appView) {
    if (hasLocalInfo()) {
      // Not always null as the value can be changed via the debugger.
      return false;
    }
    if (type.isDefinitelyNull()) {
      return true;
    }
    if (type.isClassType() && appView.appInfo().hasLiveness()) {
      return type.asClassType().getClassType().isAlwaysNull(appView.withLiveness());
    }
    return false;
  }

  public boolean isMaybeNull() {
    return !isNeverNull();
  }

  public boolean usedInMonitorOperation() {
    for (Instruction instruction : uniqueUsers()) {
      if (instruction.isMonitor()) {
        return true;
      }
    }
    return false;
  }

  public void addUser(Instruction user) {
    users.add(user);
    uniqueUsers = null;
  }

  public void removeUser(Instruction user) {
    users.remove(user);
    uniqueUsers = null;
  }

  private void fullyRemoveUser(Instruction user) {
    users.removeIf(u -> u == user);
    uniqueUsers = null;
  }

  public void clearUsers() {
    users.clear();
    uniqueUsers = null;
    clearPhiUsers();
    if (debugData != null) {
      debugData.users.clear();
    }
  }

  public void clearPhiUsers() {
    phiUsers.clear();
    uniquePhiUsers = null;
  }

  public void addPhiUser(Phi user) {
    phiUsers.add(user);
    uniquePhiUsers = null;
  }

  public void removePhiUser(Phi user) {
    phiUsers.remove(user);
    uniquePhiUsers = null;
  }

  private void fullyRemovePhiUser(Phi user) {
    phiUsers.removeIf(u -> u == user);
    uniquePhiUsers = null;
  }

  public boolean isUninitializedLocal() {
    return definition != null && definition.isDebugLocalUninitialized();
  }

  public boolean isInitializedLocal() {
    return !isUninitializedLocal();
  }

  public void removeDebugUser(Instruction user) {
    if (debugData != null && debugData.users != null) {
      debugData.users.remove(user);
      return;
    }
    assert false;
  }

  public boolean hasUsersInfo() {
    return users != null;
  }

  public void clearUsersInfo() {
    users = null;
    uniqueUsers = null;
    phiUsers = null;
    uniquePhiUsers = null;
    if (debugData != null) {
      debugData.users = null;
    }
  }

  // Returns the set of Value that are affected if the current value's type lattice is updated.
  public AffectedValues affectedValues() {
    AffectedValues affectedValues = new AffectedValues();
    forEachAffectedValue(affectedValues::add);
    return affectedValues;
  }

  public void addAffectedValuesTo(Set<Value> affectedValues) {
    forEachAffectedValue(affectedValues::add);
  }

  public void forEachAffectedValue(Consumer<Value> consumer) {
    for (Instruction user : uniqueUsers()) {
      if (user.hasOutValue()) {
        consumer.accept(user.outValue());
      }
    }
    uniquePhiUsers().forEach(consumer);
  }

  public void replaceUsers(Value newValue) {
    replaceUsers(newValue, null);
  }

  public void replaceUsers(Value newValue, Set<Value> affectedValues) {
    if (this == newValue) {
      return;
    }
    for (Instruction user : uniqueUsers()) {
      user.replaceValue(this, newValue, affectedValues);
    }
    for (Phi user : uniquePhiUsers()) {
      user.replaceOperand(this, newValue, affectedValues);
    }
    if (debugData != null) {
      for (Instruction user : debugData.users) {
        replaceUserInDebugData(user, newValue);
      }
      debugData.users.clear();
    }
    clearUsers();
  }

  public void replacePhiUsers(Value newValue) {
    if (this == newValue) {
      return;
    }
    for (Phi user : uniquePhiUsers()) {
      user.replaceOperand(this, newValue);
    }
    clearPhiUsers();
  }

  public void replaceSelectiveInstructionUsers(Value newValue, Predicate<Instruction> predicate) {
    if (this == newValue) {
      return;
    }
    for (Instruction user : uniqueUsers()) {
      if (predicate.test(user)) {
        fullyRemoveUser(user);
        user.replaceValue(this, newValue);
      }
    }
  }

  public void replaceSelectiveUsers(
      Value newValue,
      Set<Instruction> selectedInstructions,
      Map<Phi, IntList> selectedPhisWithPredecessorIndexes) {
    replaceSelectiveUsers(newValue, selectedInstructions, selectedPhisWithPredecessorIndexes, null);
  }

  public void replaceSelectiveUsers(
      Value newValue,
      Set<Instruction> selectedInstructions,
      Map<Phi, IntList> selectedPhisWithPredecessorIndexes,
      Set<Value> affectedValues) {
    if (this == newValue) {
      return;
    }
    // Unlike {@link #replaceUsers} above, which clears all users at the end, this routine will
    // manually remove updated users. Remove such updated users from the user pool before replacing
    // value, otherwise we lost the identity.
    for (Instruction user : uniqueUsers()) {
      if (selectedInstructions.contains(user)) {
        fullyRemoveUser(user);
        user.replaceValue(this, newValue, affectedValues);
      }
    }
    Set<Phi> selectedPhis = selectedPhisWithPredecessorIndexes.keySet();
    for (Phi user : uniquePhiUsers()) {
      if (selectedPhis.contains(user)) {
        long count = user.getOperands().stream().filter(operand -> operand == this).count();
        IntList positionsToUpdate = selectedPhisWithPredecessorIndexes.get(user);
        // We may not _fully_ remove this from the phi, e.g., phi(v0, v1, v1) -> phi(v0, vn, v1).
        if (count == positionsToUpdate.size()) {
          fullyRemovePhiUser(user);
        }
        for (int position : positionsToUpdate) {
          assert user.getOperand(position) == this;
          user.replaceOperandAt(position, newValue, affectedValues);
        }
      }
    }
    if (debugData != null) {
      Iterator<Instruction> users = debugData.users.iterator();
      while (users.hasNext()) {
        Instruction user = users.next();
        if (selectedInstructions.contains(user)) {
          replaceUserInDebugData(user, newValue);
          users.remove();
        }
      }
    }
  }

  private void replaceUserInDebugData(Instruction user, Value newValue) {
    user.replaceDebugValue(this, newValue);
    // If user is a DebugLocalRead and now has no debug values, we would like to remove it.
    // However, replaceUserInDebugData() is called in contexts where the instruction list is being
    // iterated, so we cannot remove user from the instruction list at this point.
  }

  public void replaceDebugUser(Instruction oldUser, Instruction newUser) {
    boolean removed = debugData.users.remove(oldUser);
    assert removed;
    if (removed) {
      addDebugLocalEnd(newUser);
    }
  }

  public void setLiveIntervals(LiveIntervals intervals) {
    assert liveIntervals == null;
    liveIntervals = intervals;
  }

  public LiveIntervals getLiveIntervals() {
    return liveIntervals;
  }

  public boolean needsRegister() {
    assert needsRegister >= 0;
    // This has quadratic behavior so don't check for large user sets.
    assert !hasUsersInfo()
        || numberOfAllUsers() > 100
        || (needsRegister > 0) == internalComputeNeedsRegister();
    return needsRegister > 0;
  }

  public void setNeedsRegister(boolean value) {
    assert needsRegister == -1 || (needsRegister > 0) == value;
    needsRegister = value ? 1 : 0;
  }

  public void computeNeedsRegister() {
    assert needsRegister < 0;
    setNeedsRegister(internalComputeNeedsRegister());
  }

  public boolean internalComputeNeedsRegister() {
    if (!isConstNumber()) {
      return true;
    }
    if (numberOfPhiUsers() > 0) {
      return true;
    }
    for (Instruction user : uniqueUsers()) {
      if (user.needsValueInRegister(this)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasRegisterConstraint() {
    for (Instruction instruction : uniqueUsers()) {
      if (instruction.maxInValueRegister() != Constants.U16BIT_MAX) {
        return true;
      }
    }
    return false;
  }

  public boolean isValueOnStack() {
    return false;
  }

  @Override
  public int compareTo(Value value) {
    return Integer.compare(this.number, value.number);
  }

  @Override
  public int hashCode() {
    return number;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("v");
    builder.append(number);
    boolean isConstant = definition != null && definition.isConstNumber();
    if (isConstant || hasLocalInfo()) {
      builder.append("(");
      if (isConstant && definition.asConstNumber().outValue != null) {
        ConstNumber constNumber = definition.asConstNumber();
        if (constNumber.getOutType().isSinglePrimitive()) {
          builder.append((int) constNumber.getRawValue());
        } else {
          builder.append(constNumber.getRawValue());
        }
      }
      if (isConstant && hasLocalInfo()) {
        builder.append(", ");
      }
      if (hasLocalInfo()) {
        builder.append(getLocalInfo());
      }
      builder.append(")");
    }
    if (valueRange != null) {
      builder.append(valueRange);
    }
    return builder.toString();
  }

  public ValueType outType() {
    return ValueType.fromType(type);
  }

  public ConstInstruction getConstInstruction() {
    assert isConstant();
    return definition.getOutConstantConstInstruction();
  }

  public boolean isConstNumber() {
    return isConstant() && getConstInstruction().isConstNumber();
  }

  public boolean isConstBoolean(boolean value) {
    return isConstNumber()
        && definition.asConstNumber().getRawValue() == BooleanUtils.longValue(value);
  }

  public boolean isConstZero() {
    return isConstNumber() && definition.asConstNumber().isZero();
  }

  public boolean isConstString() {
    return isConstant() && getConstInstruction().isConstString();
  }

  public boolean isDexItemBasedConstString() {
    return isConstant() && getConstInstruction().isDexItemBasedConstString();
  }

  public boolean isDexItemBasedConstStringThatNeedsToComputeClassName() {
    return isDexItemBasedConstString()
        && getConstInstruction()
            .asDexItemBasedConstString()
            .getNameComputationInfo()
            .needsToComputeName();
  }

  public boolean isConstClass() {
    return isConstant() && getConstInstruction().isConstClass();
  }

  public boolean isConstant() {
    return definition.isOutConstant() && !hasLocalInfo();
  }

  public final AbstractValue getAbstractValue(AppView<?> appView, ProgramMethod context) {
    return getAbstractValue(appView, context, AbstractValueSupplier.unknown());
  }

  public final AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (!appView.enableWholeProgramOptimizations()) {
      return UnknownValue.getInstance();
    }

    if (getType().nullability().isDefinitelyNull()) {
      return appView.abstractValueFactory().createNullValue();
    }

    Value root = getAliasedValue();
    if (root.isPhi()) {
      return UnknownValue.getInstance();
    }

    return root.definition.getAbstractValue(appView, context, abstractValueSupplier);
  }

  public boolean isDefinedByInstructionSatisfying(Predicate<Instruction> predicate) {
    return predicate.test(definition);
  }

  public boolean isPhi() {
    return false;
  }

  public Phi asPhi() {
    return null;
  }

  /**
   * Returns whether this value is known to never be <code>null</code>.
   */
  public boolean isNeverNull() {
    assert type.isReferenceType();
    return isDefinedByInstructionSatisfying(Instruction::isAssumeWithNonNullAssumption)
        || type.isDefinitelyNotNull();
  }

  public boolean isArgument() {
    return isDefinedByInstructionSatisfying(Instruction::isArgument);
  }

  public boolean onlyDependsOnArgument() {
    if (isPhi()) {
      return false;
    }
    switch (definition.opcode()) {
      case ARGUMENT:
      case CONST_CLASS:
      case CONST_NUMBER:
      case CONST_STRING:
      case DEX_ITEM_BASED_CONST_STRING:
        // Constants don't depend on anything.
        return true;
      case CHECK_CAST:
        return definition.asCheckCast().object().onlyDependsOnArgument();
      case INSTANCE_OF:
        return definition.asInstanceOf().value().onlyDependsOnArgument();
      default:
        return false;
    }
  }

  public boolean knownToBeBoolean() {
    return knownToBeBoolean(null);
  }

  public boolean knownToBeBoolean(Set<Phi> seen) {
    if (!getType().isInt()) {
      return false;
    }

    if (isPhi()) {
      Phi self = this.asPhi();
      if (seen == null) {
        seen = Sets.newIdentityHashSet();
      }
      if (seen.contains(self)) {
        return true;
      }
      seen.add(self);
      for (Value operand : self.getOperands()) {
        if (!operand.knownToBeBoolean(seen)) {
          operand.knownToBeBoolean(seen);
          return false;
        }
      }
      return true;
    }
    assert definition != null;
    return definition.outTypeKnownToBeBoolean(seen);
  }

  public void markAsThis() {
    assert isArgument();
    assert !isThis;
    isThis = true;
  }

  /**
   * Returns whether this value is known to be the receiver (this argument) in a method body.
   * <p>
   * For a receiver value {@link #isNeverNull()} is guaranteed to be <code>true</code> as well.
   */
  public boolean isThis() {
    return isThis;
  }

  public void setValueRange(NumberFromIntervalValue range) {
    valueRange = new LongInterval(range.getMinInclusive(), range.getMaxInclusive());
  }

  public boolean hasValueRange() {
    return valueRange != null || isConstNumber();
  }

  public LongInterval getValueRange() {
    if (isConstNumber()) {
      if (type.isSinglePrimitive()) {
        int value = getConstInstruction().asConstNumber().getIntValue();
        return new LongInterval(value, value);
      } else {
        assert type.isWidePrimitive();
        long value = getConstInstruction().asConstNumber().getLongValue();
        return new LongInterval(value, value);
      }
    } else {
      return valueRange;
    }
  }

  public boolean isZero() {
    return isConstant()
        && getConstInstruction().isConstNumber()
        && getConstInstruction().asConstNumber().isZero();
  }

  /**
   * Overwrites the current type lattice value without any assertions.
   *
   * @param newType The new type lattice element
   */
  public void setType(TypeElement newType) {
    assert newType != null;
    type = newType;
  }

  public void widening(AppView<?> appView, TypeElement newType) {
    // During WIDENING (due to fix-point iteration), type update is monotonically upwards,
    //   i.e., towards something wider.
    assert skipWideningOrNarrowingCheck(appView) || this.type.lessThanOrEqual(newType, appView)
        : "During WIDENING, "
            + newType
            + " < "
            + type
            + " at "
            + (isPhi() ? asPhi().printPhi() : definition.toString());
    setType(newType);
  }

  public void narrowing(AppView<?> appView, ProgramMethod context, TypeElement newType) {
    // During NARROWING (e.g., after inlining), type update is monotonically downwards,
    //   i.e., towards something narrower, with more specific type info.
    assert skipWideningOrNarrowingCheck(appView) || !this.type.strictlyLessThan(newType, appView)
        : "During NARROWING, "
            + type
            + " < "
            + newType
            + " at "
            + (isPhi() ? asPhi().printPhi() : definition.toString())
            + " (context: "
            + context
            + ")";
    setType(newType);
  }

  private boolean skipWideningOrNarrowingCheck(AppView<?> appView) {
    // TODO(b/169120386): We should not check widening or narrowing when in D8 with valid type-info.
    return !appView.options().testing.enableNarrowAndWideningingChecksInD8
        && !appView.enableWholeProgramOptimizations();
  }

  public BasicBlock getBlock() {
    return definition.getBlock();
  }

  public TypeElement getType() {
    return type;
  }

  public DynamicTypeWithUpperBound getDynamicType(AppView<AppInfoWithLiveness> appView) {
    return DynamicType.create(appView, this);
  }

  public TypeElement getDynamicUpperBoundType(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    // Try to find an alias of the receiver, which is defined by an instruction of the type Assume.
    Value aliasedValue =
        getSpecificAliasedValue(
            value ->
                value.isDefinedByInstructionSatisfying(
                    Instruction::isAssumeWithDynamicTypeAssumption));
    Value root = getAliasedValue();
    TypeElement upperBoundType;
    if (aliasedValue != null) {
      // If there is an alias of the receiver, which is defined by an Assume instruction that
      // carries a dynamic type, then use the dynamic type as the refined receiver type.
      upperBoundType =
          aliasedValue
              .getDefinition()
              .asAssume()
              .getDynamicTypeAssumption()
              .getDynamicUpperBoundType();
    } else if (root.isPhi()) {
      upperBoundType = root.getDynamicUpperBoundType(appView);
    } else {
      // Otherwise, simply use the static type.
      upperBoundType = type;
    }

    // Account for nullability, which could be flown from non-null assumption in between dynamic
    // type assumption or simply from array/object creation.
    if (type.isDefinitelyNotNull() && upperBoundType.isNullable()) {
      // Having non-null assumption means it is a reference type.
      assert upperBoundType.isReferenceType();
      // Then, we can return the non-null variant of dynamic type if both assumptions are aliased.
      return upperBoundType.asReferenceType().asMeetWithNotNull();
    }
    return upperBoundType;
  }

  public ClassTypeElement getDynamicLowerBoundType(AppView<AppInfoWithLiveness> appView) {
    return getDynamicLowerBoundType(appView, null, Nullability.maybeNull());
  }

  public ClassTypeElement getDynamicLowerBoundType(
      AppView<AppInfoWithLiveness> appView,
      TypeElement dynamicUpperBoundType,
      Nullability maxNullability) {
    // If the dynamic upper bound type is a final or effectively-final class type, then we know the
    // lower bound. Since the dynamic upper bound type is below the static type in the class
    // hierarchy, we only need to check if the dynamic upper bound type is effectively final if it
    // is present.
    TypeElement upperBoundType = dynamicUpperBoundType != null ? dynamicUpperBoundType : getType();
    if (upperBoundType.isClassType()) {
      ClassTypeElement upperBoundClassType = upperBoundType.asClassType();
      DexClass upperBoundClass = appView.definitionFor(upperBoundClassType.getClassType());
      if (upperBoundClass != null && upperBoundClass.isEffectivelyFinal(appView)) {
        return upperBoundClassType.meetNullability(maxNullability);
      }
    }

    Value root = getAliasedValue();
    if (root.isPhi()) {
      return null;
    }

    Instruction definition = root.getDefinition();
    if (definition.isNewInstance()) {
      DexType type = definition.asNewInstance().getType();
      DexClass clazz = appView.definitionFor(type);
      if (clazz != null && !clazz.isInterface()) {
        assert !maxNullability.isBottom();
        return TypeElement.fromDexType(type, definitelyNotNull(), appView).asClassType();
      }
      return null;
    }

    // Try to find an alias of the receiver, which is defined by an instruction of the type Assume.
    Value aliasedValue =
        getSpecificAliasedValue(value -> value.getDefinition().isAssumeWithDynamicTypeAssumption());
    if (aliasedValue != null) {
      ClassTypeElement aliasedValueType =
          aliasedValue
              .getDefinition()
              .asAssume()
              .getDynamicTypeAssumption()
              .getDynamicLowerBoundType();
      if (aliasedValueType != null) {
        aliasedValueType = aliasedValueType.meetNullability(getType().nullability());
        assert aliasedValueType.nullability().lessThanOrEqual(maxNullability);
        return aliasedValueType;
      }
    }

    return null;
  }
}
