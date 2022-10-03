// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import static com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RemoveInnerFramesAction.REMOVE_INNER_FRAMES_NAME;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.PositionalMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.internal.RetraceStackTraceContextImpl;
import com.android.tools.r8.retrace.internal.RetraceStackTraceCurrentEvaluationInformation;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RewriteFrameMappingInformation extends PositionalMappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_0;
  public static final String ID = "com.android.tools.r8.rewriteFrame";
  private static final String CONDITIONS_KEY = "conditions";
  private static final String ACTIONS_KEY = "actions";

  private final List<Condition> conditions;
  private final List<RewriteAction> actions;

  private RewriteFrameMappingInformation(List<Condition> conditions, List<RewriteAction> actions) {
    this.conditions = conditions;
    this.actions = actions;
  }

  public List<Condition> getConditions() {
    return conditions;
  }

  public List<RewriteAction> getActions() {
    return actions;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonArray conditionsArray = new JsonArray();
    conditions.forEach(condition -> conditionsArray.add(condition.serialize()));
    object.add(CONDITIONS_KEY, conditionsArray);
    JsonArray actionsArray = new JsonArray();
    actions.forEach(action -> actionsArray.add(action.serialize()));
    object.add(ACTIONS_KEY, actionsArray);
    return object.toString();
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isRewriteFrameMappingInformation();
  }

  public static void deserialize(
      MapVersion mapVersion, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (!isSupported(mapVersion)) {
      return;
    }
    ImmutableList.Builder<Condition> conditions = ImmutableList.builder();
    object
        .get(CONDITIONS_KEY)
        .getAsJsonArray()
        .forEach(
            element -> {
              conditions.add(Condition.deserialize(element));
            });
    ImmutableList.Builder<RewriteAction> actions = ImmutableList.builder();
    object
        .get(ACTIONS_KEY)
        .getAsJsonArray()
        .forEach(element -> actions.add(RewriteAction.deserialize(element)));
    onMappingInfo.accept(new RewriteFrameMappingInformation(conditions.build(), actions.build()));
  }

  @Override
  public boolean isRewriteFrameMappingInformation() {
    return true;
  }

  @Override
  public RewriteFrameMappingInformation asRewriteFrameMappingInformation() {
    return this;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    throw new MappingComposeException("Unable to compose " + ID);
  }

  public static RewriteFrameMappingInformation.Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final List<Condition> conditions = new ArrayList<>();
    private final List<RewriteAction> actions = new ArrayList<>();

    public Builder addCondition(Condition condition) {
      this.conditions.add(condition);
      return this;
    }

    public Builder addRewriteAction(RewriteAction action) {
      this.actions.add(action);
      return this;
    }

    public RewriteFrameMappingInformation build() {
      return new RewriteFrameMappingInformation(conditions, actions);
    }
  }

  public abstract static class Condition {

    protected abstract JsonPrimitive serialize();

    private static Condition deserialize(JsonElement element) {
      String elementString = element.getAsString();
      int argIndex = elementString.indexOf('(');
      if (argIndex < 1 || !elementString.endsWith(")")) {
        throw new CompilationError("Invalid formatted condition: " + elementString);
      }
      String functionName = elementString.substring(0, argIndex);
      String contents = elementString.substring(argIndex + 1, elementString.length() - 1);
      if (ThrowsCondition.FUNCTION_NAME.equals(functionName)) {
        return ThrowsCondition.deserialize(contents);
      }
      throw new CompilationError("Unexpected condition: " + elementString);
    }

    public boolean isThrowsCondition() {
      return false;
    }

    public ThrowsCondition asThrowsCondition() {
      return null;
    }

    public abstract boolean evaluate(RetraceStackTraceContextImpl context);
  }

  public static class ThrowsCondition extends Condition {

    static final String FUNCTION_NAME = "throws";

    private ClassReference classReference;

    private ThrowsCondition(ClassReference classReference) {
      this.classReference = classReference;
    }

    @Override
    protected JsonPrimitive serialize() {
      return new JsonPrimitive(FUNCTION_NAME + "(" + classReference.getDescriptor() + ")");
    }

    @Override
    public boolean isThrowsCondition() {
      return true;
    }

    @Override
    public ThrowsCondition asThrowsCondition() {
      return this;
    }

    public void setClassReferenceInternal(ClassReference reference) {
      this.classReference = reference;
    }

    public ClassReference getClassReference() {
      return classReference;
    }

    @Override
    public boolean evaluate(RetraceStackTraceContextImpl context) {
      return classReference.equals(context.getThrownException());
    }

    public static ThrowsCondition deserialize(String conditionString) {
      if (!DescriptorUtils.isClassDescriptor(conditionString)) {
        throw new CompilationError("Unexpected throws-descriptor: " + conditionString);
      }
      return new ThrowsCondition(Reference.classFromDescriptor(conditionString));
    }

    public static ThrowsCondition create(ClassReference classReference) {
      return new ThrowsCondition(classReference);
    }
  }

  public abstract static class RewriteAction {

    abstract JsonElement serialize();

    private static RewriteAction deserialize(JsonElement element) {
      String functionString = element.getAsString();
      int startArgsIndex = functionString.indexOf("(");
      int endArgsIndex = functionString.indexOf(")");
      if (endArgsIndex <= startArgsIndex) {
        throw new Unimplemented("Unexpected action: " + functionString);
      }
      String functionName = functionString.substring(0, startArgsIndex);
      String args = functionString.substring(startArgsIndex + 1, endArgsIndex);
      if (REMOVE_INNER_FRAMES_NAME.equals(functionName)) {
        return RemoveInnerFramesAction.deserialize(args);
      }
      assert false : "Unknown function " + functionName;
      throw new Unimplemented("Unexpected action: " + functionName);
    }

    public boolean isRemoveInnerFramesAction() {
      return false;
    }

    public RemoveInnerFramesAction asRemoveInnerFramesRewriteAction() {
      return null;
    }

    public abstract void evaluate(RetraceStackTraceCurrentEvaluationInformation.Builder builder);
  }

  public static class RemoveInnerFramesAction extends RewriteAction {

    static final String REMOVE_INNER_FRAMES_NAME = "removeInnerFrames";

    private final int numberOfFrames;

    public RemoveInnerFramesAction(int numberOfFrames) {
      this.numberOfFrames = numberOfFrames;
    }

    public int getNumberOfFrames() {
      return numberOfFrames;
    }

    @Override
    JsonElement serialize() {
      return new JsonPrimitive(REMOVE_INNER_FRAMES_NAME + "(" + numberOfFrames + ")");
    }

    @Override
    public boolean isRemoveInnerFramesAction() {
      return true;
    }

    @Override
    public RemoveInnerFramesAction asRemoveInnerFramesRewriteAction() {
      return this;
    }

    @Override
    public void evaluate(RetraceStackTraceCurrentEvaluationInformation.Builder builder) {
      builder.incrementRemoveInnerFramesCount(numberOfFrames);
    }

    public static RemoveInnerFramesAction create(int numberOfFrames) {
      return new RemoveInnerFramesAction(numberOfFrames);
    }

    public static RemoveInnerFramesAction deserialize(String contents) {
      try {
        return create(Integer.parseInt(contents));
      } catch (NumberFormatException nfe) {
        throw new CompilationError(
            "Unexpected number for " + REMOVE_INNER_FRAMES_NAME + ": " + contents);
      }
    }
  }

  public static class RewritePreviousObfuscatedPosition extends RewriteAction {

    private final Int2IntMap rewriteMap;

    private RewritePreviousObfuscatedPosition(Int2IntMap rewriteMap) {
      this.rewriteMap = rewriteMap;
    }

    @Override
    JsonElement serialize() {
      throw new CompilationError("Do not serialize this");
    }

    @Override
    public void evaluate(RetraceStackTraceCurrentEvaluationInformation.Builder builder) {
      builder.setPosition(rewriteMap.getOrDefault(builder.getPosition(), builder.getPosition()));
    }

    public static RewritePreviousObfuscatedPosition create(Int2IntMap rewriteMap) {
      return new RewritePreviousObfuscatedPosition(rewriteMap);
    }
  }
}
