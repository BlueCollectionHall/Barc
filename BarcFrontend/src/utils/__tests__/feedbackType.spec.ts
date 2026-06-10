import { describe, expect, it } from "vitest";

import { normalizeFeedbackType } from "@/utils/feedbackType.ts";

describe("normalizeFeedbackType", () => {
  it("keeps supported feedback payload types unchanged", () => {
    expect(normalizeFeedbackType("BUG")).toBe("BUG");
    expect(normalizeFeedbackType("SUGGESTION")).toBe("SUGGESTION");
    expect(normalizeFeedbackType("OTHER")).toBe("OTHER");
  });

  it("maps feedback category labels to payload types", () => {
    expect(normalizeFeedbackType("BUG问题")).toBe("BUG");
    expect(normalizeFeedbackType("意见反馈")).toBe("SUGGESTION");
    expect(normalizeFeedbackType("其他问题")).toBe("OTHER");
  });

  it("falls back to OTHER for unknown values", () => {
    expect(normalizeFeedbackType("UNKNOWN")).toBe("OTHER");
    expect(normalizeFeedbackType("")).toBe("OTHER");
  });
});
