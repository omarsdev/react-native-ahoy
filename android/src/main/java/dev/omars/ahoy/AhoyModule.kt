package dev.omars.ahoy

import com.facebook.react.bridge.ReactApplicationContext

class AhoyModule(reactContext: ReactApplicationContext) :
  NativeAhoySpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeAhoySpec.NAME
  }
}
