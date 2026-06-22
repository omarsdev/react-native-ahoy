import Ahoy from './NativeAhoy';

export default Ahoy;
export type { Spec as AhoyModule } from './NativeAhoy';

export const END_CALL_REASONS = {
  FAILED: 1,
  REMOTE_ENDED: 2,
  UNANSWERED: 3,
  ANSWERED_ELSEWHERE: 4,
  DECLINED_ELSEWHERE: 5,
  MISSED: 6,
} as const;

// usage:
//   const sub = Ahoy.onAnswerCall(({ uuid }) => { /* ... */ });
//   // later: sub.remove();
