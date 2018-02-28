// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package properties

class CompanionProperties {
    companion object {
        private var privateProp: String = "privateProp"
        internal var internalProp: String = "internalProp"
        public var publicProp: String = "publicProp"

        public var primitiveProp: Int = Int.MAX_VALUE

        fun callSetterPrivateProp(v: String) {
            privateProp = v
        }

        fun callGetterPrivateProp(): String {
            return privateProp
        }
    }
}

fun companionProperties_noUseOfProperties() {
    CompanionProperties()
    println("DONE")
}

fun companionProperties_usePrivateProp() {
    CompanionProperties.callSetterPrivateProp("foo")
    println(CompanionProperties.callGetterPrivateProp())
}

fun companionProperties_useInternalProp() {
    CompanionProperties.internalProp = "foo"
    println(CompanionProperties.internalProp)
}

fun companionProperties_usePublicProp() {
    CompanionProperties.publicProp = "foo"
    println(CompanionProperties.publicProp)
}

fun companionProperties_usePrimitiveProp() {
    CompanionProperties.primitiveProp = Int.MIN_VALUE
    println(CompanionProperties.primitiveProp)
}
