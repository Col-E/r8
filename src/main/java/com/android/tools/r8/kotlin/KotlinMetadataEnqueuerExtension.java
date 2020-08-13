// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinClassMetadataReader.hasKotlinClassMetadataAnnotation;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.NO_KOTLIN_INFO;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer;
import com.google.common.collect.Sets;
import java.util.Set;

public class KotlinMetadataEnqueuerExtension extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final DexDefinitionSupplier definitionSupplier;

  public KotlinMetadataEnqueuerExtension(
      AppView<?> appView, DexDefinitionSupplier definitionSupplier, Set<DexType> prunedTypes) {
    this.appView = appView;
    this.definitionSupplier = new KotlinMetadataDefinitionSupplier(definitionSupplier, prunedTypes);
  }

  @Override
  public void done(Enqueuer enqueuer) {
    DexType kotlinMetadataType = appView.dexItemFactory().kotlinMetadataType;
    DexClass kotlinMetadataClass =
        appView.appInfo().definitionForWithoutExistenceAssert(kotlinMetadataType);
    // In the first round of tree shaking build up all metadata such that it can be traced later.
    boolean keepMetadata =
        kotlinMetadataClass == null
            || kotlinMetadataClass.isNotProgramClass()
            || enqueuer.isPinned(kotlinMetadataType);
    if (enqueuer.getMode().isInitialTreeShaking()) {
      Set<DexMethod> keepByteCodeFunctions = Sets.newIdentityHashSet();
      Set<DexProgramClass> localOrAnonymousClasses = Sets.newIdentityHashSet();
      enqueuer.forAllLiveClasses(
          clazz -> {
            boolean onlyProcessLambdas = !keepMetadata || !enqueuer.isPinned(clazz.type);
            assert clazz.getKotlinInfo().isNoKotlinInformation();
            clazz.setKotlinInfo(
                KotlinClassMetadataReader.getKotlinInfo(
                    appView.dexItemFactory().kotlin,
                    clazz,
                    definitionSupplier.dexItemFactory(),
                    appView.options().reporter,
                    onlyProcessLambdas,
                    method -> keepByteCodeFunctions.add(method.method)));
            if (clazz.getEnclosingMethodAttribute() != null
                && clazz.getEnclosingMethodAttribute().getEnclosingMethod() != null) {
              localOrAnonymousClasses.add(clazz);
            }
          });
      appView.setCfByteCodePassThrough(keepByteCodeFunctions);
      for (DexProgramClass localOrAnonymousClass : localOrAnonymousClasses) {
        EnclosingMethodAttribute enclosingAttribute =
            localOrAnonymousClass.getEnclosingMethodAttribute();
        DexClass holder =
            definitionSupplier.definitionForHolder(enclosingAttribute.getEnclosingMethod());
        if (holder == null) {
          continue;
        }
        DexEncodedMethod method = holder.lookupMethod(enclosingAttribute.getEnclosingMethod());
        // If we cannot lookup the method, the conservative choice is keep the byte code.
        if (method == null
            || (method.getKotlinMemberInfo().isFunction()
                && method.getKotlinMemberInfo().asFunction().hasCrossInlineParameter())) {
          localOrAnonymousClass.forEachProgramMethod(
              m -> keepByteCodeFunctions.add(m.getReference()));
        }
      }
    } else {
      assert verifyKotlinMetadataModeledForAllClasses(enqueuer, keepMetadata);
    }
    // Trace through the modeled kotlin metadata.
    enqueuer.forAllLiveClasses(
        clazz -> {
          clazz.getKotlinInfo().trace(definitionSupplier);
          forEachApply(
              clazz.methods(), method -> method.getKotlinMemberInfo()::trace, definitionSupplier);
          forEachApply(
              clazz.fields(), field -> field.getKotlinMemberInfo()::trace, definitionSupplier);
        });
  }

  private boolean verifyKotlinMetadataModeledForAllClasses(
      Enqueuer enqueuer, boolean keepMetadata) {
    enqueuer.forAllLiveClasses(
        clazz -> {
          // Trace through class and member definitions
          assert !hasKotlinClassMetadataAnnotation(clazz, definitionSupplier)
              || !keepMetadata
              || !enqueuer.isPinned(clazz.type)
              || clazz.getKotlinInfo() != NO_KOTLIN_INFO;
        });
    return true;
  }

  public static class KotlinMetadataDefinitionSupplier implements DexDefinitionSupplier {

    private final DexDefinitionSupplier baseSupplier;
    private final Set<DexType> prunedTypes;

    private KotlinMetadataDefinitionSupplier(
        DexDefinitionSupplier baseSupplier, Set<DexType> prunedTypes) {
      this.baseSupplier = baseSupplier;
      this.prunedTypes = prunedTypes;
    }

    @Override
    public DexClass definitionFor(DexType type) {
      // TODO(b/157700128) Metadata cannot at this point keep anything alive. Therefore, if a type
      //  has been pruned it may still be referenced, so we do an early check here to ensure it will
      //  not end up as. Ideally, those types should be removed by a pass on the modeled data.
      if (prunedTypes != null && prunedTypes.contains(type)) {
        return null;
      }
      return baseSupplier.definitionFor(type);
    }

    @Override
    public DexItemFactory dexItemFactory() {
      return baseSupplier.dexItemFactory();
    }
  }
}
