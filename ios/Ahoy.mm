#import "Ahoy.h"
#import "Ahoy-Swift.h" // compiler-generated; product-module name (TODO T2: confirm sanitized name)

// T1 skeleton: every method is a stub mapped to the ticket that implements it.
// No CallKit/PushKit logic here yet — that lands in T2/T4. The real work lives
// in AhoyCallKit.swift (reached via @objc), this .mm is the thin TurboModule shell.
@implementation Ahoy {
  AhoyCallKit *_callKit; // Swift impl home, filled in T2/T4
}

+ (NSString *)moduleName
{
  return @"Ahoy";
}

- (instancetype)init
{
  if (self = [super init]) {
    _callKit = [AhoyCallKit new]; // proves the Obj-C++ -> Swift @objc bridge links
  }
  return self;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeAhoySpecJSI>(params);
}

#pragma mark - Lifecycle (both platforms)

- (void)setup:(NSDictionary *)options
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
{
  // TODO(T2): configure CXProvider/CXProviderConfiguration via _callKit; resolve for now
  resolve(nil);
}

- (void)startCall:(JS::NativeAhoy::SpecStartCallOpts &)opts
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  // TODO(T2): CXStartCallAction via CXCallController
  resolve(nil);
}

- (void)displayIncomingCall:(JS::NativeAhoy::SpecDisplayIncomingCallOpts &)opts
                    resolve:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject
{
  // TODO(T4): CXProvider reportNewIncomingCallWithUUID:update:completion:
  resolve(nil);
}

- (void)answerIncomingCall:(NSString *)uuid
{
  // TODO(T2): CXAnswerCallAction; then [self emitOnAnswerCall:@{@"uuid": uuid}];
}

- (void)endCall:(NSString *)uuid
{
  // TODO(T2): CXEndCallAction
}

- (void)endAllCalls
{
  // TODO(T2): end every CXCall via CXCallController
}

- (void)rejectCall:(NSString *)uuid
{
  // TODO(T2): CXEndCallAction on an incoming call
}

- (void)reportEndCallWithUUID:(NSString *)uuid
                       reason:(double)reason
{
  // TODO(T2): CXProvider reportCallWithUUID:endedAtDate:reason: (reason = END_CALL_REASONS code)
}

- (void)updateDisplay:(NSString *)uuid
          displayName:(NSString *)displayName
               handle:(NSString *)handle
{
  // TODO(T2): CXProvider reportCallWithUUID:updated: with a new CXCallUpdate
}

- (void)setMutedCall:(NSString *)uuid
               muted:(BOOL)muted
{
  // TODO(T2): CXSetMutedCallAction
}

- (void)setOnHold:(NSString *)uuid
             hold:(BOOL)hold
{
  // TODO(T2): CXSetHeldCallAction
}

#pragma mark - Platform-guarded (Android-only: no-op stubs on iOS)

- (void)setAvailable:(BOOL)available
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  // Android-only (TelecomManager availability). No-op on iOS.
  resolve(nil);
}

- (void)checkIsInManagedCall:(RCTPromiseResolveBlock)resolve
                      reject:(RCTPromiseRejectBlock)reject
{
  // Android-only. No-op on iOS.
  resolve(@NO);
}

#pragma mark - Platform-guarded (iOS-only)

- (void)isCallActive:(NSString *)uuid
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  // TODO(T2): query CXCallObserver for an active call with this UUID
  resolve(@NO);
}

@end
