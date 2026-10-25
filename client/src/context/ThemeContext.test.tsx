import { describe, expect, it } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "./ThemeContext";
import { ThemeMenu } from "@/layouts/Topbar";
import { setSystemDark } from "@/test/viewport";

const renderMenu = () =>
  render(
    <ThemeProvider>
      <ThemeMenu />
    </ThemeProvider>,
  );

const theme = () => document.documentElement.getAttribute("data-theme");

async function choose(label: "Light" | "Dark" | "System") {
  await userEvent.click(screen.getByRole("button", { name: /^Theme:/ }));
  await userEvent.click(await screen.findByRole("menuitemradio", { name: label }));
}

describe("theme switching", () => {
  it("defaults to System and follows the operating system", async () => {
    renderMenu();
    expect(screen.getByRole("button", { name: "Theme: System" })).toBeInTheDocument();
    expect(theme()).toBe("light");
    act(() => setSystemDark(true));
    await waitFor(() => expect(theme()).toBe("dark"));
  });

  it("switches between light and dark", async () => {
    renderMenu();
    await choose("Dark");
    expect(theme()).toBe("dark");
    expect(screen.getByRole("button", { name: "Theme: Dark" })).toBeInTheDocument();
    await choose("Light");
    expect(theme()).toBe("light");
  });

  it("ignores OS changes when a theme is chosen explicitly", async () => {
    renderMenu();
    await choose("Light");
    act(() => setSystemDark(true));
    expect(theme()).toBe("light");
  });
});

describe("theme persistence", () => {
  it("stores the choice on this device", async () => {
    renderMenu();
    await choose("Dark");
    expect(window.localStorage.getItem("iotiq.theme")).toBe("dark");
  });

  it("restores the saved choice on the next visit", () => {
    window.localStorage.setItem("iotiq.theme", "dark");
    renderMenu();
    expect(screen.getByRole("button", { name: "Theme: Dark" })).toBeInTheDocument();
    expect(theme()).toBe("dark");
  });

  it("falls back to System for an invalid saved value", () => {
    window.localStorage.setItem("iotiq.theme", "purple");
    renderMenu();
    expect(screen.getByRole("button", { name: "Theme: System" })).toBeInTheDocument();
  });
});
