import Foundation

// Codegen has NO Swift template, so CallKit/PushKit lives in an @objc public
// Swift class reached from Ahoy.mm. The podspec compiles it via
// s.source_files = "ios/**/*.{h,m,mm,swift,cpp}". This is a T1 stub; T2/T4 fill it.
@objc public class AhoyCallKit: NSObject {
  // Every member exposed to the .mm MUST be @objc public, or it is invisible
  // in the generated Ahoy-Swift.h (RN issue #48359).
  @objc public override init() {
    super.init()
  }

  // TODO(T2): CXProvider + CXProviderDelegate + CXCallController
  // TODO(T4): PKPushRegistry; reportNewIncomingCall SYNCHRONOUSLY inside
  //           pushRegistry(_:didReceiveIncomingPushWith:for:completion:)
}
