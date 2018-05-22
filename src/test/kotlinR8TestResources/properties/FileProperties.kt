// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package properties

private var privateProp: String = "privateProp"
internal var internalProp: String = "internalProp"
public var publicProp: String = "publicProp"

private lateinit var privateLateInitProp: String
internal lateinit var internalLateInitProp: String
public lateinit var publicLateInitProp: String

public var primitiveProp: Int = Int.MAX_VALUE

// Serves as intermediate to make sure the access to a property is done from a separate class.
private object Intermediate {
    fun readPrivateProp() = privateProp
    fun writePrivateProp(s: String) { privateProp = s }

    fun readInternalProp() = internalProp
    fun writeInternalProp(s: String) { internalProp = s }

    fun readPublicProp() = publicProp
    fun writePublicProp(s: String) { publicProp = s }

    fun readPrimitiveProp() = primitiveProp
    fun writePrimitiveProp(i: Int) { primitiveProp = i }

    fun readLateInitPrivateProp() = privateLateInitProp
    fun writeLateInitPrivateProp(s: String) { privateLateInitProp = s }

    fun readLateInitInternalProp() = internalLateInitProp
    fun writeLateInitInternalProp(s: String) { internalLateInitProp = s }

    fun readLateInitPublicProp() = publicLateInitProp
    fun writeLateInitPublicProp(s: String) { publicLateInitProp = s }
}

fun doNotUseProperties(): String {
    return "doNotUseProperties"
}

fun fileProperties_noUseOfProperties() {
    println(ObjectProperties.doNotUseProperties())
}

fun fileProperties_usePrivateProp() {
    Intermediate.writePrivateProp("foo")
    println(Intermediate.readPrivateProp())
}

fun fileProperties_useInternalProp() {
    Intermediate.writeInternalProp("foo")
    println(Intermediate.readInternalProp())
}

fun fileProperties_usePublicProp() {
    Intermediate.writePublicProp("foo")
    println(Intermediate.readPublicProp())
}

fun fileProperties_usePrimitiveProp() {
    Intermediate.writePrimitiveProp(Int.MIN_VALUE)
    println(Intermediate.readPrimitiveProp())
}

fun fileProperties_useLateInitPrivateProp() {
    Intermediate.writeLateInitPrivateProp("foo")
    println(Intermediate.readLateInitPrivateProp())
}

fun fileProperties_useLateInitInternalProp() {
    Intermediate.writeLateInitInternalProp( "foo")
    println(Intermediate.readLateInitInternalProp())
}

fun fileProperties_useLateInitPublicProp() {
    Intermediate.writeLateInitPublicProp("foo")
    println(Intermediate.readLateInitPublicProp())
}
