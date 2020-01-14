// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKotlinClassifier;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.Box;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmFunctionExtensionVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor;
import kotlinx.metadata.jvm.JvmMethodSignature;

public interface FoundKmDeclarationContainerSubject extends KmDeclarationContainerSubject {

  CodeInspector codeInspector();
  KmDeclarationContainer getKmDeclarationContainer();

  // TODO(b/145824437): This is a dup of DescriptorUtils#getDescriptorFromKmType
  default String getDescriptorFromKmType(KmType kmType) {
    if (kmType == null) {
      return null;
    }
    Box<String> descriptor = new Box<>(null);
    kmType.accept(new KmTypeVisitor() {
      @Override
      public void visitClass(String name) {
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }

      @Override
      public void visitTypeAlias(String name) {
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }
    });
    return descriptor.get();
  }

  @Override
  default List<String> getParameterTypeDescriptorsInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getDescriptorFromKmType(kmValueParameter.getType()))
                .filter(Objects::nonNull))
        .collect(Collectors.toList());
  }

  @Override
  default List<String> getReturnTypeDescriptorsInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> getDescriptorFromKmType(kmFunction.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  default List<String> getReturnTypeDescriptorsInProperties() {
    return getKmDeclarationContainer().getProperties().stream()
        .map(kmProperty -> getDescriptorFromKmType(kmProperty.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  // TODO(b/145824437): This is a dup of KotlinMetadataJvmExtensionUtils$KmFunctionProcessor
  class KmFunctionProcessor {
    // Custom name via @JvmName("..."). Otherwise, null.
    private JvmMethodSignature signature = null;

    KmFunctionProcessor(KmFunction kmFunction) {
      kmFunction.accept(new KmFunctionVisitor() {
        @Override
        public KmFunctionExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmFunctionExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmFunctionExtensionVisitor() {
            @Override
            public void visit(JvmMethodSignature desc) {
              assert signature == null : signature.asString();
              signature = desc;
            }
          };
        }
      });
    }

    JvmMethodSignature signature() {
      return signature;
    }
  }

  default KmFunctionSubject kmFunctionOrExtensionWithUniqueName(String name, boolean isExtension) {
    for (KmFunction kmFunction : getKmDeclarationContainer().getFunctions()) {
      if (KmFunctionSubject.isExtension(kmFunction) != isExtension) {
        continue;
      }
      if (kmFunction.getName().equals(name)) {
        return new FoundKmFunctionSubject(codeInspector(), kmFunction);
      }
      KmFunctionProcessor kmFunctionProcessor = new KmFunctionProcessor(kmFunction);
      if (kmFunctionProcessor.signature() != null
          && kmFunctionProcessor.signature().getName().equals(name)) {
        return new FoundKmFunctionSubject(codeInspector(), kmFunction);
      }
    }
    return new AbsentKmFunctionSubject();
  }

  @Override
  default KmFunctionSubject kmFunctionWithUniqueName(String name) {
    return kmFunctionOrExtensionWithUniqueName(name, false);
  }

  @Override
  default KmFunctionSubject kmFunctionExtensionWithUniqueName(String name) {
    return kmFunctionOrExtensionWithUniqueName(name, true);
  }

  @Override
  default List<KmFunctionSubject> getFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> new FoundKmFunctionSubject(codeInspector(), kmFunction))
        .collect(Collectors.toList());
  }

  default ClassSubject getClassSubjectFromKmType(KmType kmType) {
    String descriptor = getDescriptorFromKmType(kmType);
    if (descriptor == null) {
      return new AbsentClassSubject();
    }
    return codeInspector().clazz(Reference.classFromDescriptor(descriptor));
  }

  @Override
  default List<ClassSubject> getParameterTypesInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getClassSubjectFromKmType(kmValueParameter.getType()))
                .filter(ClassSubject::isPresent))
        .collect(Collectors.toList());
  }

  @Override
  default List<ClassSubject> getReturnTypesInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> getClassSubjectFromKmType(kmFunction.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  @Override
  default List<ClassSubject> getReturnTypesInProperties() {
    return getKmDeclarationContainer().getProperties().stream()
        .map(kmProperty -> getClassSubjectFromKmType(kmProperty.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }
}
