import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { CodegenTypes } from 'react-native';

// Codegen does NOT accept enums/unions as params/returns. END_CALL_REASONS
// is modeled as a plain numeric code; mirror callkeep's integer values.
//   FAILED:1, REMOTE_ENDED:2, UNANSWERED:3,
//   ANSWERED_ELSEWHERE:4, DECLINED_ELSEWHERE:5, MISSED:6

export interface Spec extends TurboModule {
  // ---- lifecycle (both platforms) ----
  // T2/T5: wire to CXProvider (iOS) / TelecomManager (Android)
  setup(options: Object): Promise<void>;
  startCall(opts: {
    uuid: string;
    handle: string;
    hasVideo?: boolean;
  }): Promise<void>;
  displayIncomingCall(opts: {
    uuid: string;
    handle: string;
    localizedCallerName?: string;
    hasVideo?: boolean;
  }): Promise<void>;
  answerIncomingCall(uuid: string): void;
  endCall(uuid: string): void;
  endAllCalls(): void;
  rejectCall(uuid: string): void;
  reportEndCallWithUUID(uuid: string, reason: number): void; // reason = END_CALL_REASONS code
  updateDisplay(uuid: string, displayName: string, handle: string): void;
  setMutedCall(uuid: string, muted: boolean): void;
  setOnHold(uuid: string, hold: boolean): void;

  // ---- platform-guarded (stub the other platform as a no-op in T1) ----
  setAvailable(available: boolean): Promise<void>; // Android
  checkIsInManagedCall(): Promise<boolean>; // Android
  isCallActive(uuid: string): Promise<boolean>; // iOS

  // ---- typed events: property MUST be readonly and start with "on" ----
  // Codegen generates emitOnAnswerCall / emitOnEndCall / emitOnDisplayIncomingCall.
  readonly onAnswerCall: CodegenTypes.EventEmitter<{ uuid: string }>;
  readonly onEndCall: CodegenTypes.EventEmitter<{ uuid: string }>;
  readonly onDisplayIncomingCall: CodegenTypes.EventEmitter<{
    uuid: string;
    handle: string;
    fromPushKit: boolean;
  }>;
  readonly onDidActivateAudioSession: CodegenTypes.EventEmitter<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Ahoy');
