// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexType;
import java.util.LinkedHashMap;
import java.util.Objects;

public class EmulatedDispatchMethodDescriptor implements SpecificationDescriptor {

  /**
   * When resolving into the descriptor, if the resolution is used for a super-invoke or to generate
   * a forwarding method, then the forwarding method should be used. If the resolution is used to
   * rewrite an invoke, then it should be rewritten to an invoke-static to the emulated dispatch
   * method.
   *
   * <p>Emulated dispatch method are generated as follows: <code>emulatedDispatchMethod {
   *   if (rcvr instanceof itf) {
   *     invoke-interface itfMethod
   *   }
   *   if (rcvr instanceof dispatchCases0) {
   *     invoke-static dispatchCases0
   *   }
   *   ...
   *   if (rcvr instanceof dispatchCasesN) {
   *     invoke-static dispatchCasesN
   *   }
   *   invoke-static forwardingMethod }</code>
   *
   * <p>For emulatedVirtualRetarget instances, the itfMethod holder and emulatedDispatchMethod
   * holder are the itf and emulated dispatch holder types to synthesize. The forwardingMethod is
   * the method to retarget to.
   *
   * <p>For emulated interface, itfMethod holder is the rewritten emulated interface type and the
   * emulated dispatch method is on the $-EI class holding the dispatch methods. The forwarding
   * method is the method on the companion class.
   */
  private final DerivedMethod interfaceMethod;
  private final DerivedMethod emulatedDispatchMethod;
  private final DerivedMethod forwardingMethod;
  private final LinkedHashMap<DexType, DerivedMethod> dispatchCases;

  public EmulatedDispatchMethodDescriptor(
      DerivedMethod interfaceMethod,
      DerivedMethod emulatedDispatchMethod,
      DerivedMethod forwardingMethod,
      LinkedHashMap<DexType, DerivedMethod> dispatchCases) {
    this.interfaceMethod = interfaceMethod;
    this.emulatedDispatchMethod = emulatedDispatchMethod;
    this.forwardingMethod = forwardingMethod;
    this.dispatchCases = dispatchCases;
  }

  public DerivedMethod getInterfaceMethod() {
    return interfaceMethod;
  }

  public DerivedMethod getEmulatedDispatchMethod() {
    return emulatedDispatchMethod;
  }

  public DerivedMethod getForwardingMethod() {
    return forwardingMethod;
  }

  public LinkedHashMap<DexType, DerivedMethod> getDispatchCases() {
    return dispatchCases;
  }

  @Override
  public Object[] toJsonStruct(
      MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter exporter) {
    return exporter.exportEmulatedDispatchMethodDescriptor(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EmulatedDispatchMethodDescriptor)) {
      return false;
    }
    EmulatedDispatchMethodDescriptor that = (EmulatedDispatchMethodDescriptor) o;
    return Objects.equals(interfaceMethod, that.interfaceMethod)
        && Objects.equals(emulatedDispatchMethod, that.emulatedDispatchMethod)
        && Objects.equals(forwardingMethod, that.forwardingMethod)
        && Objects.equals(dispatchCases, that.dispatchCases);
  }

  @Override
  public int hashCode() {
    return Objects.hash(interfaceMethod, emulatedDispatchMethod, forwardingMethod, dispatchCases);
  }
}
