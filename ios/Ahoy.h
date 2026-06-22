#import <AhoySpec/AhoySpec.h> // umbrella header == codegen name (AhoySpec), NOT "NativeAhoySpec.h"

// Conform to <NativeAhoySpec> AND inherit NativeAhoySpecBase: the base class
// provides the generated emitOn… event methods (emitOnAnswerCall, etc.).
// AhoyEventDelegate (declared in Swift, seen via Ahoy-Swift.h) lets the Swift
// CallKit core push events back through this module — see Ahoy.mm.
@interface Ahoy : NativeAhoySpecBase <NativeAhoySpec>

@end
