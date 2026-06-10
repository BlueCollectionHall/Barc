export type FeedbackPayloadType = "BUG" | "SUGGESTION" | "OTHER";

const feedbackTypeMap: Record<string, FeedbackPayloadType> = {
  BUG: "BUG",
  BUG问题: "BUG",
  SUGGESTION: "SUGGESTION",
  意见反馈: "SUGGESTION",
  OTHER: "OTHER",
  其他问题: "OTHER",
};

export const normalizeFeedbackType = (value: string): FeedbackPayloadType => {
  return feedbackTypeMap[value] ?? "OTHER";
};
