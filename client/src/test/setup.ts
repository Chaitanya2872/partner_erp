import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach } from "vitest";
import { cleanup, configure } from "@testing-library/react";
import { resetViewport, installMatchMedia } from "./viewport";

// jsdom lacks these browser APIs that Radix primitives and the responsive shell use.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;
if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => undefined;
if (!Element.prototype.hasPointerCapture) Element.prototype.hasPointerCapture = () => false;
if (!Element.prototype.releasePointerCapture) Element.prototype.releasePointerCapture = () => undefined;

installMatchMedia();

// Pages are lazy chunks; the first import of each is transformed on demand, which can exceed the 1s default.
configure({ asyncUtilTimeout: 10_000 });

beforeEach(() => {
  resetViewport();
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

afterEach(() => {
  cleanup();
});
