import { describe, expect, it } from "vitest";
import { formatTaipeiDateTime, formatTwd, presentApiError, roleLabel, statusLabel } from "./index";

describe("shared presentation mappings", () => {
  it("maps safe display labels without leaking enum values", () => { expect(roleLabel("STUDENT")).toBe("學員"); expect(statusLabel("OPEN")).toBe("開放中"); expect(statusLabel("UNEXPECTED")).toBe("狀態未提供"); });
  it("maps every formal course session status to zh-TW", () => { expect(statusLabel("SCHEDULED")).toBe("已排定"); expect(statusLabel("CANCEL_PENDING")).toBe("取消待處理"); expect(statusLabel("CANCELLED")).toBe("已取消"); expect(statusLabel("COMPLETED")).toBe("已完成"); expect(statusLabel("POSTPONED")).toBe("已延期"); });
  it("formats Taipei time and TWD consistently", () => { expect(formatTaipeiDateTime("2026-09-01T02:30:00Z")).toBe("2026/09/01 10:30"); expect(formatTwd("1200.5")).toContain("1,200.5"); });
  it("returns a safe generic message for unknown backend errors", () => { expect(presentApiError("FORBIDDEN")).toContain("權限"); expect(presentApiError("INTERNAL_STACK_TRACE")).toBe("暫時無法完成操作，請稍後再試。"); });
});
