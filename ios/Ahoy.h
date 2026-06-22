#import <AhoySpec/AhoySpec.h> // umbrella header == codegen name (AhoySpec), NOT "NativeAhoySpec.h"

// Conform to <NativeAhoySpec> AND inherit NativeAhoySpecBase: the base class
// provides the generated emitOn… event methods (emitOnAnswerCall, etc.).
@interface Ahoy : NativeAhoySpecBase <NativeAhoySpec>

@end
