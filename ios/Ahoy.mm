#import "Ahoy.h"
#import <CallKit/CallKit.h> // must precede Ahoy-Swift.h: it exposes AhoyCallKit's <CXProviderDelegate>
#import <PushKit/PushKit.h> // likewise: AhoyVoipPushManager's <PKPushRegistryDelegate>
#import "Ahoy-Swift.h"      // compiler-generated; product-module name is "Ahoy"

// Conform to the Swift event delegate in a class extension so the Swift CallKit
// core (AhoyCallKit) can push delegate callbacks back to JS through this module.
@interface Ahoy () <AhoyEventDelegate>
@end

@implementation Ahoy {
  AhoyCallKit *_callKit;
}

+ (NSString *)moduleName
{
  return @"Ahoy";
}

- (instancetype)init
{
  if (self = [super init]) {
    _callKit = [AhoyCallKit shared]; // shared so it (and its CXProvider) exist at launch
    _callKit.eventDelegate = self;   // flushes any cold-start buffered push events
  }
  return self;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeAhoySpecJSI>(params);
}

// RN wires the event emitter here (after init). Only now is it safe to emit, so
// replay any events buffered during a cold-start VoIP push.
- (void)setEventEmitterCallback:(EventEmitterCallbackWrapper *)eventEmitterCallbackWrapper
{
  [super setEventEmitterCallback:eventEmitterCallbackWrapper];
  [_callKit flushBufferedEvents];
}

#pragma mark - Setup / lifecycle

- (void)setup:(NSDictionary *)options
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
{
  // CallKit provider is created in AhoyCallKit init; nothing else to do for T2.
  // TODO(T4): apply options (ringtone, icon, supportsVideo) to CXProviderConfiguration.
  resolve(nil);
}

#pragma mark - Outgoing

- (void)startCall:(JS::NativeAhoy::SpecStartCallOpts &)opts
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  BOOL ok = [_callKit startCall:opts.uuid()
                         handle:opts.handle()
                       hasVideo:opts.hasVideo().value_or(false)];
  if (ok) {
    resolve(nil);
  } else {
    reject(@"ahoy_busy", @"already in a call", nil); // single-outgoing protector
  }
}

- (void)reportConnectedOutgoingCall:(NSString *)uuid
{
  [_callKit reportConnectedOutgoingCall:uuid];
}

#pragma mark - Incoming (JS-driven in T2)

- (void)displayIncomingCall:(JS::NativeAhoy::SpecDisplayIncomingCallOpts &)opts
                    resolve:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject
{
  NSString *callerName = opts.localizedCallerName() ?: @"";
  [_callKit displayIncomingCall:opts.uuid()
                         handle:opts.handle()
            localizedCallerName:callerName
                       hasVideo:opts.hasVideo().value_or(false)];
  resolve(nil);
}

#pragma mark - Local actions

- (void)answerIncomingCall:(NSString *)uuid
{
  [_callKit answerCall:uuid];
}

- (void)endCall:(NSString *)uuid
{
  [_callKit endCall:uuid];
}

- (void)endAllCalls
{
  [_callKit endAllCalls];
}

- (void)rejectCall:(NSString *)uuid
{
  // CallKit has no distinct "reject": ending the incoming call rejects it.
  [_callKit endCall:uuid];
}

- (void)reportEndCallWithUUID:(NSString *)uuid
                       reason:(double)reason
{
  [_callKit reportEndCall:uuid reason:(NSInteger)reason];
}

- (void)updateDisplay:(NSString *)uuid
          displayName:(NSString *)displayName
               handle:(NSString *)handle
{
  [_callKit updateDisplay:uuid displayName:displayName handle:handle];
}

- (void)setMutedCall:(NSString *)uuid
               muted:(BOOL)muted
{
  [_callKit setMuted:uuid muted:muted];
}

- (void)setOnHold:(NSString *)uuid
             hold:(BOOL)hold
{
  [_callKit setHeld:uuid onHold:hold];
}

#pragma mark - Platform-guarded (Android-only: no-op on iOS)

- (void)setAvailable:(BOOL)available
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  resolve(nil);
}

- (void)checkIsInManagedCall:(RCTPromiseResolveBlock)resolve
                      reject:(RCTPromiseRejectBlock)reject
{
  resolve(@NO);
}

#pragma mark - Platform-guarded (iOS-only)

- (void)isCallActive:(NSString *)uuid
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  resolve(@([_callKit isCallActive:uuid]));
}

#pragma mark - T4: push wakeup

- (void)getVoipPushToken:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject
{
  NSString *token = [AhoyVoipPushManager shared].currentToken;
  resolve(token ?: (id)kCFNull);
}

#pragma mark - T5: full-screen-intent (Android-only; iOS rings via CallKit/PushKit)

// iOS has no full-screen-intent permission — CallKit owns the lock-screen ring,
// so these are always-allowed / no-op to keep the cross-platform JS API uniform.
- (void)canUseFullScreenIntent:(RCTPromiseResolveBlock)resolve
                        reject:(RCTPromiseRejectBlock)reject
{
  resolve(@YES);
}

- (void)openFullScreenIntentSettings
{
  // no-op on iOS
}

#pragma mark - AhoyEventDelegate (Swift core -> JS)

- (void)sendEvent:(NSString *)name body:(NSDictionary *)body
{
  if ([name isEqualToString:@"onAnswerCall"]) {
    [self emitOnAnswerCall:body];
  } else if ([name isEqualToString:@"onEndCall"]) {
    [self emitOnEndCall:body];
  } else if ([name isEqualToString:@"onDisplayIncomingCall"]) {
    [self emitOnDisplayIncomingCall:body];
  } else if ([name isEqualToString:@"onStartCallAction"]) {
    [self emitOnStartCallAction:body];
  } else if ([name isEqualToString:@"onToggleMute"]) {
    [self emitOnToggleMute:body];
  } else if ([name isEqualToString:@"onToggleHold"]) {
    [self emitOnToggleHold:body];
  } else if ([name isEqualToString:@"onProviderReset"]) {
    [self emitOnProviderReset];
  } else if ([name isEqualToString:@"onDidActivateAudioSession"]) {
    [self emitOnDidActivateAudioSession];
  } else if ([name isEqualToString:@"onDidDeactivateAudioSession"]) {
    [self emitOnDidDeactivateAudioSession];
  } else if ([name isEqualToString:@"onVoipPushToken"]) {
    [self emitOnVoipPushToken:body];
  } else {
    NSLog(@"Ahoy: unhandled event %@", name);
  }
}

@end
