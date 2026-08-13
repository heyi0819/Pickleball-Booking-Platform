import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("admin app", () => {
  it("renders the admin heading", () => {
    render(<App />);
    expect(screen.getByRole("heading", { name: "Pickleball Booking Platform Admin" })).toBeTruthy();
  });
});
