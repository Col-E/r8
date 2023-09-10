// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinClassMetadataReader.hasKotlinClassMetadataAnnotation;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.EnqueuerDefinitionSupplier;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class KotlinMetadataEnqueuerExtension extends EnqueuerAnalysis {

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  private final AppView<?> appView;
  private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;
  private final Set<DexType> prunedTypes;
  private final AtomicBoolean reportedUnknownMetadataVersion = new AtomicBoolean(false);

  public KotlinMetadataEnqueuerExtension(
      AppView<?> appView,
      EnqueuerDefinitionSupplier enqueuerDefinitionSupplier,
      Set<DexType> prunedTypes) {
    this.appView = appView;
    this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
    this.prunedTypes = prunedTypes;
  }

  private KotlinMetadataDefinitionSupplier definitionsForContext(ProgramDefinition context) {
    return new KotlinMetadataDefinitionSupplier(context, enqueuerDefinitionSupplier, prunedTypes);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void done(Enqueuer enqueuer) {
    // In the first round of tree shaking build up all metadata such that it can be traced later.
    boolean keepKotlinMetadata =
        KeepClassInfo.isKotlinMetadataClassKept(
            appView.dexItemFactory(),
            appView.options(),
            appView.appInfo()::definitionForWithoutExistenceAssert,
            enqueuer::getKeepInfo);
    // In the first round of tree shaking build up all metadata such that it can be traced later.
    if (enqueuer.getMode().isInitialTreeShaking()) {
      Set<DexMethod> keepByteCodeFunctions = Sets.newIdentityHashSet();
      Set<DexProgramClass> localOrAnonymousClasses = Sets.newIdentityHashSet();
      enqueuer.forAllLiveClasses(
          clazz -> {
            assert clazz.getKotlinInfo().isNoKotlinInformation();
            if (enqueuer
                .getKeepInfo(clazz)
                .isKotlinMetadataRemovalAllowed(appView.options(), keepKotlinMetadata)) {
              if (KotlinClassMetadataReader.isLambda(
                      appView, clazz, () -> reportedUnknownMetadataVersion.getAndSet(true))
                  && clazz.hasClassInitializer()) {
                feedback.classInitializerMayBePostponed(clazz.getClassInitializer());
              }
              clazz.clearKotlinInfo();
              clazz.removeAnnotations(
                  annotation ->
                      annotation.getAnnotationType()
                          == appView.dexItemFactory().kotlinMetadataType);
            } else {
              clazz.setKotlinInfo(
                  KotlinClassMetadataReader.getKotlinInfo(
                      appView,
                      clazz,
                      method -> keepByteCodeFunctions.add(method.getReference()),
                      () -> reportedUnknownMetadataVersion.getAndSet(true)));
              if (clazz.getEnclosingMethodAttribute() != null
                  && clazz.getEnclosingMethodAttribute().getEnclosingMethod() != null) {
                localOrAnonymousClasses.add(clazz);
              }
            }
          });
      for (DexProgramClass localOrAnonymousClass : localOrAnonymousClasses) {
        EnclosingMethodAttribute enclosingAttribute =
            localOrAnonymousClass.getEnclosingMethodAttribute();
        DexClass holder =
            definitionsForContext(localOrAnonymousClass)
                .definitionForHolder(enclosingAttribute.getEnclosingMethod());
        if (holder == null) {
          continue;
        }
        DexEncodedMethod method = holder.lookupMethod(enclosingAttribute.getEnclosingMethod());
        // If we cannot lookup the method, the conservative choice is keep the byte code.
        if (method == null
            || (method.getKotlinInfo().isFunction()
                && method.getKotlinInfo().asFunction().hasCrossInlineParameter())) {
          localOrAnonymousClass.forEachProgramMethod(
              m -> keepByteCodeFunctions.add(m.getReference()));
        }
      }
      appView.setCfByteCodePassThrough(keepByteCodeFunctions);
    } else {
      assert enqueuer.getMode().isFinalTreeShaking();
      enqueuer.forAllLiveClasses(
          clazz -> {
            if (enqueuer
                .getKeepInfo(clazz)
                .isKotlinMetadataRemovalAllowed(appView.options(), keepKotlinMetadata)) {
              clazz.clearKotlinInfo();
              clazz.members().forEach(DexEncodedMember::clearKotlinInfo);
              clazz.removeAnnotations(
                  annotation ->
                      annotation.getAnnotationType()
                          == appView.dexItemFactory().kotlinMetadataType);
            } else {
              // Use the concrete getNoKotlinInfo() instead of isNoKotlinInformation() to handle
              // invalid kotlin info as well.
              assert hasKotlinClassMetadataAnnotation(clazz, definitionsForContext(clazz))
                      == (clazz.getKotlinInfo() != getNoKotlinInfo())
                  : clazz.toSourceString()
                      + " "
                      + (clazz.getKotlinInfo() == getNoKotlinInfo() ? "no info" : "has info");
            }
          });
    }
    // Trace through the modeled kotlin metadata.
    enqueuer.forAllLiveClasses(
        clazz -> {
          clazz.getKotlinInfo().trace(definitionsForContext(clazz));
          clazz.forEachProgramMember(
              member ->
                  member.getDefinition().getKotlinInfo().trace(definitionsForContext(member)));
        });
  }

  public class KotlinMetadataDefinitionSupplier implements DexDefinitionSupplier {

    private final ProgramDefinition context;
    private final EnqueuerDefinitionSupplier enqueuerDefinitionSupplier;
    private final Set<DexType> prunedTypes;

    private KotlinMetadataDefinitionSupplier(
        ProgramDefinition context,
        EnqueuerDefinitionSupplier enqueuerDefinitionSupplier,
        Set<DexType> prunedTypes) {
      this.context = context;
      this.enqueuerDefinitionSupplier = enqueuerDefinitionSupplier;
      this.prunedTypes = prunedTypes;
    }

    @Override
    public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
      throw new Unreachable("Not yet used");
    }

    @Override
    public DexClass definitionFor(DexType type) {
      // TODO(b/157700128) Metadata cannot at this point keep anything alive. Therefore, if a type
      //  has been pruned it may still be referenced, so we do an early check here to ensure it will
      //  not end up as. Ideally, those types should be removed by a pass on the modeled data.
      if (prunedTypes != null && prunedTypes.contains(type)) {
        return null;
      }
      return enqueuerDefinitionSupplier.definitionFor(type, context);
    }

    @Override
    public DexItemFactory dexItemFactory() {
      return appView.dexItemFactory();
    }
  }
}
