// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.AndroidResourceInput;
import com.android.tools.r8.AndroidResourceInput.Kind;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ResourceAccessAnalysis implements EnqueuerFieldAccessAnalysis {

  private final R8ResourceShrinkerState resourceShrinkerState;
  private final Map<DexProgramClass, RClassFieldToValueStore> fieldToValueMapping =
      new IdentityHashMap<>();
  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  @SuppressWarnings("UnusedVariable")
  private ResourceAccessAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      R8ResourceShrinkerState resourceShrinkerState) {
    this.appView = appView;
    this.resourceShrinkerState = resourceShrinkerState;
    appView.setResourceShrinkerState(resourceShrinkerState);
    try {
      for (AndroidResourceInput androidResource :
          appView.options().androidResourceProvider.getAndroidResources()) {
        if (androidResource.getKind() == Kind.RESOURCE_TABLE) {
          resourceShrinkerState.setResourceTableInput(androidResource.getByteStream());
          break;
        }
      }
    } catch (ResourceException e) {
      throw appView.reporter().fatalError("Failed initializing resource table");
    }
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (enabled(appView, enqueuer)) {
      enqueuer.registerFieldAccessAnalysis(
          new ResourceAccessAnalysis(appView, enqueuer, new R8ResourceShrinkerState()));
    }
  }

  @Override
  public void done(Enqueuer enqueuer) {
    appView.setResourceShrinkerState(resourceShrinkerState);
    EnqueuerFieldAccessAnalysis.super.done(enqueuer);
  }

  private static boolean enabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    // For now, we only do the resource tracing in the initial round since we don't track inlining
    // of values yet.
    return appView.options().androidResourceProvider != null
        && appView.options().resourceShrinkerConfiguration.isOptimizedShrinking()
        && enqueuer.getMode().isInitialTreeShaking();
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    ProgramField resolvedField = resolutionResult.getProgramField();
    if (resolvedField == null) {
      return;
    }
    if (getMaybeCachedIsRClass(resolvedField.getHolder())) {
      DexProgramClass holderType = resolvedField.getHolder();
      if (!fieldToValueMapping.containsKey(holderType)) {
        populateRClassValues(resolvedField);
      }
      assert fieldToValueMapping.containsKey(holderType);
      RClassFieldToValueStore rClassFieldToValueStore = fieldToValueMapping.get(holderType);
      IntList integers = rClassFieldToValueStore.valueMapping.get(field);
      for (Integer integer : integers) {
        resourceShrinkerState.trace(integer);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void populateRClassValues(ProgramField field) {
    // TODO(287398085): Pending discussions with the AAPT2 team, we might need to harden this
    // to not fail if we wrongly classify an unrelated class as R class in our heuristic..
    RClassFieldToValueStore.Builder rClassValueBuilder = new RClassFieldToValueStore.Builder();
    ProgramMethod programClassInitializer = field.getHolder().getProgramClassInitializer();
    if (programClassInitializer == null) {
      // No initialization of fields, empty R class.
      return;
    }
    IRCode code = programClassInitializer.buildIR(appView, MethodConversionOptions.nonConverting());

    // We handle two cases:
    //  - Simple integer field assigments.
    //  - Assigments of integer arrays to fields.
    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      Value value = staticPut.value();
      if (value.isPhi()) {
        continue;
      }
      IntList values;
      Instruction definition = staticPut.value().definition;
      if (definition.isConstNumber()) {
        values = new IntArrayList(1);
        values.add(definition.asConstNumber().getIntValue());
      } else if (definition.isNewArrayEmpty()) {
        NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
        values = new IntArrayList();
        for (Instruction uniqueUser : newArrayEmpty.outValue().uniqueUsers()) {
          if (uniqueUser.isArrayPut()) {
            Value constValue = uniqueUser.asArrayPut().value();
            if (constValue.isConstNumber()) {
              values.add(constValue.getDefinition().asConstNumber().getIntValue());
            }
          } else {
            assert uniqueUser == staticPut;
          }
        }
      } else if (definition.isNewArrayFilled()) {
        values = new IntArrayList();
        for (Value inValue : definition.asNewArrayFilled().inValues()) {
          if (value.isPhi()) {
            continue;
          }
          Instruction valueDefinition = inValue.definition;
          if (valueDefinition.isConstNumber()) {
            values.add(valueDefinition.asConstNumber().getIntValue());
          }
        }
      } else {
        continue;
      }
      rClassValueBuilder.addMapping(staticPut.getField(), values);
    }

    fieldToValueMapping.put(field.getHolder(), rClassValueBuilder.build());
  }

  private final Map<DexProgramClass, Boolean> cachedClassLookups = new IdentityHashMap<>();

  private boolean getMaybeCachedIsRClass(DexProgramClass holder) {
    Boolean result = cachedClassLookups.get(holder);
    if (result != null) {
      return result;
    }
    String simpleClassName =
        DescriptorUtils.getSimpleClassNameFromDescriptor(holder.getType().toDescriptorString());
    List<String> split = StringUtils.split(simpleClassName, '$');

    if (split.size() < 2) {
      cachedClassLookups.put(holder, false);
      return false;
    }
    String type = split.get(split.size() - 1);
    String rClass = split.get(split.size() - 2);
    // We match on R if:
    // - The name of the Class is R$type - we allow R to be an inner class.
    //   - The inner type should be with lower case
    boolean isRClass = Character.isLowerCase(type.charAt(0)) && rClass.equals("R");
    cachedClassLookups.put(holder, isRClass);
    return isRClass;
  }

  private static class RClassFieldToValueStore {
    private final Map<DexField, IntList> valueMapping;

    private RClassFieldToValueStore(Map<DexField, IntList> valueMapping) {
      this.valueMapping = valueMapping;
    }

    public static class Builder {
      private final Map<DexField, IntList> valueMapping = new IdentityHashMap<>();

      public void addMapping(DexField field, IntList values) {
        assert !valueMapping.containsKey(field);
        valueMapping.put(field, values);
      }

      public RClassFieldToValueStore build() {
        return new RClassFieldToValueStore(valueMapping);
      }
    }
  }
}
